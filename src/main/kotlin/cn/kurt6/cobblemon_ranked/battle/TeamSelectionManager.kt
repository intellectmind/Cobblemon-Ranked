package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.ArenaCoordinate
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.network.SelectionPokemonInfo
import cn.kurt6.cobblemon_ranked.network.TeamSelectionEndPayload
import cn.kurt6.cobblemon_ranked.network.TeamSelectionStartPayload
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object TeamSelectionManager {
    private val pendingSessions = ConcurrentHashMap<UUID, SelectionSession>()
    private val scheduler = Executors.newScheduledThreadPool(1)

    data class SelectionSession(
        val sessionId: UUID,
        val player1: ServerPlayerEntity,
        val player2: ServerPlayerEntity,
        val format: BattleFormat,
        val formatName: String,
        val limit: Int,
        val p1Pos: ArenaCoordinate,
        val p2Pos: ArenaCoordinate,
        var p1Selection: List<UUID>? = null,
        var p2Selection: List<UUID>? = null,
        var timeoutTask: ScheduledFuture<*>? = null
    )

    fun shutdown() {
        pendingSessions.clear()
        try {
            scheduler.shutdownNow()
        } catch (e: Exception) {
            CobblemonRanked.logger.warn("Error shutting down TeamSelectionManager scheduler", e)
        }
    }

    fun startSelection(
        player1: ServerPlayerEntity,
        team1Uuids: List<UUID>,
        p1SkipSelection: Boolean,
        player2: ServerPlayerEntity,
        team2Uuids: List<UUID>,
        p2SkipSelection: Boolean,
        format: BattleFormat,
        formatName: String,
        p1Pos: ArenaCoordinate,
        p2Pos: ArenaCoordinate
    ) {
        val config = CobblemonRanked.config
        val lang = config.defaultLang

        val limit = if (!config.enableTeamPreview) {
            6
        } else {
            when (formatName) {
                "doubles" -> config.doublesPickCount
                "singles", "2v2singles" -> config.singlesPickCount
                else -> 4
            }
        }

        val validTeam1 = team1Uuids.filter { !DuoBattleManager.isPokemonUsed(player1.uuid, it) }
        val validTeam2 = team2Uuids.filter { !DuoBattleManager.isPokemonUsed(player2.uuid, it) }

        val adjustedLimit1 = limit.coerceAtMost(validTeam1.size)
        val adjustedLimit2 = limit.coerceAtMost(validTeam2.size)
        val actualLimit = minOf(adjustedLimit1, adjustedLimit2).coerceAtLeast(1)

        val sessionId = UUID.randomUUID()
        val session = SelectionSession(sessionId, player1, player2, format, formatName, actualLimit, p1Pos, p2Pos)

        pendingSessions[player1.uuid] = session
        pendingSessions[player2.uuid] = session

        if (p1SkipSelection || !config.enableTeamPreview || validTeam1.size <= actualLimit) {
            session.p1Selection = validTeam1.take(actualLimit)

            if (config.enableTeamPreview && validTeam1.size <= actualLimit) {
                RankUtils.sendMessage(player1, MessageConfig.get("queue.selection_confirmed", lang))
            }
        } else {
            sendStartPacket(player1, validTeam1, player2, validTeam2, actualLimit)
        }

        if (p2SkipSelection || !config.enableTeamPreview || validTeam2.size <= actualLimit) {
            session.p2Selection = validTeam2.take(actualLimit)

            if (config.enableTeamPreview && validTeam2.size <= actualLimit) {
                RankUtils.sendMessage(player2, MessageConfig.get("queue.selection_confirmed", lang))
            }
        } else {
            sendStartPacket(player2, validTeam2, player1, validTeam1, actualLimit)
        }

        if (session.p1Selection != null && session.p2Selection != null) {
            cleanup(session)
            player1.server.execute {
                teleportAndStartBattle(session, session.p1Selection!!, session.p2Selection!!)
            }
            return
        }

        session.timeoutTask = scheduler.schedule({
            handleTimeout(session, validTeam1, validTeam2)
        }, config.teamSelectionTime.toLong(), TimeUnit.SECONDS)
    }

    fun handleDisconnect(player: ServerPlayerEntity) {
        val session = pendingSessions[player.uuid] ?: return

        synchronized(session) {
            session.timeoutTask?.cancel(false)
            cleanup(session)

            val opponent = if (session.player1.uuid == player.uuid) session.player2 else session.player1

            if (ServerPlayNetworking.canSend(opponent, TeamSelectionEndPayload.ID)) {
                ServerPlayNetworking.send(opponent, TeamSelectionEndPayload.INSTANCE)
            }

            if (session.formatName == "2v2singles") {
                DuoBattleManager.handleSelectionDisconnect(player, opponent)
            } else {
                player.server.execute {
                    BattleHandler.handleSelectionPhaseDisconnect(opponent, player, session.formatName)
                }
            }

            BattleHandler.forceCleanupPlayerBattleData(opponent)
            BattleHandler.forceCleanupPlayerBattleData(player)
        }
    }

    private fun sendStartPacket(
        target: ServerPlayerEntity,
        myTeamUuids: List<UUID>,
        opponent: ServerPlayerEntity,
        opponentTeamUuids: List<UUID>,
        limit: Int
    ) {
        val config = CobblemonRanked.config
        val myParty = Cobblemon.storage.getParty(target)
        val opParty = Cobblemon.storage.getParty(opponent)

        val myTeamInfo = myTeamUuids.mapNotNull { uuid ->
            myParty.find { it.uuid == uuid }
        }.map { p ->
            val displayLevel = if (config.enableCustomLevel) config.customBattleLevel else p.level
            SelectionPokemonInfo(p.uuid, p.species.name, p.getDisplayName().string, displayLevel, p.gender.toString(), p.shiny, p.form.name)
        }

        val opTeamInfo = opponentTeamUuids.mapNotNull { uuid ->
            opParty.find { it.uuid == uuid }
        }.map { p ->
            val displayLevel = if (config.enableCustomLevel) config.customBattleLevel else p.level
            SelectionPokemonInfo(UUID.randomUUID(), p.species.name, p.species.name, displayLevel, p.gender.toString(), p.shiny, p.form.name)
        }

        val payload = TeamSelectionStartPayload(limit, config.teamSelectionTime, opponent.name.string, opTeamInfo, myTeamInfo)
        ServerPlayNetworking.send(target, payload)
    }

    fun handleSubmission(player: ServerPlayerEntity, selectedUuids: List<UUID>) {
        val session = pendingSessions[player.uuid] ?: return

        synchronized(session) {
            fun sendError() {
                val lang = CobblemonRanked.config.defaultLang
                RankUtils.sendMessage(player, MessageConfig.get("queue.selection_invalid", lang))
            }

            if (!validateSubmission(player, selectedUuids, session.limit)) {
                sendError()
                return
            }

            if (player.uuid == session.player1.uuid) {
                if (session.p1Selection == null) {
                    session.p1Selection = selectedUuids
                    if (session.formatName == "2v2singles") {
                        DuoBattleManager.updateTeamOrder(player.uuid, selectedUuids)
                    }
                    RankUtils.sendMessage(player, MessageConfig.get("queue.selection_confirmed", CobblemonRanked.config.defaultLang))
                    RankUtils.sendMessage(session.player2, MessageConfig.get("queue.opponent_confirmed", CobblemonRanked.config.defaultLang))
                }
            } else if (player.uuid == session.player2.uuid) {
                if (session.p2Selection == null) {
                    session.p2Selection = selectedUuids
                    if (session.formatName == "2v2singles") {
                        DuoBattleManager.updateTeamOrder(player.uuid, selectedUuids)
                    }
                    RankUtils.sendMessage(player, MessageConfig.get("queue.selection_confirmed", CobblemonRanked.config.defaultLang))
                    RankUtils.sendMessage(session.player1, MessageConfig.get("queue.opponent_confirmed", CobblemonRanked.config.defaultLang))
                }
            }

            if (session.p1Selection != null && session.p2Selection != null) {
                session.timeoutTask?.cancel(false)
                cleanup(session)
                player.server.execute {
                    teleportAndStartBattle(session, session.p1Selection!!, session.p2Selection!!)
                }
            }
        }
    }

    private fun validateSubmission(player: ServerPlayerEntity, selected: List<UUID>, limit: Int): Boolean {
        if (selected.size != limit) return false
        val party = Cobblemon.storage.getParty(player)

        val allInParty = selected.all { uuid ->
            party.any { it.uuid == uuid } && !DuoBattleManager.isPokemonUsed(player.uuid, uuid)
        }
        if (!allInParty) return false

        return BattleHandler.validateTeam(player, selected, BattleFormat.GEN_9_SINGLES)
    }

    private fun handleTimeout(session: SelectionSession, p1Full: List<UUID>, p2Full: List<UUID>) {
        if (!pendingSessions.containsKey(session.player1.uuid)) return
        synchronized(session) {
            if (!pendingSessions.containsKey(session.player1.uuid)) return
            val p1Final = session.p1Selection ?: p1Full.take(session.limit)
            val p2Final = session.p2Selection ?: p2Full.take(session.limit)
            cleanup(session)
            session.player1.server.execute {
                val lang = CobblemonRanked.config.defaultLang
                if(session.p1Selection == null) RankUtils.sendMessage(session.player1, MessageConfig.get("queue.selection_timeout", lang))
                if(session.p2Selection == null) RankUtils.sendMessage(session.player2, MessageConfig.get("queue.selection_timeout", lang))
                teleportAndStartBattle(session, p1Final, p2Final)
            }
        }
    }

    private fun cleanup(session: SelectionSession) {
        pendingSessions.remove(session.player1.uuid)
        pendingSessions.remove(session.player2.uuid)
    }

    private fun teleportAndStartBattle(
        session: SelectionSession,
        t1Uuids: List<UUID>,
        t2Uuids: List<UUID>
    ) {
        if (ServerPlayNetworking.canSend(session.player1, TeamSelectionEndPayload.ID)) {
            ServerPlayNetworking.send(session.player1, TeamSelectionEndPayload.INSTANCE)
        }
        if (ServerPlayNetworking.canSend(session.player2, TeamSelectionEndPayload.ID)) {
            ServerPlayNetworking.send(session.player2, TeamSelectionEndPayload.INSTANCE)
        }

        session.player1.teleport(session.player1.serverWorld, session.p1Pos.x, session.p1Pos.y, session.p1Pos.z, 0f, 0f)
        session.player2.teleport(session.player2.serverWorld, session.p2Pos.x, session.p2Pos.y, session.p2Pos.z, 0f, 0f)

        startActualBattle(
            session.player1, t1Uuids,
            session.player2, t2Uuids,
            session.format, session.formatName
        )
    }

    private fun startActualBattle(
        p1: ServerPlayerEntity, t1Uuids: List<UUID>,
        p2: ServerPlayerEntity, t2Uuids: List<UUID>,
        format: BattleFormat, formatName: String
    ) {
        if (formatName == "2v2singles") {
            DuoBattleManager.startActualBattle(p1, t1Uuids, p2, t2Uuids, format, formatName)
        } else {
            startStandardBattle(p1, t1Uuids, p2, t2Uuids, format, formatName)
        }
    }

    private fun startStandardBattle(
        p1: ServerPlayerEntity, t1Uuids: List<UUID>,
        p2: ServerPlayerEntity, t2Uuids: List<UUID>,
        format: BattleFormat, formatName: String
    ) {
        val team1 = t1Uuids.mapNotNull { getBattlePokemon(p1, it) }
        val team2 = t2Uuids.mapNotNull { getBattlePokemon(p2, it) }

        val side1 = BattleSide(PlayerBattleActor(p1.uuid, team1))
        val side2 = BattleSide(PlayerBattleActor(p2.uuid, team2))

        val result = Cobblemon.battleRegistry.startBattle(format, side1, side2)

        result.ifSuccessful { battle ->
            val battleId = UUID.randomUUID()
            BattleHandler.markAsRanked(battleId, formatName)
            BattleHandler.registerBattle(battle, battleId)
        }.ifErrored { error ->
            CobblemonRanked.logger.error("Failed to start standard battle: $error")
            val lang = CobblemonRanked.config.defaultLang
            val msg = MessageConfig.get("queue.battle_start_fail", lang, "reason" to error.toString())

            RankUtils.sendMessage(p1, msg)
            RankUtils.sendMessage(p2, msg)

            BattleHandler.teleportBackIfPossible(p1)
            BattleHandler.teleportBackIfPossible(p2)
            BattleHandler.forceCleanupPlayerBattleData(p1)
            BattleHandler.forceCleanupPlayerBattleData(p2)
        }
    }

    private fun getBattlePokemon(player: ServerPlayerEntity, uuid: UUID): BattlePokemon? {
        val party = Cobblemon.storage.getParty(player)
        val original = party.find { it.uuid == uuid } ?: return null

        val config = CobblemonRanked.config
        val battleEntity = if (config.enableCustomLevel) {
            try {
                val clone = original.clone()
                clone.level = config.customBattleLevel
                clone.heal()
                clone
            } catch (e: Exception) {
                original
            }
        } else {
            original
        }
        return BattlePokemon(battleEntity)
    }
}