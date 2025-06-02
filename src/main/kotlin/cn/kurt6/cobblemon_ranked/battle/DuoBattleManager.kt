// DuoBattleManager.kt
// 2v2车轮战对战管理器
package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.util.RankUtils
import cn.kurt6.cobblemon_ranked.matchmaking.DuoMatchmakingQueue.DuoTeam
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object DuoBattleManager {
    // 存储当前活跃的对战，键为对战ID，值为两个对战队伍
    private val activeBattles = ConcurrentHashMap<UUID, Pair<DuoTeam, DuoTeam>>()
    // 存储当前参与对战的玩家，键为玩家UUID，值为所属队伍
    private val activePlayers = ConcurrentHashMap<UUID, DuoTeam>()

    /**
     * 注册一个新的对战
     * @param battleId 对战ID
     * @param team1 队伍1
     * @param team2 队伍2
     */
    fun registerBattle(battleId: UUID, team1: DuoTeam, team2: DuoTeam) {
        activeBattles[battleId] = team1 to team2
        activePlayers[team1.getActivePlayer().uuid] = team1
        activePlayers[team2.getActivePlayer().uuid] = team2
    }

    /**
     * 处理对战胜利事件
     * @param winnerId 获胜玩家UUID
     * @param loserId 失败玩家UUID
     */
    fun handleVictory(winnerId: UUID, loserId: UUID) {
        val lang = CobblemonRanked.config.defaultLang
        val winnerTeam = activePlayers[winnerId] ?: return
        val loserTeam = activePlayers[loserId] ?: return

        val winnerIsActive = winnerTeam.getActivePlayer().uuid == winnerId
        val loserIsActive = loserTeam.getActivePlayer().uuid == loserId

        if (!winnerIsActive || !loserIsActive) {
            // 忽略非当前对战选手的胜负
            return
        }

        if (loserTeam.switchToNextPlayer()) {
            // 失败队伍还有下一位选手，开始下一轮
            val next = loserTeam.getActivePlayer()
            val opponent = winnerTeam.getActivePlayer()
            RankUtils.sendMessage(next, MessageConfig.get("duo.next_round.ready", lang, "opponent" to opponent.name.string))
            RankUtils.sendMessage(opponent, MessageConfig.get("duo.next_round.win_continue", lang))
            startNextRound(winnerTeam, loserTeam)
        } else {
            // 失败队伍没有更多选手，结束对战
            endBattle(winnerTeam, loserTeam)
        }
    }

    /**
     * 开始下一轮对战
     * @param winnerTeam 上一轮获胜的队伍
     * @param loserTeam 上一轮失败的队伍
     */
    private fun startNextRound(winnerTeam: DuoTeam, loserTeam: DuoTeam) {
        val lang = CobblemonRanked.config.defaultLang
        val player1 = winnerTeam.getActivePlayer()
        val player2 = loserTeam.getActivePlayer()

        // 显示对战状态栏
        DuoSpectatorManager.showBattleBar(player1.server, player1, player2)

        // 发送对战开始标题
        RankUtils.sendTitle(player1, MessageConfig.get("duo.next_round.start.title", lang), MessageConfig.get("duo.next_round.start.subtitle", lang, "opponent" to player2.name.string))
        RankUtils.sendTitle(player2, MessageConfig.get("duo.next_round.alert.title", lang), MessageConfig.get("duo.next_round.alert.subtitle", lang, "opponent" to player1.name.string))

        // 准备对战宝可梦
        val team1Pokemon = getBattlePokemonList(player1, winnerTeam.getActiveTeam())
        val team2Pokemon = getBattlePokemonList(player2, loserTeam.getActiveTeam())

        // 创建对战参与者
        val actor1 = PlayerBattleActor(player1.uuid, team1Pokemon)
        val actor2 = PlayerBattleActor(player2.uuid, team2Pokemon)

        val side1 = BattleSide(actor1)
        val side2 = BattleSide(actor2)

        val format = BattleFormat.GEN_9_SINGLES
        val battleId = UUID.randomUUID()

        // 开始对战
        val result = Cobblemon.battleRegistry.startBattle(format, side1, side2, true)

        result.ifSuccessful {
            // 标记为排位赛并注册对战
            BattleHandler.markAsRanked(battleId, "2v2")
            BattleHandler.registerBattle(it, battleId)
            registerBattle(battleId, winnerTeam, loserTeam)
        }.ifErrored { error ->
            // 对战开始失败处理
            RankUtils.sendMessage(player1, MessageConfig.get("duo.rematch.failed", lang, "error" to error.toString()))
            RankUtils.sendMessage(player2, MessageConfig.get("duo.rematch.failed", lang, "error" to error.toString()))
        }
    }

    /**
     * 结束对战并处理结果
     * @param winnerTeam 获胜队伍
     * @param loserTeam 失败队伍
     */
    fun endBattle(winnerTeam: DuoTeam, loserTeam: DuoTeam) {
        val lang = CobblemonRanked.config.defaultLang
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val format = "2v2"
        val server = winnerTeam.player1.server
        val winnerNames = "${winnerTeam.player1.name.string} & ${winnerTeam.player2.name.string}"
        val loserNames = "${loserTeam.player1.name.string} & ${loserTeam.player2.name.string}"

        // 清除对战状态栏
        DuoSpectatorManager.clearBattleBar(server)

        // 处理获胜队伍
        listOf(winnerTeam.player1, winnerTeam.player2).forEach {
            RankUtils.sendTitle(it, MessageConfig.get("duo.end.victory.title", lang), MessageConfig.get("duo.end.victory.subtitle", lang, "loser" to loserNames))
            RankUtils.updateElo(it, true)

            // 更新排名和奖励
            val data = CobblemonRanked.rankDao.getPlayerData(it.uuid, seasonId, format)
            if (data != null) {
                CobblemonRanked.rewardManager.grantRankRewardIfEligible(it, data.getRankTitle(), format, server)
                it.sendMessage(Text.literal(MessageConfig.get("duo.end.rank_display", lang, "rank" to data.getRankTitle(), "elo" to data.elo.toString())))
            }
        }

        // 处理失败队伍
        listOf(loserTeam.player1, loserTeam.player2).forEach {
            RankUtils.sendTitle(it, MessageConfig.get("duo.end.defeat.title", lang), MessageConfig.get("duo.end.defeat.subtitle", lang, "winner" to winnerNames))
            RankUtils.updateElo(it, false)

            // 更新排名和奖励
            val data = CobblemonRanked.rankDao.getPlayerData(it.uuid, seasonId, format)
            if (data != null) {
                CobblemonRanked.rewardManager.grantRankRewardIfEligible(it, data.getRankTitle(), format, server)
                it.sendMessage(Text.literal(MessageConfig.get("duo.end.rank_display", lang, "rank" to data.getRankTitle(), "elo" to data.elo.toString())))
            }
        }

        // 清理缓存
        activePlayers.remove(winnerTeam.player1.uuid)
        activePlayers.remove(winnerTeam.player2.uuid)
        activePlayers.remove(loserTeam.player1.uuid)
        activePlayers.remove(loserTeam.player2.uuid)

        activeBattles.entries.removeIf { (_, pair) ->
            pair.first == winnerTeam || pair.second == winnerTeam ||
                    pair.first == loserTeam || pair.second == loserTeam
        }
    }

    /**
     * 获取对战宝可梦列表
     * @param player 玩家
     * @param uuids 宝可梦UUID列表
     * @return 对战宝可梦列表
     */
    private fun getBattlePokemonList(player: ServerPlayerEntity, uuids: List<UUID>): List<BattlePokemon> {
        val party = Cobblemon.storage.getParty(player)
        return uuids.mapNotNull { id -> party.find { it?.uuid == id }?.let { BattlePokemon(it!!) } }
    }

    /**
     * 获取当前活跃的玩家
     */
    private fun DuoTeam.getActivePlayer(): ServerPlayerEntity =
        if (currentIndex == 0) player1 else player2

    /**
     * 获取当前活跃的队伍宝可梦UUID列表
     */
    private fun DuoTeam.getActiveTeam(): List<UUID> =
        if (currentIndex == 0) team1 else team2

    /**
     * 切换到队伍中的下一位玩家
     * @return 是否切换成功
     */
    fun DuoTeam.switchToNextPlayer(): Boolean {
        return if (currentIndex == 0) {
            currentIndex = 1
            true
        } else {
            false
        }
    }

    // 队伍当前玩家索引的扩展属性
    var DuoTeam.currentIndex: Int
        get() = indices.getOrPut(this) { 0 }
        set(value) { indices[this] = value }

    // 存储队伍当前玩家索引的映射
    private val indices = mutableMapOf<DuoTeam, Int>()
}