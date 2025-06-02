// DuoMatchmakingQueue.kt
// 多人匹配队列处理
package cn.kurt6.cobblemon_ranked.matchmaking

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object DuoMatchmakingQueue {
    // 待处理的邀请列表：接收者UUID -> 发起者UUID
    val pendingInvites = mutableMapOf<UUID, UUID>()
    // 等待组队的玩家及其队伍宝可梦UUID列表：玩家UUID -> 宝可梦UUID列表
    private val pendingPlayers = mutableMapOf<UUID, List<UUID>>()
    // 队友关系映射：玩家UUID -> 队友UUID
    private val teamPartners = mutableMapOf<UUID, UUID>()
    // 已组队排队的队伍列表（线程安全）
    private val queuedTeams = CopyOnWriteArrayList<DuoTeam>()
    // 定时任务调度器
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    init {
        // 每5秒执行一次队列处理
        scheduler.scheduleAtFixedRate(::processQueue, 5, 5, TimeUnit.SECONDS)
    }

    // 战斗冷却时间管理：玩家UUID -> 冷却结束时间戳
    private val cooldownMap = mutableMapOf<UUID, Long>()
    private const val BATTLE_COOLDOWN_MS = 10_000L // 10秒冷却时间

    /**
     * 加入匹配队列
     * @param player 玩家实体
     * @param team 选择的宝可梦UUID列表
     */
    fun joinQueue(player: ServerPlayerEntity, team: List<UUID>) {
        val lang = CobblemonRanked.config.defaultLang
        val now = System.currentTimeMillis()
        // 检查冷却时间
        val nextAllowedTime = cooldownMap[player.uuid] ?: 0L
        if (now < nextAllowedTime) {
            val secondsLeft = ((nextAllowedTime - now) / 1000).coerceAtLeast(1)
            RankUtils.sendMessage(player, MessageConfig.get("duo.cooldown", lang, "seconds" to secondsLeft))
            return
        }

        // 检查是否已在战斗中
        if (Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) != null) {
            RankUtils.sendMessage(player, MessageConfig.get("duo.in_battle", lang))
            return
        }

        // 验证选择的宝可梦是否在玩家队伍中
        val partyUuids = Cobblemon.storage.getParty(player).mapNotNull { it?.uuid }.toSet()
        if (!team.all { it in partyUuids }) {
            RankUtils.sendMessage(player, MessageConfig.get("duo.invalid_team_selection", lang))
            return
        }

        // 验证队伍合法性
        if (!BattleHandler.validateTeam(player, team, BattleFormat.GEN_9_SINGLES)) {
            RankUtils.sendMessage(player, MessageConfig.get("duo.invalid_team", lang))
            return
        }

        // 强制退出singles匹配队列
        CobblemonRanked.matchmakingQueue.removePlayer(player.uuid)

        // 加入等待列表
        pendingPlayers[player.uuid] = team
        RankUtils.sendMessage(player, MessageConfig.get("duo.waiting_for_teammate", lang))
    }

    /**
     * 组成2人队伍
     * @param p1 玩家1
     * @param p2 玩家2
     */
    fun formTeam(p1: ServerPlayerEntity, p2: ServerPlayerEntity) {
        val lang = CobblemonRanked.config.defaultLang
        // 从等待列表中移除玩家
        val team1 = pendingPlayers.remove(p1.uuid)
        val team2 = pendingPlayers.remove(p2.uuid)

        if (team1 == null || team2 == null) {
            RankUtils.sendMessage(p1, MessageConfig.get("duo.form_fail", lang))
            RankUtils.sendMessage(p2, MessageConfig.get("duo.form_fail", lang))
            return
        }

        // 创建队伍并加入队列
        val team = DuoTeam(p1, p2, team1, team2)
        queuedTeams.add(team)
        // 记录队友关系
        teamPartners[p1.uuid] = p2.uuid
        teamPartners[p2.uuid] = p1.uuid
        // 清除邀请记录
        pendingInvites.remove(p1.uuid)
        pendingInvites.remove(p2.uuid)
        // 发送成功消息
        RankUtils.sendMessage(p1, MessageConfig.get("duo.queue_success", lang, "partner" to p2.name.string))
        RankUtils.sendMessage(p2, MessageConfig.get("duo.queue_success", lang, "partner" to p1.name.string))
    }

    /**
     * 检查玩家是否在队列中
     * @param uuid 玩家UUID
     * @return 是否在队列中
     */
    fun isQueued(uuid: UUID): Boolean {
        return queuedTeams.any { it.player1.uuid == uuid || it.player2.uuid == uuid }
    }

    /**
     * 从队列中移除玩家
     * @param player 玩家实体
     * @return 是否成功移除
     */
    fun removePlayer(player: ServerPlayerEntity): Boolean {
        var removed = false
        // 从各个列表中移除玩家记录
        if (pendingPlayers.remove(player.uuid) != null) removed = true
        if (teamPartners.remove(player.uuid) != null) removed = true
        val queueRemoved = queuedTeams.removeIf {
            it.player1.uuid == player.uuid || it.player2.uuid == player.uuid
        }
        if (queueRemoved) removed = true
        // 清除相关邀请
        pendingInvites.entries.removeIf { it.key == player.uuid || it.value == player.uuid }
        return removed
    }

    /**
     * 检查玩家是否在等待队友状态
     * @param uuid 玩家UUID
     * @return 是否在等待中
     */
    fun isPending(uuid: UUID): Boolean = pendingPlayers.containsKey(uuid)

    /**
     * 获取队友UUID
     * @param uuid 玩家UUID
     * @return 队友UUID或null
     */
    fun getPartner(uuid: UUID): UUID? = teamPartners[uuid]

    /**
     * 处理匹配队列
     */
    private fun processQueue() {
        // 清理离线或异常的队伍
        queuedTeams.removeIf {
            !it.player1.isAlive || !it.player2.isAlive ||
                    Cobblemon.battleRegistry.getBattleByParticipatingPlayer(it.player1) != null ||
                    Cobblemon.battleRegistry.getBattleByParticipatingPlayer(it.player2) != null
        }

        // 如果队伍不足2队则返回
        if (queuedTeams.size < 2) return

        val now = System.currentTimeMillis()
        val maxEloDiff = CobblemonRanked.config.maxEloDiff // 最大ELO差异
        val maxWaitTime = CobblemonRanked.config.maxQueueTime * 1000L // 最长等待时间(毫秒)

        val pairsToStart = mutableListOf<Pair<DuoTeam, DuoTeam>>() // 待匹配的队伍对
        val usedIndices = mutableSetOf<Int>() // 已匹配的队伍索引

        // 遍历队列寻找最佳匹配
        for (i in queuedTeams.indices) {
            if (i in usedIndices) continue
            val t1 = queuedTeams[i]
            val elo1 = getAverageElo(t1)

            var bestMatchIndex = -1
            var bestDiff = Int.MAX_VALUE

            // 寻找ELO最接近的对手
            for (j in i + 1 until queuedTeams.size) {
                if (j in usedIndices) continue
                val t2 = queuedTeams[j]
                val elo2 = getAverageElo(t2)

                // 根据等待时间动态调整最大ELO差异
                val waitTime = now - minOf(t1.joinTime, t2.joinTime)
                val dynamicMaxDiff = when {
                    waitTime > maxWaitTime * 0.75 -> maxEloDiff * 3
                    waitTime > maxWaitTime * 0.5 -> maxEloDiff * 2
                    else -> maxEloDiff
                }

                val diff = kotlin.math.abs(elo1 - elo2)
                if (diff <= dynamicMaxDiff && diff < bestDiff) {
                    bestMatchIndex = j
                    bestDiff = diff
                }
            }

            // 找到最佳匹配则加入待开始列表
            if (bestMatchIndex != -1) {
                pairsToStart.add(queuedTeams[i] to queuedTeams[bestMatchIndex])
                usedIndices.add(i)
                usedIndices.add(bestMatchIndex)
            }
        }

        // 开始匹配到的战斗
        pairsToStart.forEach { (t1, t2) ->
            queuedTeams.remove(t1)
            queuedTeams.remove(t2)
            startNextBattle(t1, t2)
        }
    }

    /**
     * 开始下一场战斗
     * @param t1 队伍1
     * @param t2 队伍2
     */
    private fun startNextBattle(t1: DuoTeam, t2: DuoTeam) {
        val lang = CobblemonRanked.config.defaultLang
        val (p1, _) = t1
        val (e1, _) = t2

        // 获取战斗宝可梦列表
        val team1Pokemon = getBattlePokemonList(p1, t1.team1)
        val team2Pokemon = getBattlePokemonList(e1, t2.team1)

        // 创建战斗角色
        val actor1 = PlayerBattleActor(p1.uuid, team1Pokemon)
        val actor2 = PlayerBattleActor(e1.uuid, team2Pokemon)

        // 创建战斗双方
        val side1 = BattleSide(actor1)
        val side2 = BattleSide(actor2)

        val battleId = UUID.randomUUID()
        val format = BattleFormat.GEN_9_SINGLES

        // 开始战斗
        val result = Cobblemon.battleRegistry.startBattle(format, side1, side2, true)
        result.ifSuccessful {
            // 标记为排位赛并注册战斗
            BattleHandler.markAsRanked(battleId, "doubles")
            BattleHandler.registerBattle(it, battleId)
        }.ifErrored { error ->
            // 战斗开始失败处理
            RankUtils.sendMessage(p1, MessageConfig.get("duo.battle_start_fail", lang, "reason" to error.toString()))
            RankUtils.sendMessage(e1, MessageConfig.get("duo.battle_start_fail", lang, "reason" to error.toString()))

            // 设置战斗冷却
            val now = System.currentTimeMillis()
            cooldownMap[p1.uuid] = now + BATTLE_COOLDOWN_MS
            cooldownMap[e1.uuid] = now + BATTLE_COOLDOWN_MS
        }
    }

    /**
     * 获取战斗宝可梦列表
     * @param player 玩家
     * @param uuids 宝可梦UUID列表
     * @return 战斗宝可梦列表
     */
    private fun getBattlePokemonList(player: ServerPlayerEntity, uuids: List<UUID>): List<BattlePokemon> {
        val party = Cobblemon.storage.getParty(player)
        return uuids.mapNotNull { id -> party.find { it?.uuid == id }?.let { BattlePokemon(it!!) } }
    }

    /**
     * 计算队伍平均ELO
     * @param team 队伍
     * @return 平均ELO值
     */
    private fun getAverageElo(team: DuoTeam): Int {
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val format = "doubles"
        val dao = CobblemonRanked.rankDao

        // 获取两队长的ELO值
        val elo1 = dao.getPlayerData(team.player1.uuid, seasonId, format)?.elo ?: CobblemonRanked.config.initialElo
        val elo2 = dao.getPlayerData(team.player2.uuid, seasonId, format)?.elo ?: CobblemonRanked.config.initialElo

        return (elo1 + elo2) / 2
    }

    /**
     * 关闭队列处理器
     */
    fun shutdown() {
        scheduler.shutdownNow()
    }

    /**
     * 双人队伍数据类
     * @property player1 玩家1
     * @property player2 玩家2
     * @property team1 玩家1的队伍宝可梦UUID列表
     * @property team2 玩家2的队伍宝可梦UUID列表
     * @property joinTime 加入队列的时间戳
     */
    data class DuoTeam(
        val player1: ServerPlayerEntity,
        val player2: ServerPlayerEntity,
        val team1: List<UUID>,
        val team2: List<UUID>,
        val joinTime: Long = System.currentTimeMillis()
    )
}