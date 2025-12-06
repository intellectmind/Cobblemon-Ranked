package cn.kurt6.cobblemon_ranked.matchmaking

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.config
import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.battle.DuoBattleManager
import cn.kurt6.cobblemon_ranked.battle.DuoBattleManager.DuoTeam
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.network.TeamSelectionStartPayload
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleFormat
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object DuoMatchmakingQueue {
    private val queuedPlayers = CopyOnWriteArrayList<QueuedPlayer>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val cooldownMap = mutableMapOf<UUID, Long>()

    init {
        scheduler.scheduleAtFixedRate({
            try {
                cleanupStaleEntries()
                processQueue()
                DuoBattleManager.tick()
            } catch (e: Exception) {
                CobblemonRanked.logger.error("Error in DuoMatchmakingQueue tick", e)
            }
        }, 5, 5, TimeUnit.SECONDS)
    }

    fun joinQueue(player: ServerPlayerEntity, team: List<UUID>) {
        val lang = CobblemonRanked.config.defaultLang
        val now = System.currentTimeMillis()
        val nextAllowedTime = cooldownMap[player.uuid] ?: 0L

        if (config.enableTeamPreview && !ServerPlayNetworking.canSend(player, TeamSelectionStartPayload.ID)) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.mod_required", lang))
            return
        }

        if (!canPlayerJoinQueue(player)) {
            RankUtils.sendMessage(player, MessageConfig.get("duo.cannot_join", lang))
            return
        }

        if (isInQueue(player.uuid)) {
            RankUtils.sendMessage(player, MessageConfig.get("duo.already_in_queue", lang))
            return
        }

        if (now < nextAllowedTime) {
            val secondsLeft = ((nextAllowedTime - now) / 1000).coerceAtLeast(1)
            RankUtils.sendMessage(player, MessageConfig.get("duo.cooldown", lang, "seconds" to secondsLeft))
            return
        }

        val battleRegistry = Cobblemon.battleRegistry
        if (battleRegistry.getBattleByParticipatingPlayer(player) != null) {
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
    }

    private fun processQueue() {
        if (queuedPlayers.size < 4) return

        val snapshot = ArrayList(queuedPlayers)
        val matched = HashSet<UUID>()
        val battleRegistry = Cobblemon.battleRegistry

        var i = 0
        while (i < snapshot.size) {
            val p1Entry = snapshot[i]
            if (matched.contains(p1Entry.player.uuid)) {
                i++
                continue
            }

            if (p1Entry.player.isDisconnected || battleRegistry.getBattleByParticipatingPlayer(p1Entry.player) != null) {
                queuedPlayers.remove(p1Entry)
                i++
                continue
            }

            var p2Entry: QueuedPlayer? = null
            for (j in i + 1 until snapshot.size) {
                val candidate = snapshot[j]
                if (!matched.contains(candidate.player.uuid) &&
                    !candidate.player.isDisconnected &&
                    battleRegistry.getBattleByParticipatingPlayer(candidate.player) == null
                ) {
                    p2Entry = candidate
                    break
                }
            }

            if (p2Entry == null) {
                i++
                continue
            }

            val teamA = DuoTeam(p1Entry.player, p2Entry.player, p1Entry.team, p2Entry.team)

            var foundOpponent = false
            for (k in snapshot.indexOf(p2Entry) + 1 until snapshot.size) {
                val p3Entry = snapshot[k]
                if (matched.contains(p3Entry.player.uuid) || p3Entry.player.isDisconnected) continue

                for (l in k + 1 until snapshot.size) {
                    val p4Entry = snapshot[l]
                    if (matched.contains(p4Entry.player.uuid) || p4Entry.player.isDisconnected) continue

                    val teamB = DuoTeam(p3Entry.player, p4Entry.player, p3Entry.team, p4Entry.team)

                    if (isEloCompatible(teamA, teamB)) {
                        foundOpponent = true

                        val participants = listOf(p1Entry, p2Entry, p3Entry, p4Entry)
                        participants.forEach { matched.add(it.player.uuid) }
                        queuedPlayers.removeAll(participants)

                        initiateMatch(p1Entry, p2Entry, p3Entry, p4Entry, teamA, teamB)
                        break
                    }
                }
                if (foundOpponent) break
            }
            
            i++ 
        }
    }

    private fun initiateMatch(
        p1: QueuedPlayer, p2: QueuedPlayer,
        p3: QueuedPlayer, p4: QueuedPlayer,
        teamA: DuoTeam, teamB: DuoTeam
    ) {
        val lang = config.defaultLang
        val players = listOf(p1, p2, p3, p4)

        players.forEach {
            RankUtils.sendMessage(it.player, MessageConfig.get("queue.match_success", lang))
        }

        val server = p1.player.server
        scheduler.schedule({
            server.execute {
                val finalCheck = players.all { entry ->
                    !entry.player.isDisconnected &&
                    BattleHandler.validateTeam(entry.player, entry.team, BattleFormat.GEN_9_SINGLES)
                }

                if (!finalCheck) {
                    players.forEach { entry ->
                        if (!entry.player.isDisconnected) {
                            RankUtils.sendMessage(entry.player, MessageConfig.get("queue.cancel_team_changed", lang))
                            if (BattleHandler.validateTeam(entry.player, entry.team, BattleFormat.GEN_9_SINGLES)) {
                                if (!queuedPlayers.any { it.player.uuid == entry.player.uuid }) {
                                     queuedPlayers.add(0, entry)
                                }
                            }
                        }
                    }
                    return@execute
                }

                startNextBattle(teamA, teamB)
            }
        }, 5, TimeUnit.SECONDS)
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

    fun cleanupStaleEntries() {
        val toRemove = mutableListOf<QueuedPlayer>()
        val battleRegistry = Cobblemon.battleRegistry

        queuedPlayers.forEach { entry ->
            if (battleRegistry.getBattleByParticipatingPlayer(entry.player) != null) {
                toRemove.add(entry)
            } else if (entry.player.isDisconnected) {
                toRemove.add(entry)
            }
        }

        queuedPlayers.removeAll(toRemove)
    }

    fun canPlayerJoinQueue(player: ServerPlayerEntity): Boolean {
        if (queuedPlayers.any { it.player.uuid == player.uuid }) return false

        val battleRegistry = Cobblemon.battleRegistry
        if (battleRegistry.getBattleByParticipatingPlayer(player) != null) return false

        if (player.isDisconnected) return false

        return true
    }
}