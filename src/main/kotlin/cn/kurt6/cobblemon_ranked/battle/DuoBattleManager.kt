package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.config
import cn.kurt6.cobblemon_ranked.config.ArenaCoordinate
import cn.kurt6.cobblemon_ranked.config.BattleArena
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.data.PlayerRankData
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object DuoBattleManager {
    private val activeBattles = ConcurrentHashMap<UUID, Pair<DuoTeam, DuoTeam>>()
    private val activePlayers = ConcurrentHashMap<UUID, DuoTeam>()
    private val pendingTeams = ConcurrentHashMap<UUID, DuoTeam>()
    private val teamBattleIdMap = mutableMapOf<DuoTeam, UUID>()
    private val indices = mutableMapOf<DuoTeam, Int>()
    private val arenaCache = mutableMapOf<DuoTeam, Pair<BattleArena, List<ArenaCoordinate>>>()

    fun registerBattle(battleId: UUID, team1: DuoTeam, team2: DuoTeam) {
        activeBattles[battleId] = team1 to team2
        activePlayers[team1.getActivePlayer().uuid] = team1
        activePlayers[team2.getActivePlayer().uuid] = team2
        teamBattleIdMap[team1] = battleId
        teamBattleIdMap[team2] = battleId

        BattleHandler.setPlayerInRankedBattle(team1.player1.uuid, true)
        BattleHandler.setPlayerInRankedBattle(team1.player2.uuid, true)
        BattleHandler.setPlayerInRankedBattle(team2.player1.uuid, true)
        BattleHandler.setPlayerInRankedBattle(team2.player2.uuid, true)
    }

    fun onBattleStarted(battleId: UUID, p1: ServerPlayerEntity, p2: ServerPlayerEntity) {
        val t1 = pendingTeams.remove(p1.uuid)
        val t2 = pendingTeams.remove(p2.uuid)
        if (t1 != null && t2 != null) {
            registerBattle(battleId, t1, t2)
            CobblemonRanked.logger.info("DuoBattleManager: Registered battle $battleId")
        }
    }

    fun handleSelectionDisconnect(loser: ServerPlayerEntity, winner: ServerPlayerEntity) {
        handleVictory(winner.uuid, loser.uuid)
    }

    fun updateTeamOrder(playerUuid: UUID, newOrder: List<UUID>) {
        val team = activePlayers[playerUuid] ?: pendingTeams[playerUuid] ?: return
        if (team.player1.uuid == playerUuid) team.team1 = newOrder
        else if (team.player2.uuid == playerUuid) team.team2 = newOrder
    }

    fun updateBattleState(battle: PokemonBattle) {
        battle.sides.forEach { side ->
            side.actors.forEach { actor ->
                if (actor is PlayerBattleActor) {
                    actor.pokemonList.forEach {
                        if (it.originalPokemon.currentHealth <= 0 || it.originalPokemon.isFainted()) {
                            markAsUsed(actor.uuid, it.originalPokemon.uuid)
                        }
                    }
                }
            }
        }
    }

    fun markPokemonAsFainted(playerUuid: UUID, battle: PokemonBattle) {
        val actor = battle.actors.find { it is PlayerBattleActor && it.uuid == playerUuid } as? PlayerBattleActor ?: return
        actor.pokemonList.forEach {
            it.originalPokemon.currentHealth = 0
            markAsUsed(playerUuid, it.originalPokemon.uuid)
        }
    }

    private fun markAsUsed(playerUuid: UUID, originalPokemonUuid: UUID) {
        BattleHandler.markPokemonAsUsed(playerUuid, originalPokemonUuid)
    }

    fun isPokemonUsed(playerUuid: UUID, originalPokemonUuid: UUID): Boolean {
        return BattleHandler.isPokemonUsed(playerUuid, originalPokemonUuid)
    }

    fun handlePlayerQuit(player: ServerPlayerEntity) {
        val team = activePlayers[player.uuid] ?: pendingTeams[player.uuid] ?: return
        val battleId = teamBattleIdMap[team]
        val opponentUuid = pendingTeams.entries.find { it.value == team && it.key != player.uuid }?.key
            ?: activePlayers.entries.find { it.value != team && teamBattleIdMap[it.value] == battleId }?.key

        BattleHandler.restorePlayerPokemonLevels(player)
        BattleHandler.setPlayerInRankedBattle(player.uuid, false)
        BattleHandler.clearPlayerUsedPokemon(player.uuid)

        val arenaToRelease = arenaCache[team]?.first
        if (arenaToRelease != null) {
            BattleHandler.releaseArena(arenaToRelease)
            arenaCache.remove(team)
        }

        if (opponentUuid != null) {
            handleVictory(opponentUuid, player.uuid)
        } else {
            cleanupTeamData(team)
        }
    }

    fun handleVictory(winnerId: UUID, loserId: UUID) {
        val lang = CobblemonRanked.config.defaultLang
        val winnerTeam = activePlayers[winnerId] ?: pendingTeams[winnerId] ?: return
        val loserTeam = activePlayers[loserId] ?: pendingTeams[loserId] ?: return

        if (loserTeam.switchToNextPlayer()) {
            val nextLoserPlayer = loserTeam.getActivePlayer()
            val currentWinnerPlayer = winnerTeam.getActivePlayer()

            if (nextLoserPlayer.isDisconnected || currentWinnerPlayer.isDisconnected) {
                val actualWinner = if (nextLoserPlayer.isDisconnected) currentWinnerPlayer else nextLoserPlayer
                val actualLoser = if (nextLoserPlayer.isDisconnected) nextLoserPlayer else currentWinnerPlayer
                endBattle(
                    if (actualWinner.uuid == winnerTeam.player1.uuid || actualWinner.uuid == winnerTeam.player2.uuid) winnerTeam else loserTeam,
                    if (actualLoser.uuid == loserTeam.player1.uuid || actualLoser.uuid == loserTeam.player2.uuid) loserTeam else winnerTeam
                )
                return
            }

            if (!nextLoserPlayer.isDisconnected) {
                RankUtils.sendMessage(
                    nextLoserPlayer,
                    MessageConfig.get("duo.next_round.ready", lang, "opponent" to currentWinnerPlayer.name.string)
                )
            }
            if (!currentWinnerPlayer.isDisconnected) {
                RankUtils.sendMessage(currentWinnerPlayer, MessageConfig.get("duo.next_round.win_continue", lang))
            }

            val server = if (!nextLoserPlayer.isDisconnected) nextLoserPlayer.server else currentWinnerPlayer.server
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule({
                server.execute {
                    try {
                        if (nextLoserPlayer.isDisconnected) {
                            handleVictory(currentWinnerPlayer.uuid, nextLoserPlayer.uuid)
                            return@execute
                        }

                        if (currentWinnerPlayer.isDisconnected) {
                            handleVictory(nextLoserPlayer.uuid, currentWinnerPlayer.uuid)
                            return@execute
                        }

                        val cached = arenaCache[winnerTeam]
                        if (cached == null) {
                            CobblemonRanked.logger.error("Arena cache is null, unable to continue match")
                            endBattle(winnerTeam, loserTeam)
                            return@execute
                        }

                        val (arena, positions) = cached
                        if (positions.size < 2) {
                            CobblemonRanked.logger.error("Not enough positions for next round")
                            endBattle(winnerTeam, loserTeam)
                            return@execute
                        }

                        startNextRound(currentWinnerPlayer, winnerTeam, nextLoserPlayer, loserTeam, positions[0], positions[1])
                    } catch (e: Exception) {
                        CobblemonRanked.logger.error("Error starting next round", e)
                        endBattle(winnerTeam, loserTeam)
                    }
                }
            }, 5, TimeUnit.SECONDS)
        } else {
            endBattle(winnerTeam, loserTeam)
        }
    }

    fun startNextRound(t1: DuoTeam, t2: DuoTeam) {
        pendingTeams[t1.player1.uuid] = t1
        pendingTeams[t1.player2.uuid] = t1
        pendingTeams[t2.player1.uuid] = t2
        pendingTeams[t2.player2.uuid] = t2

        val cached = arenaCache[t1]
        if (cached != null) {
            arenaCache[t2] = cached
        }

        val (arena, positions) = cached ?: run {
            CobblemonRanked.logger.error("No arena in cache for DuoTeam")
            return
        }

        if (positions.size < 2) {
            CobblemonRanked.logger.error("Not enough positions for duo battle")
            return
        }

        startNextRound(
            t1.getActivePlayer(), t1,
            t2.getActivePlayer(), t2,
            positions[0], positions[1]
        )
    }

    private fun startNextRound(
        winnerPlayer: ServerPlayerEntity, winnerTeam: DuoTeam,
        loserPlayer: ServerPlayerEntity, loserTeam: DuoTeam,
        winnerPos: ArenaCoordinate, loserPos: ArenaCoordinate
    ) {
        val winnerUuids = winnerTeam.getActiveTeam().filter { !isPokemonUsed(winnerPlayer.uuid, it) }
        val loserUuids = loserTeam.getActiveTeam().filter { !isPokemonUsed(loserPlayer.uuid, it) }

        if (winnerUuids.isEmpty() || loserUuids.isEmpty()) {
            endBattle(winnerTeam, loserTeam)
            return
        }

        TeamSelectionManager.startSelection(
            player1 = winnerPlayer,
            team1Uuids = winnerUuids,
            p1SkipSelection = false,
            player2 = loserPlayer,
            team2Uuids = loserUuids,
            p2SkipSelection = false,
            format = BattleFormat.GEN_9_SINGLES,
            formatName = "2v2singles",
            p1Pos = winnerPos,
            p2Pos = loserPos
        )
    }

    fun startActualBattle(
        p1: ServerPlayerEntity, t1Uuids: List<UUID>,
        p2: ServerPlayerEntity, t2Uuids: List<UUID>,
        format: BattleFormat, formatName: String
    ) {
        val team1 = t1Uuids.mapNotNull { getBattlePokemon(p1, it) }
        val team2 = t2Uuids.mapNotNull { getBattlePokemon(p2, it) }

        if (team1.isEmpty() || team2.isEmpty()) return

        val side1 = BattleSide(PlayerBattleActor(p1.uuid, team1))
        val side2 = BattleSide(PlayerBattleActor(p2.uuid, team2))

        val result = Cobblemon.battleRegistry.startBattle(format, side1, side2)
        result.ifSuccessful { battle ->
            val battleId = UUID.randomUUID()
            BattleHandler.markAsRanked(battleId, formatName)
            BattleHandler.registerBattle(battle, battleId)
            if (formatName == "2v2singles") onBattleStarted(battleId, p1, p2)
        }.ifErrored {
            pendingTeams.remove(p1.uuid)
            pendingTeams.remove(p2.uuid)
            BattleHandler.setPlayerInRankedBattle(p1.uuid, false)
            BattleHandler.setPlayerInRankedBattle(p2.uuid, false)
            BattleHandler.clearPlayerUsedPokemon(p1.uuid)
            BattleHandler.clearPlayerUsedPokemon(p2.uuid)
        }
    }

    private fun getBattlePokemon(player: ServerPlayerEntity, uuid: UUID): BattlePokemon? {
        val party = Cobblemon.storage.getParty(player)
        val pokemon = party.find { it.uuid == uuid } ?: return null

        if (pokemon.currentHealth <= 0 || pokemon.isFainted()) return null

        val config = CobblemonRanked.config
        return if (config.enableCustomLevel) {
            val originalLevel = pokemon.level
            BattleHandler.savePokemonLevel(pokemon.uuid, originalLevel)
            pokemon.level = config.customBattleLevel
            pokemon.heal()
            BattlePokemon(
                originalPokemon = pokemon,
                effectedPokemon = pokemon,
                postBattleEntityOperation = { entity ->
                    BattleHandler.restorePokemonLevel(pokemon.uuid, pokemon)
                }
            )
        } else {
            BattlePokemon(originalPokemon = pokemon)
        }
    }

    fun endBattle(winnerTeam: DuoTeam, loserTeam: DuoTeam) {
        val allPlayers = listOf(winnerTeam.player1, winnerTeam.player2, loserTeam.player1, loserTeam.player2)
        allPlayers.forEach { player ->
            if (!player.isDisconnected) {
                BattleHandler.teleportBackIfPossible(player)
            }
            BattleHandler.restorePlayerPokemonLevels(player)
            BattleHandler.healPlayerPokemon(player)
            BattleHandler.setPlayerInRankedBattle(player.uuid, false)
            BattleHandler.clearPlayerUsedPokemon(player.uuid)
        }

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

        val oldWinnerElos = winnerData.map { it.elo }
        val oldLoserElos = loserData.map { it.elo }

        val teamEloWinner = winnerData.map { it.elo }.average().roundToInt()
        val teamEloLoser = loserData.map { it.elo }.average().roundToInt()
        val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(
            teamEloWinner,
            teamEloLoser,
            config.eloKFactor,
            config.minElo,
            config.loserProtectionRate
        )

        winnerData.forEachIndexed { index, data ->
            val player = listOf(winnerTeam.player1, winnerTeam.player2)[index]
            val eloDiff = newWinnerElo - oldWinnerElos[index]
            data.apply {
                elo = newWinnerElo
                wins++
                winStreak++
                if (winStreak > bestWinStreak) bestWinStreak = winStreak
            }
            BattleHandler.rankDao.savePlayerData(data)
            if (!player.isDisconnected) {
                BattleHandler.rewardManager.grantRankRewardIfEligible(player, data.getRankTitle(), format, player.server)
                BattleHandler.sendBattleResultMessage(player, data, eloDiff)
                BattleHandler.grantVictoryRewards(player, player.server)
            }
        }

        loserData.forEachIndexed { index, data ->
            val player = listOf(loserTeam.player1, loserTeam.player2)[index]
            val eloDiff = newLoserElo - oldLoserElos[index]
            data.apply {
                elo = newLoserElo
                losses++
                winStreak = 0
            }
            BattleHandler.rankDao.savePlayerData(data)
            if (!player.isDisconnected) {
                BattleHandler.rewardManager.grantRankRewardIfEligible(player, data.getRankTitle(), format, player.server)
                BattleHandler.sendBattleResultMessage(player, data, eloDiff)
            }
        }

        val winnerNames = "${winnerTeam.player1.name.string} & ${winnerTeam.player2.name.string}"
        val loserNames = "${loserTeam.player1.name.string} & ${loserTeam.player2.name.string}"
        val lang = CobblemonRanked.config.defaultLang

        listOf(winnerTeam.player1, winnerTeam.player2).forEach {
            if (!it.isDisconnected) {
                RankUtils.sendTitle(
                    it,
                    MessageConfig.get("duo.end.victory.title", lang),
                    MessageConfig.get("duo.end.victory.subtitle", lang, "loser" to loserNames)
                )
            }
        }

        listOf(loserTeam.player1, loserTeam.player2).forEach {
            if (!it.isDisconnected) {
                RankUtils.sendTitle(
                    it,
                    MessageConfig.get("duo.end.defeat.title", lang),
                    MessageConfig.get("duo.end.defeat.subtitle", lang, "winner" to winnerNames)
                )
            }
        }

        cleanupTeamData(winnerTeam, loserTeam)
    }

    fun tick() {}

    private fun DuoTeam.getActivePlayer(): ServerPlayerEntity = if (currentIndex == 0) player1 else player2
    private fun DuoTeam.getActiveTeam(): List<UUID> = if (currentIndex == 0) team1 else team2
    fun DuoTeam.switchToNextPlayer(): Boolean = if (currentIndex == 0) {
        currentIndex = 1
        true
    } else false

    var DuoTeam.currentIndex: Int
        get() = indices.getOrPut(this) { 0 }
        set(value) {
            indices[this] = value
        }

    fun cleanupTeamData(vararg teams: DuoTeam) {
        val arenasToRelease = mutableSetOf<BattleArena>()

        teams.forEach { team ->
            arenaCache[team]?.first?.let { arenasToRelease.add(it) }
            activePlayers.remove(team.player1.uuid)
            activePlayers.remove(team.player2.uuid)
            activeBattles.entries.removeIf { (_, pair) -> pair.first == team || pair.second == team }
            teamBattleIdMap.remove(team)
            indices.remove(team)
            arenaCache.remove(team)
            pendingTeams.remove(team.player1.uuid)
            pendingTeams.remove(team.player2.uuid)

            BattleHandler.setPlayerInRankedBattle(team.player1.uuid, false)
            BattleHandler.setPlayerInRankedBattle(team.player2.uuid, false)

            BattleHandler.clearPlayerUsedPokemon(team.player1.uuid)
            BattleHandler.clearPlayerUsedPokemon(team.player2.uuid)
        }

        arenasToRelease.forEach { arena ->
            BattleHandler.releaseArena(arena)
        }
    }

    fun clearAll() {
        activeBattles.clear()
        activePlayers.clear()
        pendingTeams.clear()
        teamBattleIdMap.clear()
        indices.clear()
        arenaCache.clear()
    }

    data class DuoTeam(
        val player1: ServerPlayerEntity,
        val player2: ServerPlayerEntity,
        var team1: List<UUID>,
        var team2: List<UUID>,
        val joinTime: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean =
            other is DuoTeam && setOf(player1.uuid, player2.uuid) == setOf(other.player1.uuid, other.player2.uuid)

        override fun hashCode(): Int = player1.uuid.hashCode() + player2.uuid.hashCode()
    }
}