package cn.kurt6.cobblemon_ranked.matchmaking

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.config
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.matchmakingQueue
import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.config.RankConfig
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.pokemon.Pokemon
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
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

    init {
        registerDisconnectHandler()
        registerRespawnHandler()

        scheduler.scheduleAtFixedRate({
            cleanupStaleEntries()
            processQueue()
        }, 5, 5, TimeUnit.SECONDS)
        initializeFormatMap()
    }

    private fun initializeFormatMap() {
        formatMap["singles"] = BattleFormat.GEN_9_SINGLES
        formatMap["doubles"] = BattleFormat.GEN_9_DOUBLES
        formatMap["2v2singles"] = BattleFormat.GEN_9_SINGLES
    }

    private val cooldownMap = mutableMapOf<UUID, Long>()
    companion object {
        private const val BATTLE_COOLDOWN_MS = 10_000L

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

                if (matchmakingQueue.queue.containsKey(uuid)) {
                    matchmakingQueue.removePlayer(uuid)
                    val lang = CobblemonRanked.config.defaultLang
                    RankUtils.sendMessage(newPlayer, MessageConfig.get("queue.leave", lang))
                }

                if (DuoMatchmakingQueue.removePlayer(oldPlayer)) {
                    val lang = CobblemonRanked.config.defaultLang
                    RankUtils.sendMessage(newPlayer, MessageConfig.get("queue.leave", lang))
                }

                BattleHandler.forceCleanupPlayerBattleData(newPlayer)
            }
        }
    }

    fun addPlayer(player: ServerPlayerEntity, formatName: String) {
        val lang = config.defaultLang
        val now = System.currentTimeMillis()
        val nextAllowedTime = cooldownMap[player.uuid] ?: 0L
        val server = player.server

        if (!canPlayerJoinQueue(player)) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.cannot_join", lang))
            return
        }

        if (now < nextAllowedTime) {
            val secondsLeft = ((nextAllowedTime - now) / 1000).coerceAtLeast(1)
            RankUtils.sendMessage(player, MessageConfig.get("queue.cooldown", lang, "seconds" to secondsLeft))
            return
        }

        val format = formatMap[formatName] ?: run {
            RankUtils.sendMessage(player, MessageConfig.get("queue.invalid_format", lang, "format" to formatName))
            return
        }

        if (!config.allowedFormats.contains(formatName.lowercase())) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.ban_format", lang, "format" to formatName))
            return
        }

        val battleRegistry = Cobblemon.battleRegistry
        if (battleRegistry.getBattleByParticipatingPlayer(player) != null) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.already_in_battle", lang))
            return
        }

        if (formatName.lowercase() == "2v2singles") {
            try {
                val team = getPlayerTeam(player)
                DuoMatchmakingQueue.joinQueue(player, team)

                val joinMsg = MessageConfig.get("queue.global_join", lang,
                    "player" to player.name.string, "format" to formatName)
                server.playerManager.broadcast(net.minecraft.text.Text.literal(joinMsg), false)

            } catch (e: Exception) {
                if (e.message?.contains("队伍为空") == true) {
                    RankUtils.sendMessage(player, MessageConfig.get("queue.empty_team", lang))
                } else {
                    RankUtils.sendMessage(player, MessageConfig.get("queue.error", lang, "error" to e.message.toString()))
                }
            }
            return
        }

        try {
            val team = getPlayerTeam(player)
            if (!BattleHandler.validateTeam(player, team, format)) return

            val entry = QueueEntry(player, format, team, System.currentTimeMillis())
            queue[player.uuid] = entry
            val messageKey = when (formatName.lowercase()) {
                "singles" -> "queue.join_success_singles"
                "doubles" -> "queue.join_success_doubles"
                else -> "queue.join_success_unknown"
            }
            RankUtils.sendMessage(player, MessageConfig.get(messageKey, lang))

            val joinMsg = MessageConfig.get("queue.global_join", lang,
                "player" to player.name.string, "format" to formatName)
            player.server.playerManager.broadcast(net.minecraft.text.Text.literal(joinMsg), false)
        } catch (e: Exception) {
            if (e.message?.contains("队伍为空") == true) {
                RankUtils.sendMessage(player, MessageConfig.get("queue.empty_team", lang))
            } else {
                RankUtils.sendMessage(player, MessageConfig.get("queue.error", lang, "error" to e.message.toString()))
            }
        }
    }

    fun removePlayer(playerId: UUID) {
        queue.remove(playerId)?.player?.let {
        }
    }

    fun getPlayer(playerId: UUID, format: String? = null): ServerPlayerEntity? {
        val entry = queue[playerId] ?: return null

        if (format == null) return entry.player

        return if (getFormatName(entry.format) == format) entry.player else null
    }

    fun clear() {
        queue.values.forEach {
            RankUtils.sendMessage(it.player, MessageConfig.get("queue.clear", config.defaultLang))
        }
        queue.clear()
    }

    private fun processQueue() {
        synchronized(queue) {
            if (queue.size < 2) return

            val entries = queue.values.toList()
            val processedPlayers = mutableSetOf<UUID>()

            for (i in entries.indices) {
                for (j in i + 1 until entries.size) {
                    val player1 = entries[i]
                    val player2 = entries[j]

                    if (player1.player.uuid in processedPlayers ||
                        player2.player.uuid in processedPlayers) {
                        continue
                    }

                    if (player1.format != player2.format) continue

                    if (!isEloCompatible(player1, player2)) continue

                    if (!queue.containsKey(player1.player.uuid) ||
                        !queue.containsKey(player2.player.uuid)) {
                        continue
                    }

                    val battleRegistry = Cobblemon.battleRegistry
                    if (battleRegistry.getBattleByParticipatingPlayer(player1.player) != null ||
                        battleRegistry.getBattleByParticipatingPlayer(player2.player) != null) {
                        queue.remove(player1.player.uuid)
                        queue.remove(player2.player.uuid)
                        continue
                    }

                    if (!BattleHandler.validateTeam(player1.player, player1.team, player1.format) ||
                        !BattleHandler.validateTeam(player2.player, player2.team, player2.format)) {
                        queue[player1.player.uuid] = player1
                        queue[player2.player.uuid] = player2
                        continue
                    }

                    val removed1 = queue.remove(player1.player.uuid)
                    val removed2 = queue.remove(player2.player.uuid)

                    if (removed1 == null || removed2 == null) {
                        removed1?.let { queue[player1.player.uuid] = it }
                        removed2?.let { queue[player2.player.uuid] = it }
                        continue
                    }

                    processedPlayers.add(player1.player.uuid)
                    processedPlayers.add(player2.player.uuid)

                    startRankedBattle(player1, player2)
                    return
                }
            }
        }
    }

    private fun isEloCompatible(player1: QueueEntry, player2: QueueEntry): Boolean {
        val dao = CobblemonRanked.rankDao
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val formatName = getFormatName(player1.format)

        val elo1 = dao.getPlayerData(player1.player.uuid, seasonId, formatName)?.elo ?: 1000
        val elo2 = dao.getPlayerData(player2.player.uuid, seasonId, formatName)?.elo ?: 1000
        val eloDiff = kotlin.math.abs(elo1 - elo2)

        val waitTime = System.currentTimeMillis() - minOf(player1.joinTime, player2.joinTime)
        val maxWaitTime = config.maxQueueTime * 1000L

        val ratio = (waitTime.toDouble() / maxWaitTime).coerceIn(0.0, 1.0)
        val maxMultiplier = config.maxEloMultiplier
        val dynamicMultiplier = 1.0 + (ratio * (maxMultiplier - 1.0))

        val maxDiff = (config.maxEloDiff * dynamicMultiplier).toInt()

        return eloDiff <= maxDiff
    }

    private fun startRankedBattle(player1: QueueEntry, player2: QueueEntry) {
        val lang = config.defaultLang
        val team1 = getBattlePokemonList(player1.player, player1.team)
        val team2 = getBattlePokemonList(player2.player, player2.team)

        if (team1.isEmpty() || team2.isEmpty()) {
            RankUtils.sendMessage(player1.player, MessageConfig.get("queue.team_load_fail", lang))
            RankUtils.sendMessage(player2.player, MessageConfig.get("queue.team_load_fail", lang))
            queue[player1.player.uuid] = player1
            queue[player2.player.uuid] = player2
            return
        }

        val server = player1.player.server

        val arenaResult = BattleHandler.getRandomArenaForPlayers(2) ?: run {
            RankUtils.sendMessage(player1.player, MessageConfig.get("queue.no_arena", lang))
            RankUtils.sendMessage(player2.player, MessageConfig.get("queue.no_arena", lang))
            return
        }

        val (arena, positions) = arenaResult

        val worldId = Identifier.tryParse(arena.world) ?: run {
            RankUtils.sendMessage(player1.player, MessageConfig.get("queue.invalid_world", lang, "world" to arena.world))
            RankUtils.sendMessage(player2.player, MessageConfig.get("queue.invalid_world", lang, "world" to arena.world))
            return
        }

        val worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId)
        val world = server.getWorld(worldKey) ?: run {
            RankUtils.sendMessage(player1.player, MessageConfig.get("queue.world_load_fail", lang, "world" to arena.world))
            RankUtils.sendMessage(player2.player, MessageConfig.get("queue.world_load_fail", lang, "world" to arena.world))
            return
        }

        RankUtils.sendMessage(player1.player, MessageConfig.get("queue.match_success", lang))
        RankUtils.sendMessage(player2.player, MessageConfig.get("queue.match_success", lang))

        scheduler.schedule({
            server.execute {
                if (player1.player.isDisconnected || player2.player.isDisconnected) {
                    if (!player1.player.isDisconnected) {
                        queue[player1.player.uuid] = player1
                        RankUtils.sendMessage(player1.player, MessageConfig.get("queue.opponent_disconnected", lang))
                    }
                    if (!player2.player.isDisconnected) {
                        queue[player2.player.uuid] = player2
                        RankUtils.sendMessage(player2.player, MessageConfig.get("queue.opponent_disconnected", lang))
                    }
                    return@execute
                }

                if (!BattleHandler.validateTeam(player1.player, player1.team, player1.format) ||
                    !BattleHandler.validateTeam(player2.player, player2.team, player2.format)) {
                    RankUtils.sendMessage(player1.player, MessageConfig.get("queue.cancel_team_changed", lang))
                    RankUtils.sendMessage(player2.player, MessageConfig.get("queue.cancel_team_changed", lang))
                    return@execute
                }

                BattleHandler.setReturnLocation(player1.player.uuid, player1.player.serverWorld, Triple(player1.player.x, player1.player.y, player1.player.z))
                BattleHandler.setReturnLocation(player2.player.uuid, player2.player.serverWorld, Triple(player2.player.x, player2.player.y, player2.player.z))

                player1.player.teleport(world, positions[0].x, positions[0].y, positions[0].z, 0f, 0f)
                player2.player.teleport(world, positions[1].x, positions[1].y, positions[1].z, 0f, 0f)

                if (config.enableCustomLevel) {
                    RankUtils.sendMessage(
                        player1.player,
                        MessageConfig.get("queue.customBattleLevel", lang, "level" to config.customBattleLevel)
                    )
                    RankUtils.sendMessage(
                        player2.player,
                        MessageConfig.get("queue.customBattleLevel", lang, "level" to config.customBattleLevel)
                    )
                    BattleHandler.prepareBattleSnapshot(player1.player, player1.team)
                    BattleHandler.prepareBattleSnapshot(player2.player, player2.team)
                    BattleHandler.applyLevelAdjustments(player1.player)
                    BattleHandler.applyLevelAdjustments(player2.player)
                }

                val actor1 = PlayerBattleActor(player1.player.uuid, team1)
                val actor2 = PlayerBattleActor(player2.player.uuid, team2)
                val side1 = BattleSide(actor1)
                val side2 = BattleSide(actor2)

                val result = Cobblemon.battleRegistry.startBattle(player1.format, side1, side2)

                var battle: PokemonBattle? = null
                var failReason: String? = null

                result.ifSuccessful { b -> battle = b }
                result.ifErrored { error -> failReason = error.toString() }

                if (battle == null) {
                    val errorMsg = failReason ?: "未知错误"
                    RankUtils.sendMessage(player1.player, MessageConfig.get("queue.battle_start_fail", lang, "reason" to errorMsg))
                    RankUtils.sendMessage(player2.player, MessageConfig.get("queue.battle_start_fail", lang, "reason" to errorMsg))

                    val cooldownUntil = System.currentTimeMillis() + BATTLE_COOLDOWN_MS
                    cooldownMap[player1.player.uuid] = cooldownUntil
                    cooldownMap[player2.player.uuid] = cooldownUntil
                    return@execute
                }

                val battleId = UUID.randomUUID()
                val formatName = getFormatName(player1.format)
                val nonNullBattle = battle!!

                BattleHandler.markAsRanked(battleId, formatName)
                BattleHandler.registerBattle(nonNullBattle, battleId)

                RankUtils.sendMessage(player1.player, MessageConfig.get("queue.battle_start", lang, "opponent" to player2.player.name.string))
                RankUtils.sendMessage(player2.player, MessageConfig.get("queue.battle_start", lang, "opponent" to player1.player.name.string))
            }
        }, 5, TimeUnit.SECONDS)
    }

    private fun getPlayerTeam(player: ServerPlayerEntity): List<UUID> {
        val party = Cobblemon.storage.getParty(player)

        if (party.count() == 0) {
            throw IllegalStateException("队伍为空")
        }

        return party.mapNotNull { pokemon ->
            pokemon?.uuid
        }
    }

    private fun getBattlePokemonList(player: ServerPlayerEntity, team: List<UUID>): List<BattlePokemon> {
        return team.mapNotNull { id -> getBattlePokemon(player, id) }
    }

    private fun getBattlePokemon(player: ServerPlayerEntity, uuid: UUID): BattlePokemon? {
        val party = Cobblemon.storage.getParty(player)
        val pokemon: Pokemon? = party.find { it?.uuid == uuid }
        return pokemon?.let { BattlePokemon(it) }
    }

    private fun getFormatName(format: BattleFormat): String {
        return when (format) {
            BattleFormat.GEN_9_SINGLES -> "singles"
            BattleFormat.GEN_9_DOUBLES -> "doubles"
            else -> "custom"
        }
    }

    fun reloadConfig(newConfig: RankConfig) {
        formatMap["singles"] = BattleFormat.GEN_9_SINGLES
        formatMap["doubles"] = BattleFormat.GEN_9_DOUBLES
        formatMap["2v2singles"] = BattleFormat.GEN_9_SINGLES
    }

    fun shutdown() {
        scheduler.shutdownNow()
    }

    data class QueueEntry(
        val player: ServerPlayerEntity,
        val format: BattleFormat,
        val team: List<UUID>,
        val joinTime: Long
    )

    fun cleanupStaleEntries() {
        synchronized(queue) {
            val toRemove = mutableListOf<UUID>()

            queue.values.forEach { entry ->
                val battleRegistry = Cobblemon.battleRegistry

                if (battleRegistry.getBattleByParticipatingPlayer(entry.player) != null) {
                    toRemove.add(entry.player.uuid)
                } else if (entry.player.isDisconnected) {
                    toRemove.add(entry.player.uuid)
                }
            }

            toRemove.forEach { uuid ->
                queue.remove(uuid)
            }
        }
    }

    fun canPlayerJoinQueue(player: ServerPlayerEntity): Boolean {
        if (queue.containsKey(player.uuid)) return false

        val battleRegistry = Cobblemon.battleRegistry
        if (battleRegistry.getBattleByParticipatingPlayer(player) != null) return false

        if (player.isDisconnected) return false

        return true
    }
}