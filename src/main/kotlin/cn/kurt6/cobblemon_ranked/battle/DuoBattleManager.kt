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
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.util.Identifier
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
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

    private val battleTeamCache = ConcurrentHashMap<UUID, Map<UUID, Pokemon>>()
    private val usedPokemonUuids = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    private val clonedPokemonEntities = ConcurrentHashMap<UUID, MutableSet<PokemonEntity>>()

    fun register() {
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (entity is PokemonEntity) {
                val owner = entity.owner
                if (owner is ServerPlayerEntity && (activePlayers.containsKey(owner.uuid) || pendingTeams.containsKey(owner.uuid))) {
                    clonedPokemonEntities.computeIfAbsent(owner.uuid) { ConcurrentHashMap.newKeySet() }.add(entity)
                }
            }
        }
    }

    fun registerBattle(battleId: UUID, team1: DuoTeam, team2: DuoTeam) {
        activeBattles[battleId] = team1 to team2
        activePlayers[team1.getActivePlayer().uuid] = team1
        activePlayers[team2.getActivePlayer().uuid] = team2
        teamBattleIdMap[team1] = battleId
        teamBattleIdMap[team2] = battleId
    }

    fun onBattleStarted(battleId: UUID, p1: ServerPlayerEntity, p2: ServerPlayerEntity) {
        val t1 = pendingTeams.remove(p1.uuid)
        val t2 = pendingTeams.remove(p2.uuid)
        if (t1 != null && t2 != null) {
            registerBattle(battleId, t1, t2)
            CobblemonRanked.logger.info("DuoBattleManager: Registered battle $battleId")
        }
    }

    private fun initializeTeamSession(player: ServerPlayerEntity) {
        if (battleTeamCache.containsKey(player.uuid)) return
        val party = Cobblemon.storage.getParty(player)
        val config = CobblemonRanked.config
        val teamMap = mutableMapOf<UUID, Pokemon>()
        party.forEach { original ->
            val clone = original.clone()
            if (config.enableCustomLevel) clone.level = config.customBattleLevel
            clone.heal()
            teamMap[original.uuid] = clone
        }
        battleTeamCache[player.uuid] = teamMap
    }

    fun handleSelectionDisconnect(loser: ServerPlayerEntity, winner: ServerPlayerEntity) { handleVictory(winner.uuid, loser.uuid) }
    fun updateTeamOrder(playerUuid: UUID, newOrder: List<UUID>) {
        val team = activePlayers[playerUuid] ?: pendingTeams[playerUuid] ?: return
        if (team.player1.uuid == playerUuid) team.team1 = newOrder else if (team.player2.uuid == playerUuid) team.team2 = newOrder
    }
    fun updateBattleState(battle: PokemonBattle) {
        battle.sides.forEach { side -> side.actors.forEach { actor -> if (actor is PlayerBattleActor) actor.pokemonList.forEach { if (it.originalPokemon.currentHealth <= 0 || it.originalPokemon.isFainted()) markAsUsed(actor.uuid, it.originalPokemon) } } }
    }
    fun markPokemonAsFainted(playerUuid: UUID, battle: PokemonBattle) {
        val actor = battle.actors.find { it is PlayerBattleActor && it.uuid == playerUuid } as? PlayerBattleActor ?: return
        actor.pokemonList.forEach { it.originalPokemon.currentHealth = 0; markAsUsed(playerUuid, it.originalPokemon) }
    }
    private fun markAsUsed(playerUuid: UUID, pokemon: Pokemon) {
        val playerCache = battleTeamCache[playerUuid]
        playerCache?.entries?.find { it.value == pokemon }?.key?.let { usedPokemonUuids.computeIfAbsent(playerUuid) { mutableSetOf() }.add(it) }
    }
    fun isPokemonUsed(playerUuid: UUID, originalPokemonUuid: UUID): Boolean = usedPokemonUuids[playerUuid]?.contains(originalPokemonUuid) == true

    fun handlePlayerQuit(player: ServerPlayerEntity) {
        val team = activePlayers[player.uuid] ?: pendingTeams[player.uuid] ?: return
        val battleId = teamBattleIdMap[team]
        val opponentUuid = pendingTeams.entries.find { it.value == team && it.key != player.uuid }?.key ?: activePlayers.entries.find { it.value != team && teamBattleIdMap[it.value] == battleId }?.key
        cleanupAllPokemonEntities(player)
        if (opponentUuid != null) handleVictory(opponentUuid, player.uuid) else cleanupTeamData(team)
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

            if (!nextLoserPlayer.isDisconnected) RankUtils.sendMessage(nextLoserPlayer, MessageConfig.get("duo.next_round.ready", lang, "opponent" to currentWinnerPlayer.name.string))
            if (!currentWinnerPlayer.isDisconnected) RankUtils.sendMessage(currentWinnerPlayer, MessageConfig.get("duo.next_round.win_continue", lang))

            val server = if (!nextLoserPlayer.isDisconnected) nextLoserPlayer.server else currentWinnerPlayer.server
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule({
                server.execute {
                    try {
                        if (nextLoserPlayer.isDisconnected) {
                            handleVictory(currentWinnerPlayer.uuid, nextLoserPlayer.uuid)
                        } else if (currentWinnerPlayer.isDisconnected) {
                            handleVictory(nextLoserPlayer.uuid, currentWinnerPlayer.uuid)
                        } else {
                            cleanupRoundPokemonEntities(loserTeam.player1)
                            cleanupRoundPokemonEntities(loserTeam.player2)
                            cleanupRoundPokemonEntities(winnerTeam.player1)
                            cleanupRoundPokemonEntities(winnerTeam.player2)
                            startNextRound(winnerTeam, loserTeam)
                        }
                    } catch (e: Exception) {
                        CobblemonRanked.logger.error("Error in next round", e)
                        loserTeam.currentIndex = 0
                        endBattle(winnerTeam, loserTeam)
                    }
                }
            }, 3, TimeUnit.SECONDS)
        } else {
            endBattle(winnerTeam, loserTeam)
        }
    }

    fun startNextRound(winnerTeam: DuoTeam, loserTeam: DuoTeam) {
        val lang = CobblemonRanked.config.defaultLang
        val allPlayers = listOf(winnerTeam.player1, winnerTeam.player2, loserTeam.player1, loserTeam.player2)
        val isFirstRound = winnerTeam.currentIndex == 0 && loserTeam.currentIndex == 0

        if (isFirstRound) allPlayers.forEach { initializeTeamSession(it) }

        if (isFirstRound) {
            val activeParticipants = listOf(winnerTeam.getActivePlayer(), loserTeam.getActivePlayer())
            BattleHandler.requestArena(
                activeParticipants,
                2,
                onArenaFound = { arena, positions ->
                    arenaCache[winnerTeam] = Pair(arena, positions)
                    arenaCache[loserTeam] = Pair(arena, positions)
                    executeRoundStart(winnerTeam, loserTeam, arena, positions, true)
                },
                onAbort = {
                }
            )
        } else {
            val cache = arenaCache[winnerTeam] ?: return
            executeRoundStart(winnerTeam, loserTeam, cache.first, cache.second, false)
        }
    }

    private fun executeRoundStart(winnerTeam: DuoTeam, loserTeam: DuoTeam, arena: BattleArena, positions: List<ArenaCoordinate>, isFirstRound: Boolean) {
        val winnerPos = positions.getOrNull(0) ?: positions[0]
        val loserPos = positions.getOrNull(1) ?: positions[1]
        val p1 = winnerTeam.getActivePlayer()
        val p2 = loserTeam.getActivePlayer()

        if (p1.isDisconnected || p2.isDisconnected) {
            val winner = if (p1.isDisconnected) p2 else p1
            val loser = if (p1.isDisconnected) p1 else p2
            handleVictory(winner.uuid, loser.uuid)
            return
        }

        val worldId = Identifier.tryParse(arena.world) ?: return
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId)
        val world = p1.server.getWorld(worldKey) ?: return
        p1.teleport(world, winnerPos.x, winnerPos.y, winnerPos.z, 0f, 0f)
        p2.teleport(world, loserPos.x, loserPos.y, loserPos.z, 0f, 0f)

        pendingTeams[p1.uuid] = winnerTeam
        pendingTeams[p2.uuid] = loserTeam

        if (isFirstRound) {
            val lang = CobblemonRanked.config.defaultLang
            val all = listOf(winnerTeam.player1, winnerTeam.player2, loserTeam.player1, loserTeam.player2)
            val matchMessage = MessageConfig.get("duo.match.announce", lang, "t1p1" to winnerTeam.player1.name.string, "t1p2" to winnerTeam.player2.name.string, "t2p1" to loserTeam.player1.name.string, "t2p2" to loserTeam.player2.name.string)
            all.forEach { if(!it.isDisconnected) it.sendMessage(Text.literal(matchMessage)) }
            all.forEach { if(!it.isDisconnected) BattleHandler.setReturnLocation(it.uuid, it.serverWorld, Triple(it.x, it.y, it.z)) }
        }

        TeamSelectionManager.startSelection(
            player1 = p1,
            team1Uuids = winnerTeam.getActiveTeam(),
            p1SkipSelection = !isFirstRound,
            player2 = p2,
            team2Uuids = loserTeam.getActiveTeam(),
            p2SkipSelection = false,
            format = BattleFormat.GEN_9_SINGLES,
            formatName = "2v2singles",
            p1Pos = winnerPos,
            p2Pos = loserPos
        )
    }

    fun startActualBattle(p1: ServerPlayerEntity, t1Uuids: List<UUID>, p2: ServerPlayerEntity, t2Uuids: List<UUID>, format: BattleFormat, formatName: String) {
        val team1 = t1Uuids.mapNotNull { getCachedPokemon(p1, it) }
        val team2 = t2Uuids.mapNotNull { getCachedPokemon(p2, it) }
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
            pendingTeams.remove(p1.uuid); pendingTeams.remove(p2.uuid)
        }
    }

    private fun cleanupRoundPokemonEntities(player: ServerPlayerEntity) {
        val entities = clonedPokemonEntities[player.uuid]
        entities?.forEach { try { if (!it.isRemoved) it.discard() } catch (e: Exception) {} }
        entities?.clear()
        BattleHandler.markPlayerForCleanup(player)
    }
    private fun cleanupAllPokemonEntities(player: ServerPlayerEntity) {
        val entities = clonedPokemonEntities.remove(player.uuid)
        entities?.forEach { try { if (!it.isRemoved) it.discard() } catch (e: Exception) {} }
        BattleHandler.markPlayerForCleanup(player)
    }
    private fun getCachedPokemon(player: ServerPlayerEntity, originalUuid: UUID): BattlePokemon? {
        val cache = battleTeamCache[player.uuid] ?: return null
        val clonePokemon = cache[originalUuid] ?: return null
        if (clonePokemon.currentHealth <= 0 || clonePokemon.isFainted()) return null
        return BattlePokemon(clonePokemon)
    }

    fun endBattle(winnerTeam: DuoTeam, loserTeam: DuoTeam) {
        val allPlayers = listOf(winnerTeam.player1, winnerTeam.player2, loserTeam.player1, loserTeam.player2)
        allPlayers.forEach { player ->
            if (!player.isDisconnected) {
                cleanupAllPokemonEntities(player)
                BattleHandler.teleportBackIfPossible(player)
                val server = player.server
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule({
                    server.execute { BattleHandler.cleanupBattleEntities(player) }
                }, 1, TimeUnit.SECONDS)
            }
        }
        val format = "2v2singles"
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId

        val winnerData = listOf(winnerTeam.player1, winnerTeam.player2).map {
            BattleHandler.rankDao.getPlayerData(it.uuid, seasonId, format) ?: PlayerRankData(playerId = it.uuid, seasonId = seasonId, format = format, playerName = it.name.string, elo = CobblemonRanked.config.initialElo)
        }
        val loserData = listOf(loserTeam.player1, loserTeam.player2).map {
            BattleHandler.rankDao.getPlayerData(it.uuid, seasonId, format) ?: PlayerRankData(playerId = it.uuid, seasonId = seasonId, format = format, playerName = it.name.string, elo = CobblemonRanked.config.initialElo)
        }

        val oldWinnerElos = winnerData.map { it.elo }
        val oldLoserElos = loserData.map { it.elo }

        val teamEloWinner = winnerData.map { it.elo }.average().roundToInt()
        val teamEloLoser = loserData.map { it.elo }.average().roundToInt()
        val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(teamEloWinner, teamEloLoser, config.eloKFactor, config.minElo, config.loserProtectionRate)

        winnerData.forEachIndexed { index, data ->
            val player = listOf(winnerTeam.player1, winnerTeam.player2)[index]
            val eloDiff = newWinnerElo - oldWinnerElos[index]
            data.apply { elo = newWinnerElo; wins++; winStreak++; if (winStreak > bestWinStreak) bestWinStreak = winStreak }
            BattleHandler.rankDao.savePlayerData(data)
            if(!player.isDisconnected) {
                BattleHandler.rewardManager.grantRankRewardIfEligible(player, data.getRankTitle(), format, player.server)
                BattleHandler.sendBattleResultMessage(player, data, eloDiff)
                BattleHandler.grantVictoryRewards(player, player.server)
            }
        }
        loserData.forEachIndexed { index, data ->
            val player = listOf(loserTeam.player1, loserTeam.player2)[index]
            val eloDiff = newLoserElo - oldLoserElos[index]
            data.apply { elo = newLoserElo; losses++; winStreak = 0 }
            BattleHandler.rankDao.savePlayerData(data)
            if(!player.isDisconnected) {
                BattleHandler.rewardManager.grantRankRewardIfEligible(player, data.getRankTitle(), format, player.server)
                BattleHandler.sendBattleResultMessage(player, data, eloDiff)
            }
        }

        val winnerNames = "${winnerTeam.player1.name.string} & ${winnerTeam.player2.name.string}"
        val loserNames = "${loserTeam.player1.name.string} & ${loserTeam.player2.name.string}"
        val lang = CobblemonRanked.config.defaultLang
        listOf(winnerTeam.player1, winnerTeam.player2).forEach { if(!it.isDisconnected) RankUtils.sendTitle(it, MessageConfig.get("duo.end.victory.title", lang), MessageConfig.get("duo.end.victory.subtitle", lang, "loser" to loserNames)) }
        listOf(loserTeam.player1, loserTeam.player2).forEach { if(!it.isDisconnected) RankUtils.sendTitle(it, MessageConfig.get("duo.end.defeat.title", lang), MessageConfig.get("duo.end.defeat.subtitle", lang, "winner" to winnerNames)) }

        cleanupTeamData(winnerTeam, loserTeam)
    }

    fun tick() {}
    private fun DuoTeam.getActivePlayer(): ServerPlayerEntity = if (currentIndex == 0) player1 else player2
    private fun DuoTeam.getActiveTeam(): List<UUID> = if (currentIndex == 0) team1 else team2
    fun DuoTeam.switchToNextPlayer(): Boolean = if (currentIndex == 0) { currentIndex = 1; true } else false
    var DuoTeam.currentIndex: Int
        get() = indices.getOrPut(this) { 0 }
        set(value) { indices[this] = value }

    fun cleanupTeamData(vararg teams: DuoTeam) {
        val arenaToRelease = arenaCache[teams.firstOrNull()]?.first
        teams.forEach { team ->
            activePlayers.remove(team.player1.uuid); activePlayers.remove(team.player2.uuid)
            activeBattles.entries.removeIf { (_, pair) -> pair.first == team || pair.second == team }
            teamBattleIdMap.remove(team); indices.remove(team); arenaCache.remove(team)
            pendingTeams.remove(team.player1.uuid); pendingTeams.remove(team.player2.uuid)
            battleTeamCache.remove(team.player1.uuid); battleTeamCache.remove(team.player2.uuid)
            usedPokemonUuids.remove(team.player1.uuid); usedPokemonUuids.remove(team.player2.uuid)
            cleanupAllPokemonEntities(team.player1); cleanupAllPokemonEntities(team.player2)
        }
        if (arenaToRelease != null) BattleHandler.releaseArena(arenaToRelease)
    }

    fun clearAll() {
        activeBattles.clear(); activePlayers.clear(); pendingTeams.clear()
        battleTeamCache.clear(); usedPokemonUuids.clear(); teamBattleIdMap.clear()
        indices.clear(); arenaCache.clear()
        clonedPokemonEntities.values.flatten().forEach { if(!it.isRemoved) it.discard() }
        clonedPokemonEntities.clear()
    }

    data class DuoTeam(val player1: ServerPlayerEntity, val player2: ServerPlayerEntity, var team1: List<UUID>, var team2: List<UUID>, val joinTime: Long = System.currentTimeMillis()) {
        override fun equals(other: Any?): Boolean = other is DuoTeam && setOf(player1.uuid, player2.uuid) == setOf(other.player1.uuid, other.player2.uuid)
        override fun hashCode(): Int = player1.uuid.hashCode() + player2.uuid.hashCode()
    }
}