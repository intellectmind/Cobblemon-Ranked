package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.network.SelectionPokemonInfo
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
        var p1Selection: List<UUID>? = null,
        var p2Selection: List<UUID>? = null,
        var timeoutTask: ScheduledFuture<*>? = null
    )

    fun startSelection(
        player1: ServerPlayerEntity,
        team1Uuids: List<UUID>,
        player2: ServerPlayerEntity,
        team2Uuids: List<UUID>,
        format: BattleFormat,
        formatName: String
    ) {
        val config = CobblemonRanked.config
        val limit = when (formatName) {
            "doubles" -> config.doublesPickCount
            "singles", "2v2singles" -> config.singlesPickCount
            else -> 3
        }

        if (!config.enableTeamPreview || team1Uuids.size <= limit || team2Uuids.size <= limit) {
            startActualBattle(player1, team1Uuids, player2, team2Uuids, format, formatName)
            return
        }

        val sessionId = UUID.randomUUID()
        val session = SelectionSession(sessionId, player1, player2, format, formatName, limit)

        pendingSessions[player1.uuid] = session
        pendingSessions[player2.uuid] = session

        sendStartPacket(player1, team1Uuids, player2, team2Uuids, limit)
        sendStartPacket(player2, team2Uuids, player1, team1Uuids, limit)

        session.timeoutTask = scheduler.schedule({
            handleTimeout(session, team1Uuids, team2Uuids)
        }, config.teamSelectionTime.toLong(), TimeUnit.SECONDS)
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

            SelectionPokemonInfo(
                uuid = p.uuid,
                species = p.species.name,
                displayName = p.getDisplayName().string,
                level = displayLevel,
                gender = p.gender.toString(),
                shiny = p.shiny,
                form = p.form.name
            )
        }

        val opTeamInfo = opponentTeamUuids.mapNotNull { uuid ->
            opParty.find { it.uuid == uuid }
        }.map { p ->
            val displayLevel = if (config.enableCustomLevel) config.customBattleLevel else p.level

            SelectionPokemonInfo(
                uuid = UUID.randomUUID(),
                species = p.species.name,
                displayName = p.species.name,
                level = displayLevel,
                gender = p.gender.toString(),
                shiny = p.shiny,
                form = p.form.name
            )
        }

        val payload = TeamSelectionStartPayload(
            limit = limit,
            timeLimitSeconds = config.teamSelectionTime,
            opponentName = opponent.name.string,
            opponentTeam = opTeamInfo,
            yourTeam = myTeamInfo
        )

        ServerPlayNetworking.send(target, payload)
    }

    fun handleSubmission(player: ServerPlayerEntity, selectedUuids: List<UUID>) {
        val session = pendingSessions[player.uuid] ?: return

        synchronized(session) {
            if (player.uuid == session.player1.uuid) {
                if (session.p1Selection == null) {
                    if (validateSelection(player, selectedUuids, session.limit)) {
                        session.p1Selection = selectedUuids
                        RankUtils.sendMessage(player, MessageConfig.get("queue.selection_confirmed", CobblemonRanked.config.defaultLang))
                    } else {
                        return
                    }
                }
            } else if (player.uuid == session.player2.uuid) {
                if (session.p2Selection == null) {
                    if (validateSelection(player, selectedUuids, session.limit)) {
                        session.p2Selection = selectedUuids
                        RankUtils.sendMessage(player, MessageConfig.get("queue.selection_confirmed", CobblemonRanked.config.defaultLang))
                    } else {
                        return
                    }
                }
            }

            if (session.p1Selection != null && session.p2Selection != null) {
                session.timeoutTask?.cancel(false)
                cleanup(session)
                player.server.execute {
                    startActualBattle(
                        session.player1, session.p1Selection!!,
                        session.player2, session.p2Selection!!,
                        session.format, session.formatName
                    )
                }
            }
        }
    }

    private fun validateSelection(player: ServerPlayerEntity, selected: List<UUID>, limit: Int): Boolean {
        if (selected.size != limit) return false
        val party = Cobblemon.storage.getParty(player)
        return selected.all { uuid -> party.any { it.uuid == uuid } }
    }

    private fun handleTimeout(session: SelectionSession, p1Full: List<UUID>, p2Full: List<UUID>) {
        synchronized(session) {
            val p1Final = session.p1Selection ?: p1Full.take(session.limit)
            val p2Final = session.p2Selection ?: p2Full.take(session.limit)

            cleanup(session)

            session.player1.server.execute {
                val lang = CobblemonRanked.config.defaultLang
                if(session.p1Selection == null) RankUtils.sendMessage(session.player1, MessageConfig.get("queue.selection_timeout", lang))
                if(session.p2Selection == null) RankUtils.sendMessage(session.player2, MessageConfig.get("queue.selection_timeout", lang))

                startActualBattle(
                    session.player1, p1Final,
                    session.player2, p2Final,
                    session.format, session.formatName
                )
            }
        }
    }

    private fun cleanup(session: SelectionSession) {
        pendingSessions.remove(session.player1.uuid)
        pendingSessions.remove(session.player2.uuid)
    }

    private fun startActualBattle(
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