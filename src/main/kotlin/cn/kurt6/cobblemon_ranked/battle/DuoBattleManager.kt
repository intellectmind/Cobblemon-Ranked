// DuoBattleManager.kt（含轮战胜利处理）
package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.ArenaCoordinate
import cn.kurt6.cobblemon_ranked.config.BattleArena
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.data.PlayerRankData
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import net.minecraft.util.Identifier
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object DuoBattleManager {
    private val activeBattles = ConcurrentHashMap<UUID, Pair<DuoTeam, DuoTeam>>()
    private val activePlayers = ConcurrentHashMap<UUID, DuoTeam>()
    private val teamBattleIdMap = mutableMapOf<DuoTeam, UUID>()
    private val indices = mutableMapOf<DuoTeam, Int>()

    fun registerBattle(battleId: UUID, team1: DuoTeam, team2: DuoTeam) {
        activeBattles[battleId] = team1 to team2
        activePlayers[team1.getActivePlayer().uuid] = team1
        activePlayers[team2.getActivePlayer().uuid] = team2
        teamBattleIdMap[team1] = battleId
        teamBattleIdMap[team2] = battleId
    }

    fun handleVictory(winnerId: UUID, loserId: UUID) {
        val lang = CobblemonRanked.config.defaultLang
        val winnerTeam = activePlayers[winnerId] ?: return
        val loserTeam = activePlayers[loserId] ?: return

        if (winnerTeam.getActivePlayer().uuid != winnerId || loserTeam.getActivePlayer().uuid != loserId) return

        if (loserTeam.switchToNextPlayer()) {
            val next = loserTeam.getActivePlayer()
            val opponent = winnerTeam.getActivePlayer()
            RankUtils.sendMessage(next, MessageConfig.get("duo.next_round.ready", lang, "opponent" to opponent.name.string))
            RankUtils.sendMessage(opponent, MessageConfig.get("duo.next_round.win_continue", lang))
            startNextRound(winnerTeam, loserTeam)
        } else {
            endBattle(winnerTeam, loserTeam)
        }
    }

    // 缓存每组队伍对应的战斗场地与坐标信息
    private val arenaCache = mutableMapOf<DuoTeam, Pair<BattleArena, List<ArenaCoordinate>>>()

    fun startNextRound(winnerTeam: DuoTeam, loserTeam: DuoTeam) {
        val lang = CobblemonRanked.config.defaultLang
        val allPlayers = listOf(winnerTeam.player1, winnerTeam.player2, loserTeam.player1, loserTeam.player2)

        val isFirstRound = winnerTeam.currentIndex == 0 && loserTeam.currentIndex == 0

        // ---------- 获取或复用场地 ----------
        val arenaResult = arenaCache[winnerTeam] ?: if (isFirstRound) {
            val result = BattleHandler.getRandomArenaForPlayers(2) ?: run {
                allPlayers.forEach {
                    RankUtils.sendMessage(it, MessageConfig.get("queue.no_arena", lang))
                }
                return
            }
            // 第一次就缓存给两个队伍
            arenaCache[winnerTeam] = result
            arenaCache[loserTeam] = result
            result
        } else {
            allPlayers.forEach {
                RankUtils.sendMessage(it, MessageConfig.get("queue.no_arena", lang))
            }
            return
        }

        val (arena, positions) = arenaResult

        // ---------- 解析世界 ----------
        val worldId = Identifier.tryParse(arena.world) ?: run {
            allPlayers.forEach {
                RankUtils.sendMessage(it, MessageConfig.get("queue.invalid_world", lang, "world" to arena.world))
            }
            return
        }

        val worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId)
        val server = winnerTeam.player1.server
        val world = server.getWorld(worldKey) ?: run {
            allPlayers.forEach {
                RankUtils.sendMessage(it, MessageConfig.get("queue.world_load_fail", lang, "world" to arena.world))
            }
            return
        }

        // ---------- 分配坐标 ----------
        val winnerPos = positions.getOrNull(0)
        val loserPos = positions.getOrNull(1)

        if (winnerPos == null || loserPos == null) {
            allPlayers.forEach {
                RankUtils.sendMessage(it, MessageConfig.get("queue.no_arena", lang))
            }
            return
        }

        val teamToPos = mapOf(
            winnerTeam.player1 to winnerPos,
            winnerTeam.player2 to winnerPos,
            loserTeam.player1 to loserPos,
            loserTeam.player2 to loserPos
        )

        // ---------- 第一轮：广播匹配成功，记录返回点 ----------
        if (isFirstRound) {
            val matchMessage = MessageConfig.get(
                "duo.match.announce", lang,
                "t1p1" to winnerTeam.player1.name.string,
                "t1p2" to winnerTeam.player2.name.string,
                "t2p1" to loserTeam.player1.name.string,
                "t2p2" to loserTeam.player2.name.string
            )
            allPlayers.forEach { it.sendMessage(Text.literal(matchMessage)) }

            teamToPos.keys.forEach { player ->
                BattleHandler.setReturnLocation(player.uuid, player.serverWorld, Triple(player.x, player.y, player.z))
            }
        }

        // ---------- 所有轮次：传送玩家 ----------
        teamToPos.forEach { (player, pos) ->
            player.teleport(world, pos.x, pos.y, pos.z, 0f, 0f)
        }

        // ---------- 广播本轮对战信息 ----------
        val p1 = winnerTeam.getActivePlayer()
        val p2 = loserTeam.getActivePlayer()
        val announce = MessageConfig.get("duo.round.announce", lang, "p1" to p1.name.string, "p2" to p2.name.string)
        allPlayers.forEach { it.sendMessage(Text.literal(announce)) }

        // ---------- 队伍合法性校验 ----------
        if (isFirstRound) {
            for (player in allPlayers) {
                val team = if (winnerTeam.player1 == player || winnerTeam.player2 == player) winnerTeam else loserTeam
                val uuids = if (team.player1 == player) team.team1 else team.team2
                if (!BattleHandler.validateTeam(player, uuids, BattleFormat.GEN_9_SINGLES)) {
                    RankUtils.sendMessage(player, MessageConfig.get("queue.cancel_team_changed", lang))
                    return
                }
            }
        } else {
            if (!BattleHandler.validateTeam(p1, winnerTeam.getActiveTeam(), BattleFormat.GEN_9_SINGLES) ||
                !BattleHandler.validateTeam(p2, loserTeam.getActiveTeam(), BattleFormat.GEN_9_SINGLES)) {
                RankUtils.sendMessage(p1, MessageConfig.get("queue.cancel_team_changed", lang))
                RankUtils.sendMessage(p2, MessageConfig.get("queue.cancel_team_changed", lang))
                return
            }
        }

        // ---------- 构建战斗对象 ----------
        val team1Pokemon = getBattlePokemonList(p1, winnerTeam.getActiveTeam())
        val team2Pokemon = getBattlePokemonList(p2, loserTeam.getActiveTeam())

        val actor1 = PlayerBattleActor(p1.uuid, team1Pokemon)
        val actor2 = PlayerBattleActor(p2.uuid, team2Pokemon)

        val side1 = BattleSide(actor1)
        val side2 = BattleSide(actor2)

        val battleId = UUID.randomUUID()
        val format = BattleFormat.GEN_9_SINGLES

        // ---------- 启动对战 ----------
        val result = Cobblemon.battleRegistry.startBattle(format, side1, side2, true)
        result.ifSuccessful {
            BattleHandler.markAsRanked(battleId, "2v2singles")
            BattleHandler.registerBattle(it, battleId)
            registerBattle(battleId, winnerTeam, loserTeam)
        }.ifErrored { error ->
            RankUtils.sendMessage(p1, MessageConfig.get("duo.rematch.failed", lang, "error" to error.toString()))
            RankUtils.sendMessage(p2, MessageConfig.get("duo.rematch.failed", lang, "error" to error.toString()))
        }
    }


    fun endBattle(winnerTeam: DuoTeam, loserTeam: DuoTeam) {
        val allPlayers = listOf(winnerTeam.player1, winnerTeam.player2, loserTeam.player1, loserTeam.player2)
        allPlayers.forEach {
            BattleHandler.teleportBackIfPossible(it)
        }

        val lang = CobblemonRanked.config.defaultLang
        val server = winnerTeam.player1.server
        val format = "2v2singles"
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId

        val winnerData = listOf(winnerTeam.player1, winnerTeam.player2).map {
            BattleHandler.rankDao.getPlayerData(it.uuid, seasonId, format) ?: PlayerRankData(
                playerId = it.uuid,
                seasonId = seasonId,
                format = format,
                playerName = it.name.string,
                elo = CobblemonRanked.config.initialElo
            )
        }

        val loserData = listOf(loserTeam.player1, loserTeam.player2).map {
            BattleHandler.rankDao.getPlayerData(it.uuid, seasonId, format) ?: PlayerRankData(
                playerId = it.uuid,
                seasonId = seasonId,
                format = format,
                playerName = it.name.string,
                elo = CobblemonRanked.config.initialElo
            )
        }

        val teamEloWinner = winnerData.map { it.elo }.average().toInt()
        val teamEloLoser = loserData.map { it.elo }.average().toInt()

        val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(
            teamEloWinner,
            teamEloLoser,
            CobblemonRanked.config.eloKFactor,
            CobblemonRanked.config.minElo
        )

        // 分别处理赢家
        winnerData.forEachIndexed { index, data ->
            val player = listOf(winnerTeam.player1, winnerTeam.player2)[index]
            val eloDiff = newWinnerElo - data.elo

            data.apply {
                elo = newWinnerElo
                wins++
                winStreak++
                if (winStreak > bestWinStreak) bestWinStreak = winStreak
            }

            BattleHandler.rankDao.savePlayerData(data)
            BattleHandler.rewardManager.grantRankRewardIfEligible(player, data.getRankTitle(), format, server)
            BattleHandler.sendBattleResultMessage(player, data, eloDiff)
        }

        // 分别处理输家
        loserData.forEachIndexed { index, data ->
            val player = listOf(loserTeam.player1, loserTeam.player2)[index]
            val eloDiff = newLoserElo - data.elo

            data.apply {
                elo = newLoserElo
                losses++
                winStreak = 0
            }

            BattleHandler.rankDao.savePlayerData(data)
            BattleHandler.rewardManager.grantRankRewardIfEligible(player, data.getRankTitle(), format, server)
            BattleHandler.sendBattleResultMessage(player, data, eloDiff)
        }

        val winnerNames = "${winnerTeam.player1.name.string} & ${winnerTeam.player2.name.string}"
        val loserNames = "${loserTeam.player1.name.string} & ${loserTeam.player2.name.string}"

        listOf(winnerTeam.player1, winnerTeam.player2).forEach {
            RankUtils.sendTitle(it, MessageConfig.get("duo.end.victory.title", lang), MessageConfig.get("duo.end.victory.subtitle", lang, "loser" to loserNames))
        }
        listOf(loserTeam.player1, loserTeam.player2).forEach {
            RankUtils.sendTitle(it, MessageConfig.get("duo.end.defeat.title", lang), MessageConfig.get("duo.end.defeat.subtitle", lang, "winner" to winnerNames))
        }

        activePlayers.remove(winnerTeam.player1.uuid)
        activePlayers.remove(winnerTeam.player2.uuid)
        activePlayers.remove(loserTeam.player1.uuid)
        activePlayers.remove(loserTeam.player2.uuid)

        activeBattles.entries.removeIf { (_, pair) ->
            pair.first == winnerTeam || pair.second == winnerTeam || pair.first == loserTeam || pair.second == loserTeam
        }
        teamBattleIdMap.remove(winnerTeam)
        teamBattleIdMap.remove(loserTeam)
    }

    fun tick() {
        activeBattles.forEach { (_, pair) ->
            val (team1, team2) = pair
            val server = team1.player1.server
            val playerManager = server.playerManager
            val allPlayers = listOf(team1.player1, team1.player2, team2.player1, team2.player2)

            if (allPlayers.any { playerManager.getPlayer(it.uuid) == null }) {
                val loser = listOf(team1, team2).find { team ->
                    listOf(team.player1, team.player2).any { playerManager.getPlayer(it.uuid) == null }
                } ?: return@forEach
                val winner = if (loser == team1) team2 else team1
                endBattle(winner, loser)
            }
        }
    }

    private fun DuoTeam.getActivePlayer(): ServerPlayerEntity = if (currentIndex == 0) player1 else player2
    private fun DuoTeam.getActiveTeam(): List<UUID> = if (currentIndex == 0) team1 else team2
    fun DuoTeam.switchToNextPlayer(): Boolean = if (currentIndex == 0) { currentIndex = 1; true } else false
    var DuoTeam.currentIndex: Int
        get() = indices.getOrPut(this) { 0 }
        set(value) { indices[this] = value }

    private fun getBattlePokemonList(player: ServerPlayerEntity, uuids: List<UUID>): List<BattlePokemon> {
        val party = Cobblemon.storage.getParty(player)
        return uuids.mapNotNull { id -> party.find { it?.uuid == id }?.let { BattlePokemon(it!!) } }
    }

    fun isTeamInBattle(team: DuoTeam): Boolean = teamBattleIdMap.containsKey(team)

    data class DuoTeam(
        val player1: ServerPlayerEntity,
        val player2: ServerPlayerEntity,
        val team1: List<UUID>,
        val team2: List<UUID>,
        val joinTime: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            return other is DuoTeam &&
                    setOf(player1.uuid, player2.uuid) == setOf(other.player1.uuid, other.player2.uuid)
        }

        override fun hashCode(): Int = player1.uuid.hashCode() + player2.uuid.hashCode()
    }
}
