// DuoMatchmakingQueue.kt
package cn.kurt6.cobblemon_ranked.matchmaking

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.config
import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.battle.DuoBattleManager
import cn.kurt6.cobblemon_ranked.battle.DuoBattleManager.DuoTeam
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleFormat
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object DuoMatchmakingQueue {
    private val queuedPlayers = CopyOnWriteArrayList<QueuedPlayer>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val cooldownMap = mutableMapOf<UUID, Long>()
    private const val BATTLE_COOLDOWN_MS = 10_000L

    init {
        scheduler.scheduleAtFixedRate({
            processQueue()
            DuoBattleManager.tick()
        }, 5, 5, TimeUnit.SECONDS)
    }

    fun joinQueue(player: ServerPlayerEntity, team: List<UUID>) {
        val lang = CobblemonRanked.config.defaultLang
        val now = System.currentTimeMillis()
        val nextAllowedTime = cooldownMap[player.uuid] ?: 0L

        if (isInQueue(player.uuid)) {
            RankUtils.sendMessage(player, MessageConfig.get("duo.already_in_queue", lang))
            return
        }

        if (now < nextAllowedTime) {
            val secondsLeft = ((nextAllowedTime - now) / 1000).coerceAtLeast(1)
            RankUtils.sendMessage(player, MessageConfig.get("duo.cooldown", lang, "seconds" to secondsLeft))
            return
        }

        if (Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) != null) {
            RankUtils.sendMessage(player, MessageConfig.get("duo.in_battle", lang))
            return
        }

        val partyUuids = Cobblemon.storage.getParty(player).mapNotNull { it?.uuid }.toSet()
        if (!team.all { it in partyUuids }) {
            RankUtils.sendMessage(player, MessageConfig.get("duo.invalid_team_selection", lang))
            return
        }

        if (!BattleHandler.validateTeam(player, team, BattleFormat.GEN_9_SINGLES)) {
            RankUtils.sendMessage(player, MessageConfig.get("duo.invalid_team", lang))
            return
        }

        CobblemonRanked.matchmakingQueue.removePlayer(player.uuid)
        queuedPlayers.add(QueuedPlayer(player, team, now))
        RankUtils.sendMessage(player, MessageConfig.get("duo.waiting_for_match", lang))

        if (config.enableCustomLevel) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.join_success_customLevel", lang))
        }
    }

    private fun processQueue() {
        if (queuedPlayers.size < 4) return

        val now = System.currentTimeMillis()
        val maxEloDiff = CobblemonRanked.config.maxEloDiff
        val maxWaitTime = CobblemonRanked.config.maxQueueTime * 1000L

        val entries = queuedPlayers.toList()

        for (i in 0 until entries.size - 3) {
            for (j in i + 1 until entries.size - 2) {
                for (k in j + 1 until entries.size - 1) {
                    for (l in k + 1 until entries.size) {
                        val p1 = entries[i]
                        val p2 = entries[j]
                        val p3 = entries[k]
                        val p4 = entries[l]

                        val teamA = DuoTeam(p1.player, p2.player, p1.team, p2.team)
                        val teamB = DuoTeam(p3.player, p4.player, p3.team, p4.team)

                        if (!isEloCompatible(teamA, teamB)) continue

                        queuedPlayers.removeAll(listOf(p1, p2, p3, p4))

                        val lang = config.defaultLang
                        RankUtils.sendMessage(p1.player, MessageConfig.get("queue.match_success", lang))
                        RankUtils.sendMessage(p2.player, MessageConfig.get("queue.match_success", lang))
                        RankUtils.sendMessage(p3.player, MessageConfig.get("queue.match_success", lang))
                        RankUtils.sendMessage(p4.player, MessageConfig.get("queue.match_success", lang))

                        // 5秒后开始传送和战斗
                        val server = p1.player.server
                        scheduler.schedule({
                            server.execute {
                                // 检查是否有玩家掉线
                                val players = listOf(p1, p2, p3, p4)
                                val disconnectedPlayers = players.filter { it.player.isDisconnected }

                                if (disconnectedPlayers.isNotEmpty()) {
                                    // 给在线的玩家发送消息并重新加入队列
                                    players.filter { !it.player.isDisconnected }.forEach { playerEntry ->
                                        RankUtils.sendMessage(
                                            playerEntry.player,
                                            MessageConfig.get("queue.opponent_disconnected", lang)
                                        )
                                        // 只将未掉线的玩家重新加入队列
                                        queuedPlayers.add(playerEntry)
                                    }

                                    return@execute
                                }

                                // 再次验证队伍
                                val invalidPlayers = players.filter {
                                    !BattleHandler.validateTeam(it.player, it.team, BattleFormat.GEN_9_SINGLES)
                                }

                                if (invalidPlayers.isNotEmpty()) {
                                    players.forEach { playerEntry ->
                                        RankUtils.sendMessage(
                                            playerEntry.player,
                                            MessageConfig.get("queue.cancel_team_changed", lang)
                                        )
                                    }
                                    return@execute
                                }

                                startNextBattle(teamA, teamB)
                            }
                        }, 5, TimeUnit.SECONDS)

                        return
                    }
                }
            }
        }
    }

    private fun isEloCompatible(team1: DuoTeam, team2: DuoTeam): Boolean {
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val format = "2v2singles"
        val dao = CobblemonRanked.rankDao

        val elo1 = listOf(team1.player1, team1.player2).map {
            dao.getPlayerData(it.uuid, seasonId, format)?.elo ?: CobblemonRanked.config.initialElo
        }.average()

        val elo2 = listOf(team2.player1, team2.player2).map {
            dao.getPlayerData(it.uuid, seasonId, format)?.elo ?: CobblemonRanked.config.initialElo
        }.average()

        val waitTime = System.currentTimeMillis() - minOf(team1.joinTime, team2.joinTime)
        val ratio = (waitTime.toDouble() / CobblemonRanked.config.maxQueueTime / 1000.0).coerceIn(0.0, 1.0)
        val multiplier = 1.0 + (CobblemonRanked.config.maxEloMultiplier - 1.0) * ratio
        val dynamicMaxDiff = CobblemonRanked.config.maxEloDiff * multiplier

        return kotlin.math.abs(elo1 - elo2) <= dynamicMaxDiff
    }

    private fun startNextBattle(t1: DuoTeam, t2: DuoTeam) {
        DuoBattleManager.startNextRound(t1, t2)
    }

    fun removePlayer(player: ServerPlayerEntity): Boolean {
        return queuedPlayers.removeIf { it.player.uuid == player.uuid }
    }

    fun shutdown() {
        scheduler.shutdownNow()
    }

    fun isInQueue(uuid: UUID): Boolean {
        return queuedPlayers.any { it.player.uuid == uuid }
    }

    data class QueuedPlayer(
        val player: ServerPlayerEntity,
        val team: List<UUID>,
        val joinTime: Long = System.currentTimeMillis()
    )
}