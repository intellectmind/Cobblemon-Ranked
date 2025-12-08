package cn.kurt6.cobblemon_ranked.matchmaking

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.config
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.matchmakingQueue
import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.battle.TeamSelectionManager
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.config.RankConfig
import cn.kurt6.cobblemon_ranked.network.TeamSelectionStartPayload
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleFormat
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RankedBattle")

class MatchmakingQueue {
    val queue = ConcurrentHashMap<UUID, QueueEntry>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val formatMap = mutableMapOf<String, BattleFormat>()
    private val cooldownMap = ConcurrentHashMap<UUID, Long>()
    private val processingMatches = ConcurrentHashMap.newKeySet<UUID>()

    init {
        registerDisconnectHandler()
        registerRespawnHandler()
        scheduler.scheduleAtFixedRate({ cleanupStaleEntries(); processQueue() }, 5, 5, TimeUnit.SECONDS)
        initializeFormatMap()
    }

    private fun initializeFormatMap() {
        formatMap["singles"] = BattleFormat.GEN_9_SINGLES
        formatMap["doubles"] = BattleFormat.GEN_9_DOUBLES
        formatMap["2v2singles"] = BattleFormat.GEN_9_SINGLES
    }

    companion object {
        fun registerDisconnectHandler() {
            ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
                val player = handler.player
                val uuid = player.uuid
                try {
                    BattleHandler.forceCleanupPlayerBattleData(player)
                    matchmakingQueue.removePlayer(uuid)
                    DuoMatchmakingQueue.removePlayer(player)
                } catch (e: Exception) {
                    logger.warn("Failed to cleanup player data on disconnect", e)
                }
            }
        }

        fun registerRespawnHandler() {
            ServerPlayerEvents.AFTER_RESPAWN.register { oldPlayer, newPlayer, alive ->
                val uuid = newPlayer.uuid
                if (matchmakingQueue.queue.containsKey(uuid)) matchmakingQueue.removePlayer(uuid)
                if (DuoMatchmakingQueue.removePlayer(oldPlayer)) {}
                BattleHandler.forceCleanupPlayerBattleData(newPlayer)
            }
        }
    }

    fun addPlayer(player: ServerPlayerEntity, formatName: String) {
        val lang = config.defaultLang

        if (Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) != null) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.already_in_battle", lang))
            return
        }

        if (TeamSelectionManager.isPlayerInSelection(player.uuid)) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.already_in_battle", lang))
            return
        }

        if (BattleHandler.isPlayerWaitingForArena(player.uuid)) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.cannot_join", lang))
            return
        }

        if (config.enableTeamPreview && !ServerPlayNetworking.canSend(player, TeamSelectionStartPayload.ID)) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.mod_required", lang))
            return
        }

        if (!canPlayerJoinQueue(player)) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.cannot_join", lang))
            return
        }

        val format = formatMap[formatName] ?: return

        if (formatName.lowercase() == "2v2singles") {
            try {
                val team = getPlayerTeam(player)
                DuoMatchmakingQueue.joinQueue(player, team)
                player.server.playerManager.broadcast(net.minecraft.text.Text.literal(MessageConfig.get("queue.global_join", lang, "player" to player.name.string, "format" to formatName)), false)
            } catch (e: Exception) {
                RankUtils.sendMessage(player, MessageConfig.get("queue.error", lang, "error" to e.message.toString()))
            }
            return
        }

        try {
            val team = getPlayerTeam(player)
            if (!BattleHandler.validateTeam(player, team, format)) return
            queue[player.uuid] = QueueEntry(player, format, team, System.currentTimeMillis())
            RankUtils.sendMessage(player, MessageConfig.get(if (formatName=="singles") "queue.join_success_singles" else "queue.join_success_doubles", lang))
            player.server.playerManager.broadcast(net.minecraft.text.Text.literal(MessageConfig.get("queue.global_join", lang, "player" to player.name.string, "format" to formatName)), false)
        } catch (e: Exception) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.error", lang, "error" to e.message.toString()))
        }
    }

    fun removePlayer(playerId: UUID) {
        queue.remove(playerId)
        processingMatches.remove(playerId)
        BattleHandler.removePlayerFromWaitingQueue(playerId)
    }

    fun getPlayer(playerId: UUID, format: String? = null): ServerPlayerEntity? {
        val entry = queue[playerId] ?: return null
        if (format == null) return entry.player
        return if (getFormatName(entry.format) == format) entry.player else null
    }

    fun clear() {
        queue.clear()
        processingMatches.clear()
    }

    private fun processQueue() {
        val matchedPairs = mutableListOf<Pair<QueueEntry, QueueEntry>>()
        synchronized(queue) {
            if (queue.size < 2) return
            val entries = queue.values.toList()
            val processedInThisRound = mutableSetOf<UUID>()
            for (i in entries.indices) {
                for (j in i + 1 until entries.size) {
                    val p1 = entries[i]; val p2 = entries[j]
                    if (p1.player.uuid in processedInThisRound || p2.player.uuid in processedInThisRound || p1.player.uuid in processingMatches || p2.player.uuid in processingMatches) continue
                    if (p1.format != p2.format) continue
                    if (!isEloCompatible(p1, p2)) continue
                    if (Cobblemon.battleRegistry.getBattleByParticipatingPlayer(p1.player) != null || Cobblemon.battleRegistry.getBattleByParticipatingPlayer(p2.player) != null) {
                        queue.remove(p1.player.uuid); queue.remove(p2.player.uuid); continue
                    }
                    val removed1 = queue.remove(p1.player.uuid)
                    val removed2 = queue.remove(p2.player.uuid)
                    if (removed1 == null || removed2 == null) {
                        removed1?.let { queue[p1.player.uuid] = it }; removed2?.let { queue[p2.player.uuid] = it }; continue
                    }
                    processingMatches.add(p1.player.uuid); processingMatches.add(p2.player.uuid)
                    processedInThisRound.add(p1.player.uuid); processedInThisRound.add(p2.player.uuid)
                    matchedPairs.add(p1 to p2)
                    break
                }
            }
        }
        matchedPairs.forEach { (p1, p2) ->
            try {
                startRankedBattle(p1, p2)
            } catch (e: Exception) {
                logger.error("Error starting battle", e)
                processingMatches.remove(p1.player.uuid); processingMatches.remove(p2.player.uuid)
                queue[p1.player.uuid] = p1; queue[p2.player.uuid] = p2
            }
        }
    }

    private fun isEloCompatible(player1: QueueEntry, player2: QueueEntry): Boolean {
        val dao = CobblemonRanked.rankDao
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val formatName = getFormatName(player1.format)
        val elo1 = dao.getPlayerData(player1.player.uuid, seasonId, formatName)?.elo ?: 1000
        val elo2 = dao.getPlayerData(player2.player.uuid, seasonId, formatName)?.elo ?: 1000
        val waitTime = System.currentTimeMillis() - minOf(player1.joinTime, player2.joinTime)
        val maxWaitTime = config.maxQueueTime * 1000L
        val ratio = (waitTime.toDouble() / maxWaitTime).coerceIn(0.0, 1.0)
        val multiplier = 1.0 + (ratio * (config.maxEloMultiplier - 1.0))
        return kotlin.math.abs(elo1 - elo2) <= (config.maxEloDiff * multiplier).toInt()
    }

    private fun startRankedBattle(player1: QueueEntry, player2: QueueEntry) {
        val lang = config.defaultLang
        val server = player1.player.server

        BattleHandler.requestArena(
            listOf(player1.player, player2.player),
            2,
            onArenaFound = { arena, positions ->
                val worldId = Identifier.tryParse(arena.world)
                val worldKey = if (worldId != null) RegistryKey.of(RegistryKeys.WORLD, worldId) else null
                val world = if (worldKey != null) server.getWorld(worldKey) else null

                if (world == null) {
                    RankUtils.sendMessage(player1.player, MessageConfig.get("queue.world_load_fail", lang, "world" to arena.world))
                    RankUtils.sendMessage(player2.player, MessageConfig.get("queue.world_load_fail", lang, "world" to arena.world))
                    processingMatches.remove(player1.player.uuid)
                    processingMatches.remove(player2.player.uuid)
                    return@requestArena
                }

                RankUtils.sendMessage(player1.player, MessageConfig.get("queue.match_success", lang))
                RankUtils.sendMessage(player2.player, MessageConfig.get("queue.match_success", lang))

                server.execute {
                    try {
                        if (player1.player.isDisconnected || player2.player.isDisconnected) {
                            processingMatches.remove(player1.player.uuid)
                            processingMatches.remove(player2.player.uuid)
                            BattleHandler.releaseArena(arena)
                            return@execute
                        }

                        BattleHandler.setReturnLocation(player1.player.uuid, player1.player.serverWorld, Triple(player1.player.x, player1.player.y, player1.player.z))
                        BattleHandler.setReturnLocation(player2.player.uuid, player2.player.serverWorld, Triple(player2.player.x, player2.player.y, player2.player.z))

                        player1.player.teleport(world, positions[0].x, positions[0].y, positions[0].z, 0f, 0f)
                        player2.player.teleport(world, positions[1].x, positions[1].y, positions[1].z, 0f, 0f)

                        val formatName = getFormatName(player1.format)
                        TeamSelectionManager.startSelection(
                            player1 = player1.player,
                            team1Uuids = player1.team,
                            p1SkipSelection = false,
                            player2 = player2.player,
                            team2Uuids = player2.team,
                            p2SkipSelection = false,
                            format = player1.format,
                            formatName = formatName,
                            p1Pos = positions[0],
                            p2Pos = positions[1]
                        )
                        processingMatches.remove(player1.player.uuid)
                        processingMatches.remove(player2.player.uuid)
                    } catch (e: Exception) {
                        logger.error("Error in battle startup", e)
                        BattleHandler.releaseArena(arena)
                        processingMatches.remove(player1.player.uuid)
                        processingMatches.remove(player2.player.uuid)
                    }
                }
            },
            onAbort = { survivor ->
                val entry = if (survivor.uuid == player1.player.uuid) player1 else player2
                if (!queue.containsKey(entry.player.uuid)) {
                    queue[entry.player.uuid] = entry
                }
                processingMatches.remove(entry.player.uuid)
                RankUtils.sendMessage(survivor, MessageConfig.get("queue.opponent_disconnected", lang))
            }
        )
    }

    private fun getPlayerTeam(player: ServerPlayerEntity): List<UUID> {
        val party = Cobblemon.storage.getParty(player)
        if (party.count() == 0) throw IllegalStateException("队伍为空")
        return party.mapNotNull { it?.uuid }
    }

    private fun getFormatName(format: BattleFormat): String {
        return when (format) {
            BattleFormat.GEN_9_SINGLES -> "singles"
            BattleFormat.GEN_9_DOUBLES -> "doubles"
            else -> "custom"
        }
    }

    fun reloadConfig(newConfig: RankConfig) { initializeFormatMap() }
    fun shutdown() {
        scheduler.shutdownNow()
        queue.clear()
        processingMatches.clear()
    }
    data class QueueEntry(val player: ServerPlayerEntity, val format: BattleFormat, val team: List<UUID>, val joinTime: Long)
    fun cleanupStaleEntries() {
        synchronized(queue) {
            val toRemove = queue.values.filter { Cobblemon.battleRegistry.getBattleByParticipatingPlayer(it.player) != null || it.player.isDisconnected }.map { it.player.uuid }
            toRemove.forEach { queue.remove(it); processingMatches.remove(it) }
        }
    }
    fun canPlayerJoinQueue(player: ServerPlayerEntity): Boolean {
        if (queue.containsKey(player.uuid) || processingMatches.contains(player.uuid)) return false
        if (Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) != null || player.isDisconnected) return false
        if (TeamSelectionManager.isPlayerInSelection(player.uuid)) return false
        return true
    }
}