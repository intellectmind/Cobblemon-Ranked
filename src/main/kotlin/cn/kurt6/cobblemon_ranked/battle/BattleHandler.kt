package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.*
import cn.kurt6.cobblemon_ranked.config.ArenaCoordinate
import cn.kurt6.cobblemon_ranked.config.BattleArena
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.data.PlayerRankData
import cn.kurt6.cobblemon_ranked.util.PokemonUsageValidator
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

object BattleHandler {
    private val logger = LoggerFactory.getLogger(BattleHandler::class.java)

    private val rankedBattles = ConcurrentHashMap<UUID, String>()
    private val battleToIdMap = ConcurrentHashMap<PokemonBattle, UUID>()

    private val occupiedArenas = ConcurrentHashMap.newKeySet<BattleArena>()
    private val battleIdToArena = ConcurrentHashMap<UUID, BattleArena>()
    val playerToArena = ConcurrentHashMap<UUID, BattleArena>()

    private val playersInRankedBattle = ConcurrentHashMap.newKeySet<UUID>()

    private val pokemonOriginalLevels = ConcurrentHashMap<UUID, Int>()

    data class PendingBattleRequest(
        val players: List<ServerPlayerEntity>,
        val requiredSeats: Int,
        val onArenaFound: (BattleArena, List<ArenaCoordinate>) -> Unit,
        val onAbort: (ServerPlayerEntity) -> Unit,
        var assignedArena: BattleArena? = null
    )
    private val pendingRequests = ConcurrentLinkedQueue<PendingBattleRequest>()

    private val config get() = CobblemonRanked.config
    val rankDao get() = CobblemonRanked.rankDao
    val rewardManager get() = CobblemonRanked.rewardManager
    private val seasonManager get() = CobblemonRanked.seasonManager
    private val usedPokemonUuids = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    private val returnLocations = ConcurrentHashMap<UUID, Pair<ServerWorld, Triple<Double, Double, Double>>>()

    fun requestArena(
        players: List<ServerPlayerEntity>,
        requiredSeats: Int,
        onArenaFound: (BattleArena, List<ArenaCoordinate>) -> Unit,
        onAbort: (ServerPlayerEntity) -> Unit
    ) {
        val selected = synchronized(occupiedArenas) {
            val arenas = CobblemonRanked.config.battleArenas
            val suitableAndFree = arenas.filter {
                it.playerPositions.size >= requiredSeats && !occupiedArenas.contains(it)
            }
            suitableAndFree.randomOrNull()
        }

        if (selected != null) {
            lockArena(selected, players)
            onArenaFound(selected, selected.playerPositions.take(requiredSeats))
        } else {
            val request = PendingBattleRequest(players, requiredSeats, onArenaFound, onAbort)
            pendingRequests.add(request)
            val lang = CobblemonRanked.config.defaultLang
            players.forEach {
                RankUtils.sendMessage(it, MessageConfig.get("queue.waiting_for_arena", lang, "position" to pendingRequests.size.toString()))
            }
        }
    }

    fun isPlayerWaitingForArena(uuid: UUID): Boolean {
        return pendingRequests.any { req -> req.players.any { it.uuid == uuid } }
    }

    fun removePlayerFromWaitingQueue(uuid: UUID) {
        val iterator = pendingRequests.iterator()
        while (iterator.hasNext()) {
            val request = iterator.next()
            if (request.players.any { it.uuid == uuid }) {
                iterator.remove()

                request.assignedArena?.let { arena ->
                    releaseArena(arena)
                }

                val lang = CobblemonRanked.config.defaultLang
                request.players.forEach { player ->
                    if (player.uuid != uuid) {
                        RankUtils.sendMessage(player, MessageConfig.get("queue.opponent_disconnected", lang))
                        request.onAbort(player)
                    }
                }
                return
            }
        }
    }

    private fun lockArena(arena: BattleArena, players: List<ServerPlayerEntity>) {
        occupiedArenas.add(arena)
        players.forEach { playerToArena[it.uuid] = arena }
    }

    fun releaseArena(arena: BattleArena) {
        val shouldProcess = synchronized(occupiedArenas) {
            val removed = occupiedArenas.remove(arena)
            if (removed) {
                logger.debug("Released arena: ${arena.world}")
            }
            removed
        }
        if (shouldProcess) {
            processPendingRequests()
        }
    }

    fun releaseArenaForPlayer(uuid: UUID) {
        val arena = playerToArena.remove(uuid)
        if (arena != null) {
            logger.debug("Releasing arena for player $uuid")
            releaseArena(arena)
        }
    }

    private fun processPendingRequests() {
        if (pendingRequests.isEmpty()) return

        val request = pendingRequests.peek() ?: return

        if (request.players.any { it.isDisconnected }) {
            pendingRequests.poll()
            request.assignedArena?.let { releaseArena(it) }
            processPendingRequests()
            return
        }

        val selected = synchronized(occupiedArenas) {
            val arenas = CobblemonRanked.config.battleArenas
            val suitableAndFree = arenas.filter {
                it.playerPositions.size >= request.requiredSeats && !occupiedArenas.contains(it)
            }
            suitableAndFree.randomOrNull()
        }

        if (selected != null) {
            val validRequest = pendingRequests.poll()
            val lang = CobblemonRanked.config.defaultLang
            validRequest.players.forEach {
                RankUtils.sendMessage(it, MessageConfig.get("queue.arena_found", lang))
            }
            lockArena(selected, validRequest.players)
            validRequest.assignedArena = selected
            validRequest.onArenaFound(selected, selected.playerPositions.take(validRequest.requiredSeats))
        }
    }

    fun setReturnLocation(uuid: UUID, world: ServerWorld, location: Triple<Double, Double, Double>) {
        returnLocations[uuid] = Pair(world, location)
        rankDao.saveReturnLocation(uuid, world.registryKey.value.toString(), location.first, location.second, location.third)
    }

    private fun cleanupBattleData(battle: PokemonBattle) {
        val battleId = battleToIdMap.remove(battle)
        if (battleId != null) {
            rankedBattles.remove(battleId)
        }
    }

    private fun finalBattleCleanup(battle: PokemonBattle, battleId: UUID?) {
        try {
            var arena: BattleArena? = null
            if (battleId != null) {
                val format = rankedBattles[battleId]
                if (format != "2v2singles") {
                    arena = battleIdToArena.remove(battleId)
                }
                rankedBattles.remove(battleId)
            }
            battleToIdMap.entries.removeIf { it.key == battle || it.value == battleId }

            if (arena != null) {
                releaseArena(arena)
            }
        } catch (e: Exception) {
            logger.error("Error during final battle cleanup", e)
        }
    }

    fun validateTeam(player: ServerPlayerEntity, teamUuids: List<UUID>, format: BattleFormat): Boolean {
        val lang = CobblemonRanked.config.defaultLang
        val party = Cobblemon.storage.getParty(player)
        val partyUuids = party.mapNotNull { it?.uuid }.toSet()

        if (!teamUuids.all { it in partyUuids }) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.invalid_team_selection", lang))
            return false
        }

        val validPokemon = teamUuids.mapNotNull { uuid ->
            party.find { it?.uuid == uuid }
        }

        if (validPokemon.size != teamUuids.size) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.invalid_team_selection", lang))
            return false
        }

        val pokemonList = validPokemon
        val config = CobblemonRanked.config

        if (format == BattleFormat.GEN_9_DOUBLES && pokemonList.size < 2) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.too_small", lang, "min" to "2"))
            return false
        }

        if (pokemonList.size < config.minTeamSize) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.too_small", lang, "min" to config.minTeamSize.toString()))
            return false
        }
        if (pokemonList.size > config.maxTeamSize) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.too_large", lang, "max" to config.maxTeamSize.toString()))
            return false
        }

        val violations = mutableListOf<String>()
        val speciesCount = mutableMapOf<String, Int>()
        val restrictedCount = mutableMapOf<String, Int>()
        val heldItems = mutableListOf<String>()
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId

        pokemonList.forEach { pokemon ->
            val speciesName = pokemon.species.name.lowercase()

            if (config.bannedPokemon.map { it.lowercase() }.contains(speciesName)) {
                violations.add("banned_pokemon:${pokemon.species.name}")
            }

            if (config.restrictedPokemon.map { it.lowercase() }.contains(speciesName)) {
                restrictedCount["restricted"] = restrictedCount.getOrDefault("restricted", 0) + 1
            }

            if (config.maxLevel > 0 && pokemon.level > config.maxLevel) {
                violations.add("overlevel:${pokemon.species.name}(Lv.${pokemon.level})")
            }

            if (!config.allowDuplicateSpecies) {
                speciesCount[pokemon.species.name] = speciesCount.getOrDefault(pokemon.species.name, 0) + 1
            }

            if (!config.allowDuplicateItems) {
                val heldItem = pokemon.heldItem()
                if (!heldItem.isEmpty) {
                    heldItems.add(Registries.ITEM.getId(heldItem.item).toString())
                }
            }

            if (isEgg(pokemon)) {
                violations.add("egg:${pokemon.species.name}")
            } else if (isFainted(pokemon)) {
                violations.add("fainted:${pokemon.species.name}")
            }

            val bannedHeldItems = config.bannedHeldItems.map { it.lowercase() }
            val stack = pokemon.heldItem()
            if (!stack.isEmpty) {
                val itemId = Registries.ITEM.getId(stack.item).toString().lowercase()
                if (itemId in bannedHeldItems) {
                    violations.add("banned_held:${pokemon.species.name}($itemId)")
                }
            }

            val bannedNatures = config.bannedNatures.map { it.lowercase() }
            if (pokemon.nature.name.toString().lowercase() in bannedNatures) {
                violations.add("banned_nature:${pokemon.species.name}(${pokemon.nature.name})")
            }

            val bannedAbilities = config.bannedAbilities.map { it.uppercase() }
            if (pokemon.ability.name.uppercase() in bannedAbilities) {
                violations.add("banned_ability:${pokemon.species.name}(${pokemon.ability.name})")
            }

            val bannedGenders = config.bannedGenders.map { it.uppercase() }
            if (pokemon.gender?.name?.uppercase() in bannedGenders) {
                violations.add("banned_gender:${pokemon.species.name}(${pokemon.gender?.name})")
            }

            val bannedMoves = config.bannedMoves.map { it.lowercase().trim() }
            val pokemonBannedMoves = pokemon.moveSet.getMovesWithNulls()
                .mapNotNull { move ->
                    val moveName = move?.name?.toString()?.lowercase()
                    if (moveName in bannedMoves) moveName else null
                }
            if (pokemonBannedMoves.isNotEmpty()) {
                violations.add("banned_moves:${pokemon.species.name}(${pokemonBannedMoves.joinToString(",")})")
            }

            if (config.bannedShiny && pokemon.shiny) {
                violations.add("shiny:${pokemon.species.name}")
            }

            val usageResult = PokemonUsageValidator.validateUsageRestrictions(player, pokemon, seasonId, lang)
            if (!usageResult.isValid) {
                usageResult.errorMessage?.let { violations.add(it) }
            }
        }

        val restrictedTotal = restrictedCount.getOrDefault("restricted", 0)
        if (restrictedTotal > config.maxRestrictedCount) {
            val restrictedNames = pokemonList
                .filter { config.restrictedPokemon.map { r -> r.lowercase() }.contains(it.species.name.lowercase()) }
                .joinToString(", ") { it.species.name }
            violations.add("restricted_exceed:${config.maxRestrictedCount}($restrictedNames)")
        }

        if (!config.allowDuplicateSpecies) {
            speciesCount.filter { it.value > 1 }.keys.forEach { species ->
                violations.add("duplicate_species:$species")
            }
        }

        if (!config.allowDuplicateItems) {
            val duplicateItems = heldItems.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            if (duplicateItems.isNotEmpty()) {
                violations.add("duplicate_items:${duplicateItems.joinToString(",")}")
            }
        }

        val bannedItems = config.bannedCarriedItems.map { it.lowercase() }
        val inventory = player.inventory
        val violatedItems = inventory.main
            .filterNot { it.isEmpty }
            .map { Registries.ITEM.getId(it.item).toString().lowercase() }
            .filter { it in bannedItems }

        if (violatedItems.isNotEmpty()) {
            violations.add("player_banned_items:${violatedItems.joinToString(",")}")
        }

        if (violations.isNotEmpty()) {
            violations.forEach { violation ->
                val parts = violation.split(":", limit = 2)
                val type = parts[0]
                val detail = parts.getOrNull(1) ?: ""

                when (type) {
                    "banned_pokemon" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_pokemon", lang, "names" to detail))
                    "restricted_exceed" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.restricted_exceed", lang, "max" to config.maxRestrictedCount.toString(), "names" to detail))
                    "overlevel" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.overleveled", lang, "max" to config.maxLevel.toString(), "names" to detail))
                    "duplicate_species" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.duplicates", lang, "names" to detail))
                    "duplicate_items" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.duplicate_items", lang))
                    "egg", "fainted" -> {
                        val status = if (type == "egg") MessageConfig.get("battle.status.egg", lang) else MessageConfig.get("battle.status.fainted", lang)
                        RankUtils.sendMessage(player, MessageConfig.get("battle.team.invalid", lang, "entries" to "$detail($status)"))
                    }
                    "banned_held" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_held_items", lang, "names" to detail))
                    "banned_nature" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_nature", lang, "names" to detail))
                    "banned_ability" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_ability", lang, "names" to detail))
                    "banned_gender" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_gender", lang, "names" to detail))
                    "banned_moves" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_moves", lang, "names" to detail))
                    "shiny" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_shiny", lang, "names" to detail))
                    "player_banned_items" -> RankUtils.sendMessage(player, MessageConfig.get("battle.player.banned_items", lang, "items" to detail))
                    else -> RankUtils.sendMessage(player, detail)
                }
            }
            return false
        }

        return true
    }

    private fun isEgg(pokemon: Pokemon): Boolean = pokemon.state.name.equals("egg", ignoreCase = true)
    private fun isFainted(pokemon: Pokemon): Boolean = pokemon.currentHealth <= 0 || pokemon.isFainted()

    fun savePokemonLevel(pokemonUuid: UUID, level: Int) {
        pokemonOriginalLevels[pokemonUuid] = level
    }

    fun restorePokemonLevel(pokemonUuid: UUID, pokemon: com.cobblemon.mod.common.pokemon.Pokemon) {
        val originalLevel = pokemonOriginalLevels.remove(pokemonUuid)
        if (originalLevel != null) {
            pokemon.level = originalLevel
        }
    }

    fun restorePlayerPokemonLevels(player: ServerPlayerEntity) {
        val party = Cobblemon.storage.getParty(player)
        party.forEach { pokemon ->
            if (pokemon != null) {
                val originalLevel = pokemonOriginalLevels.remove(pokemon.uuid)
                if (originalLevel != null) {
                    pokemon.level = originalLevel
                }
            }
        }
    }

    fun healPlayerPokemon(player: ServerPlayerEntity) {
        if (!config.restorePokemonHpAfterBattle) return
        val party = Cobblemon.storage.getParty(player)
        party.forEach { pokemon ->
            if (pokemon != null && !pokemon.isFainted() && pokemon.currentHealth < pokemon.maxHealth) {
                pokemon.heal()
            }
        }
    }

    fun isPlayerInRankedBattle(player: ServerPlayerEntity): Boolean {
        val battle = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) ?: return false
        return battleToIdMap.containsKey(battle)
    }

    fun setPlayerInRankedBattle(playerId: UUID, inBattle: Boolean) {
        if (inBattle) {
            playersInRankedBattle.add(playerId)
        } else {
            playersInRankedBattle.remove(playerId)
        }
    }

    fun isPlayerBlockBreakingRestricted(playerId: UUID): Boolean {
        return config.preventBlockBreaking && playersInRankedBattle.contains(playerId)
    }

    fun cleanupStaleRankedBattleMarkers(server: MinecraftServer) {
        val toRemove = mutableListOf<UUID>()
        for (playerId in playersInRankedBattle) {
            val player = server.playerManager.getPlayer(playerId)
            if (player == null || !isPlayerInRankedBattle(player)) {
                toRemove.add(playerId)
            }
        }
        if (toRemove.isNotEmpty()) {
            playersInRankedBattle.removeAll(toRemove)
            logger.debug("Cleaned up ${toRemove.size} stale ranked battle markers")
        }
    }

    fun clearAllRankedBattleMarkers() {
        playersInRankedBattle.clear()
    }

    fun register() {
        PlayerBlockBreakEvents.BEFORE.register { world, player, pos, state, blockEntity ->
            if (player !is ServerPlayerEntity) return@register true
            if (!config.preventBlockBreaking) return@register true
            return@register !isPlayerBlockBreakingRestricted(player.uuid)
        }

        CobblemonEvents.BATTLE_VICTORY.subscribe { event: BattleVictoryEvent ->
            val battle = event.battle
            val battleId = battleToIdMap[battle]
            val format = battleId?.let { rankedBattles[it] }
            if (battleId == null || format == null) return@subscribe

            try {
                if (format == "2v2singles") {
                    DuoBattleManager.updateBattleState(battle)
                    val winners = event.winners.filterIsInstance<PlayerBattleActor>()
                    val losers = event.losers.filterIsInstance<PlayerBattleActor>()
                    if (winners.size == 1 && losers.size == 1) {
                        val winnerId = winners.first().uuid
                        val loserId = losers.first().uuid
                        DuoBattleManager.handleVictory(winnerId, loserId)
                    }
                } else {
                    onBattleVictory(event)
                }
            } finally {
                finalBattleCleanup(battle, battleId)
            }
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            val player = handler.player
            val battle = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player)
            server.execute {
                try {
                    val battleId = if (battle != null) battleToIdMap[battle] else null

                    if (battle != null && battleId != null && rankedBattles.containsKey(battleId)) {
                        handleDisconnectAsFlee(battle, player)
                    } else {
                        forceCleanupPlayerBattleData(player)
                        DuoBattleManager.handlePlayerQuit(player)

                        val arena = playerToArena.remove(player.uuid)
                        if (arena != null) releaseArena(arena)

                        removePlayerFromWaitingQueue(player.uuid)
                    }

                    setPlayerInRankedBattle(player.uuid, false)
                } catch (e: Exception) {
                    logger.error("Error handling player disconnect", e)
                    forceCleanupPlayerBattleData(player)
                    setPlayerInRankedBattle(player.uuid, false)
                }
            }
        }

        ServerPlayConnectionEvents.JOIN.register { handler, sender, server ->
            val player = handler.player
            server.execute {
                setPlayerInRankedBattle(player.uuid, false)
            }
        }
    }

    fun handleSelectionPhaseDisconnect(winner: ServerPlayerEntity, loser: ServerPlayerEntity, formatName: String) {
        val seasonId = seasonManager.currentSeasonId
        val winnerData = getOrCreatePlayerData(winner.uuid, seasonId, formatName).apply { playerName = winner.name.string }
        val loserData = getOrCreatePlayerData(loser.uuid, seasonId, formatName).apply { playerName = loser.name.string }

        val oldWinnerElo = winnerData.elo
        val oldLoserElo = loserData.elo

        val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(winnerData.elo, loserData.elo, config.eloKFactor, config.minElo, config.loserProtectionRate)

        val eloDiffWinner = newWinnerElo - oldWinnerElo
        val eloDiffLoser = newLoserElo - oldLoserElo

        winnerData.apply { elo = newWinnerElo; wins++; winStreak++; if (winStreak > bestWinStreak) bestWinStreak = winStreak }
        loserData.apply { elo = newLoserElo; losses++; winStreak = 0; fleeCount++ }

        rankDao.savePlayerData(winnerData)
        rankDao.savePlayerData(loserData)

        val arena1 = playerToArena.remove(winner.uuid)
        val arena2 = playerToArena.remove(loser.uuid)
        val arena = arena1 ?: arena2
        if (arena != null) releaseArena(arena)

        restorePlayerPokemonLevels(winner)
        restorePlayerPokemonLevels(loser)

        healPlayerPokemon(winner)
        healPlayerPokemon(loser)

        setPlayerInRankedBattle(winner.uuid, false)
        setPlayerInRankedBattle(loser.uuid, false)

        if (!winner.isDisconnected) {
            RankUtils.sendMessage(winner, MessageConfig.get("battle.disconnect.winner", config.defaultLang, "elo" to winnerData.elo.toString()))
            sendBattleResultMessage(winner, winnerData, eloDiffWinner)
            grantVictoryRewards(winner, winner.server)
            teleportBackIfPossible(winner)
            rewardManager.grantRankRewardIfEligible(winner, winnerData.getRankTitle(), formatName, winner.server)
        }
        clearPlayerUsedPokemon(winner.uuid)
        clearPlayerUsedPokemon(loser.uuid)
    }

    private fun handleDisconnectAsFlee(battle: PokemonBattle, disconnected: ServerPlayerEntity) {
        val battleId = battleToIdMap[battle]
        val formatName = battleId?.let { rankedBattles[it] } ?: return

        try {
            if (formatName == "2v2singles") {
                DuoBattleManager.handlePlayerQuit(disconnected)
                cleanupBattleData(battle)
                setPlayerInRankedBattle(disconnected.uuid, false)
                clearPlayerUsedPokemon(disconnected.uuid)

                val arena = playerToArena.remove(disconnected.uuid)
                if (arena != null) {
                    releaseArena(arena)
                }
                return
            }

            val seasonId = seasonManager.currentSeasonId
            val actors = battle.actors.filterIsInstance<PlayerBattleActor>()
            val disconnectedActor = actors.find { it.uuid == disconnected.uuid }
            val winner = actors.find { it.uuid != disconnected.uuid }?.entity as? ServerPlayerEntity

            if (disconnectedActor == null || winner == null) {
                cleanupBattleData(battle)
                restorePlayerPokemonLevels(disconnected)
                setPlayerInRankedBattle(disconnected.uuid, false)
                clearPlayerUsedPokemon(disconnected.uuid)

                val arena = playerToArena.remove(disconnected.uuid)
                if (arena != null) {
                    releaseArena(arena)
                }
                return
            }

            markPokemonAsUsed(disconnected.uuid, UUID.randomUUID())

            val winnerData = getOrCreatePlayerData(winner.uuid, seasonId, formatName).apply { playerName = winner.name.string }
            val loserData = getOrCreatePlayerData(disconnected.uuid, seasonId, formatName).apply { playerName = disconnected.name.string }

            val oldWinnerElo = winnerData.elo
            val oldLoserElo = loserData.elo

            val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(winnerData.elo, loserData.elo, config.eloKFactor, config.minElo, config.loserProtectionRate)

            val eloDiffWinner = newWinnerElo - oldWinnerElo
            val eloDiffLoser = newLoserElo - oldLoserElo

            winnerData.apply { elo = newWinnerElo; wins++; winStreak++; if (winStreak > bestWinStreak) bestWinStreak = winStreak }
            loserData.apply { elo = newLoserElo; losses++; winStreak = 0; fleeCount++ }

            rankDao.savePlayerData(winnerData)
            rankDao.savePlayerData(loserData)

            restorePlayerPokemonLevels(winner)
            restorePlayerPokemonLevels(disconnected)

            healPlayerPokemon(winner)
            healPlayerPokemon(disconnected)

            setPlayerInRankedBattle(disconnected.uuid, false)
            setPlayerInRankedBattle(winner.uuid, false)

            clearPlayerUsedPokemon(disconnected.uuid)
            clearPlayerUsedPokemon(winner.uuid)

            val arena1 = playerToArena.remove(disconnected.uuid)
            val arena2 = playerToArena.remove(winner.uuid)
            val arena = arena1 ?: arena2
            if (arena != null) {
                releaseArena(arena)
            }

            if (!winner.isDisconnected) {
                RankUtils.sendMessage(winner, MessageConfig.get("battle.disconnect.winner", config.defaultLang, "elo" to winnerData.elo.toString()))
                sendBattleResultMessage(winner, winnerData, eloDiffWinner)
                grantVictoryRewards(winner, winner.server)
                teleportBackIfPossible(winner)
                rewardManager.grantRankRewardIfEligible(winner, winnerData.getRankTitle(), formatName, winner.server)
            }

        } catch (e: Exception) {
            logger.error("Error handling disconnect as flee", e)
            restorePlayerPokemonLevels(disconnected)
            setPlayerInRankedBattle(disconnected.uuid, false)
            clearPlayerUsedPokemon(disconnected.uuid)

            val arena = playerToArena.remove(disconnected.uuid)
            if (arena != null) {
                releaseArena(arena)
            }
        } finally {
            if (battleId != null) {
                battleToIdMap.remove(battle)
            }
        }
    }

    fun markAsRanked(battleId: UUID, formatName: String) {
        rankedBattles[battleId] = formatName
    }

    fun registerBattle(battle: PokemonBattle, battleId: UUID) {
        battleToIdMap[battle] = battleId
        val player = battle.actors.filterIsInstance<PlayerBattleActor>().firstOrNull()?.entity as? ServerPlayerEntity
        if (player != null) {
            val arena = playerToArena[player.uuid]
            if (arena != null) battleIdToArena[battleId] = arena
        }

        battle.actors.filterIsInstance<PlayerBattleActor>().forEach { actor ->
            actor.entity?.let { entity ->
                if (entity is ServerPlayerEntity) {
                    setPlayerInRankedBattle(entity.uuid, true)
                }
            }
        }
    }

    fun onBattleVictory(event: BattleVictoryEvent) {
        val battle = event.battle
        val battleId = battleToIdMap[battle]
        val formatName = battleId?.let { rankedBattles[it] }
        if (battleId == null || formatName == null) return

        try {
            val winners = extractPlayerActors(event.winners).mapNotNull { it.entity as? ServerPlayerEntity }
            val losers = extractPlayerActors(event.losers).mapNotNull { it.entity as? ServerPlayerEntity }

            val winner = winners.firstOrNull()
            val loser = losers.firstOrNull()

            if (winner != null && loser != null) {
                val seasonId = seasonManager.currentSeasonId
                val winnerData = getOrCreatePlayerData(winner.uuid, seasonId, formatName)
                val loserData = getOrCreatePlayerData(loser.uuid, seasonId, formatName)
                winnerData.playerName = winner.name.string
                loserData.playerName = loser.name.string

                val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(winnerData.elo, loserData.elo, config.eloKFactor, config.minElo, config.loserProtectionRate)
                val eloDiffWinner = newWinnerElo - winnerData.elo
                val eloDiffLoser = newLoserElo - loserData.elo

                winnerData.apply { elo = newWinnerElo; wins++; winStreak++; if (winStreak > bestWinStreak) bestWinStreak = winStreak }
                loserData.apply { elo = newLoserElo; losses++; winStreak = 0 }

                rankDao.savePlayerData(winnerData)
                rankDao.savePlayerData(loserData)

                grantVictoryRewards(winner, winner.server)
                recordPokemonUsage(listOf(winner, loser), seasonId)
                sendBattleResultMessage(winner, winnerData, eloDiffWinner)
                sendBattleResultMessage(loser, loserData, eloDiffLoser)
                rewardManager.grantRankRewardIfEligible(winner, winnerData.getRankTitle(), formatName, winner.server)
                rewardManager.grantRankRewardIfEligible(loser, loserData.getRankTitle(), formatName, loser.server)

                restorePlayerPokemonLevels(winner)
                restorePlayerPokemonLevels(loser)

                healPlayerPokemon(winner)
                healPlayerPokemon(loser)

                teleportBackIfPossible(winner)
                teleportBackIfPossible(loser)

                val arena1 = playerToArena.remove(winner.uuid)
                val arena2 = playerToArena.remove(loser.uuid)
                val arena = arena1 ?: arena2
                if (arena != null) {
                    releaseArena(arena)
                }

                setPlayerInRankedBattle(winner.uuid, false)
                setPlayerInRankedBattle(loser.uuid, false)

                clearPlayerUsedPokemon(winner.uuid)
                clearPlayerUsedPokemon(loser.uuid)
            }
        } finally {
            finalBattleCleanup(battle, battleId)
        }
    }

    fun markPokemonAsUsed(playerUuid: UUID, pokemonUuid: UUID) {
        usedPokemonUuids.computeIfAbsent(playerUuid) { mutableSetOf() }.add(pokemonUuid)
        logger.debug("Marked Pokemon $pokemonUuid as used for player $playerUuid")
    }

    fun isPokemonUsed(playerUuid: UUID, pokemonUuid: UUID): Boolean {
        return usedPokemonUuids[playerUuid]?.contains(pokemonUuid) == true
    }

    fun getUsedPokemonCount(playerUuid: UUID): Int {
        return usedPokemonUuids[playerUuid]?.size ?: 0
    }

    fun getUsedPokemonSet(playerUuid: UUID): Set<UUID> {
        return usedPokemonUuids[playerUuid]?.toSet() ?: emptySet()
    }

    fun clearPlayerUsedPokemon(playerUuid: UUID) {
        usedPokemonUuids.remove(playerUuid)
        logger.debug("Cleared used Pokemon for player $playerUuid")
    }

    fun clearAllUsedPokemon() {
        usedPokemonUuids.clear()
    }

    fun cleanupStaleUsedPokemonMarkers(server: MinecraftServer) {
        val toRemove = mutableListOf<UUID>()
        for (playerId in usedPokemonUuids.keys) {
            val player = server.playerManager.getPlayer(playerId)
            if (player == null || !isPlayerInRankedBattle(player)) {
                toRemove.add(playerId)
            }
        }
        if (toRemove.isNotEmpty()) {
            toRemove.forEach { usedPokemonUuids.remove(it) }
            logger.debug("Cleaned up ${toRemove.size} stale used Pokemon markers")
        }
    }

    fun grantVictoryRewards(winner: ServerPlayerEntity, server: MinecraftServer) {
        val rewards = CobblemonRanked.config.victoryRewards
        val lang = CobblemonRanked.config.defaultLang
        if (rewards.isNotEmpty()) {
            RankUtils.sendMessage(winner, MessageConfig.get("battle.VictoryRewards", lang))
            rewards.forEach { command -> executeRewardCommand(command, winner, server) }
        }
    }

    fun teleportBackIfPossible(player: PlayerEntity) {
        if (player !is ServerPlayerEntity) return
        val lang = CobblemonRanked.config.defaultLang
        var data = returnLocations.remove(player.uuid)
        if (data == null) {
            val dbLocation = rankDao.getReturnLocation(player.uuid)
            if (dbLocation != null) {
                val (worldId, coordinates) = dbLocation
                val worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.tryParse(worldId))
                val world = player.server.getWorld(worldKey)
                if (world != null) data = Pair(world, Triple(coordinates.first, coordinates.second, coordinates.third))
            }
        }
        if (data != null) {
            player.teleport(data.first, data.second.first, data.second.second, data.second.third, 0f, 0f)
            RankUtils.sendMessage(player, MessageConfig.get("battle.teleport.back", lang))
            rankDao.deleteReturnLocation(player.uuid)
        }
    }

    private fun extractPlayerActors(actors: List<BattleActor>): List<PlayerBattleActor> = actors.filterIsInstance<PlayerBattleActor>()

    private fun getOrCreatePlayerData(playerId: UUID, seasonId: Int, format: String): PlayerRankData {
        return rankDao.getPlayerData(playerId, seasonId, format) ?: PlayerRankData(playerId = playerId, seasonId = seasonId, format = format).apply { elo = config.initialElo }
    }

    fun sendBattleResultMessage(player: PlayerEntity, data: PlayerRankData, eloChange: Int) {
        val lang = CobblemonRanked.config.defaultLang
        val changeText = if (eloChange > 0) "§a+$eloChange" else "§c$eloChange"
        val rankTitle = data.getRankTitle()
        player.sendMessage(Text.literal(MessageConfig.get("battle.result.header", lang)))
        player.sendMessage(Text.literal(MessageConfig.get("battle.result.rank", lang, "rank" to rankTitle)))
        player.sendMessage(Text.literal(MessageConfig.get("battle.result.change", lang, "change" to changeText)))
        player.sendMessage(Text.literal(MessageConfig.get("battle.result.elo", lang, "elo" to data.elo.toString())))
        player.sendMessage(Text.literal(MessageConfig.get("battle.result.record", lang, "wins" to data.wins.toString(), "losses" to data.losses.toString())))
    }

    fun grantRankReward(player: PlayerEntity, rank: String, format: String, server: MinecraftServer) {
        val lang = CobblemonRanked.config.defaultLang
        val uuid = player.uuid
        val seasonId = seasonManager.currentSeasonId
        val playerData = rankDao.getPlayerData(uuid, seasonId) ?: return
        val rewards = RankUtils.getRewardCommands(format, rank)
        if (!rewards.isNullOrEmpty()) {
            rewards.forEach { command -> executeRewardCommand(command, player, server) }
            if (!playerData.hasClaimedReward(rank, format)) {
                playerData.markRewardClaimed(rank, format)
                rankDao.savePlayerData(playerData)
            }
            player.sendMessage(Text.literal(MessageConfig.get("reward.granted", lang, "rank" to rank)).formatted(Formatting.GREEN))
        } else {
            player.sendMessage(Text.literal(MessageConfig.get("reward.not_configured", lang)).formatted(Formatting.RED))
        }
    }

    private fun executeRewardCommand(command: String, player: PlayerEntity, server: MinecraftServer) {
        server.commandManager.executeWithPrefix(server.commandSource, command.replace("{player}", player.name.string).replace("{uuid}", player.uuid.toString()))
    }

    private fun recordPokemonUsage(players: List<ServerPlayerEntity>, seasonId: Int) {
        val dao = CobblemonRanked.rankDao
        players.forEach { player ->
            Cobblemon.storage.getParty(player).forEach { pokemon ->
                pokemon?.species?.name?.toString()?.let { speciesName -> dao.incrementPokemonUsage(seasonId, speciesName) }
            }
        }
    }

    fun forceCleanupPlayerBattleData(player: ServerPlayerEntity) {
        returnLocations.remove(player.uuid)
        playerToArena.remove(player.uuid)
        setPlayerInRankedBattle(player.uuid, false)
        clearPlayerUsedPokemon(player.uuid)
    }
}