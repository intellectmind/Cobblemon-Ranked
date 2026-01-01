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
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.registry.Registries
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.ItemEntity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import net.minecraft.util.math.Box
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object BattleHandler {
    private val logger = LoggerFactory.getLogger(BattleHandler::class.java)

    private val rankedBattles = ConcurrentHashMap<UUID, String>()
    private val battleToIdMap = ConcurrentHashMap<PokemonBattle, UUID>()
    private val teleportScheduler = Executors.newSingleThreadScheduledExecutor()

    private val battleEntities = ConcurrentHashMap<UUID, MutableSet<PokemonEntity>>()
    private val cleanupCooldowns = ConcurrentHashMap<UUID, Long>()

    private val occupiedArenas = ConcurrentHashMap.newKeySet<BattleArena>()
    private val battleIdToArena = ConcurrentHashMap<UUID, BattleArena>()
    private val playerToArena = ConcurrentHashMap<UUID, BattleArena>()

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

    private val returnLocations = ConcurrentHashMap<UUID, Pair<ServerWorld, Triple<Double, Double, Double>>>()
    private var tickCounter = 0

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
            occupiedArenas.remove(arena)
        }
        if (shouldProcess) {
            processPendingRequests()
        }
    }

    fun releaseArenaForPlayer(uuid: UUID) {
        val arena = playerToArena.remove(uuid)
        if (arena != null) {
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

    fun markPlayerForCleanup(player: ServerPlayerEntity) {
        cleanupCooldowns[player.uuid] = System.currentTimeMillis() + 5000L
        cleanupBattleEntities(player)
    }

    fun sweepArena(arena: BattleArena, server: MinecraftServer, aggressive: Boolean) {
        val worldId = Identifier.tryParse(arena.world) ?: return
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId)
        val world = server.getWorld(worldKey) ?: return

        val centerPos = arena.playerPositions.firstOrNull() ?: return
        val radius = 50.0

        val box = Box(
            centerPos.x - radius, centerPos.y - radius, centerPos.z - radius,
            centerPos.x + radius, centerPos.y + radius, centerPos.z + radius
        )

        val entities = world.getEntitiesByClass(net.minecraft.entity.Entity::class.java, box) { entity ->
            if (entity is ItemEntity) {
                return@getEntitiesByClass checkAndBlockBattleItem(entity, world) != null
            }

            if (entity is PokemonEntity) {
                val owner = entity.owner

                if (aggressive) {
                    return@getEntitiesByClass true
                } else {
                    if (owner == null) return@getEntitiesByClass true
                }
            }
            false
        }

        entities.forEach {
            if (!it.isRemoved) it.discard()
        }
    }

    fun cleanupBattleEntities(player: ServerPlayerEntity) {
        val entities = battleEntities.remove(player.uuid)
        entities?.forEach { entity ->
            try { if (!entity.isRemoved) entity.discard() } catch (e: Exception) { logger.error("Error discarding entity", e) }
        }

        val arena = playerToArena[player.uuid]
        if (arena != null) {
            sweepArena(arena, player.server, true)
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
        val heldItems = mutableListOf<String>()
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId

        pokemonList.forEach { pokemon ->
            val speciesName = pokemon.species.name.lowercase()

            if (config.bannedPokemon.map { it.lowercase() }.contains(speciesName)) {
                violations.add("banned_pokemon:${pokemon.species.name}")
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

    fun shutdown() {
        teleportScheduler.shutdownNow()
        rankedBattles.clear()
        battleToIdMap.clear()
        returnLocations.clear()
        battleEntities.values.flatten().forEach { if (!it.isRemoved) it.discard() }
        battleEntities.clear()
        cleanupCooldowns.clear()
        occupiedArenas.clear()
        pendingRequests.clear()
        logger.info("BattleHandler shutdown complete")
    }

    fun isPlayerInRankedBattle(player: ServerPlayerEntity): Boolean {
        val battle = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) ?: return false
        return battleToIdMap.containsKey(battle)
    }

    fun register() {
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (entity is PokemonEntity) {
                val owner = entity.owner
                if (owner != null && cleanupCooldowns.containsKey(owner.uuid)) {
                    val expiry = cleanupCooldowns[owner.uuid] ?: 0L
                    if (System.currentTimeMillis() < expiry) {
                        entity.discard()
                        return@register
                    } else {
                        cleanupCooldowns.remove(owner.uuid)
                    }
                }
                if (owner is ServerPlayerEntity && isPlayerInRankedBattle(owner)) {
                    battleEntities.computeIfAbsent(owner.uuid) { ConcurrentHashMap.newKeySet() }.add(entity)
                }
            }
            if (entity is ItemEntity) {
                if (battleToIdMap.isNotEmpty()) {
                    val blockResult = checkAndBlockBattleItem(entity, world)
                    if (blockResult != null) entity.discard()
                }
            }
        }

        ServerLivingEntityEvents.ALLOW_DEATH.register { entity, damageSource, amount ->
            if (entity is PokemonEntity) {
                val owner = entity.owner
                if (owner is ServerPlayerEntity && isPlayerInRankedBattle(owner)) {
                    entity.equipStack(EquipmentSlot.MAINHAND, net.minecraft.item.ItemStack.EMPTY)
                    entity.equipStack(EquipmentSlot.OFFHAND, net.minecraft.item.ItemStack.EMPTY)
                    entity.equipStack(EquipmentSlot.HEAD, net.minecraft.item.ItemStack.EMPTY)
                    entity.equipStack(EquipmentSlot.CHEST, net.minecraft.item.ItemStack.EMPTY)
                    entity.equipStack(EquipmentSlot.LEGS, net.minecraft.item.ItemStack.EMPTY)
                    entity.equipStack(EquipmentSlot.FEET, net.minecraft.item.ItemStack.EMPTY)
                }
            }
            true
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (++tickCounter % 20 != 0) return@register
            val now = System.currentTimeMillis()
            cleanupCooldowns.entries.removeIf { it.value < now }
            if (battleToIdMap.isEmpty()) return@register
            val activeWorlds = battleToIdMap.keys.flatMap { battle -> battle.actors.filterIsInstance<PlayerBattleActor>().mapNotNull { (it.entity as? ServerPlayerEntity)?.serverWorld } }.distinct()
            activeWorlds.forEach { world ->
                world.iterateEntities().forEach { entity ->
                    if (entity is ItemEntity && !entity.isRemoved) {
                        if (checkAndBlockBattleItem(entity, world) != null) entity.discard()
                    }
                }
            }
        }

        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register { world, player, pos, state, blockEntity ->
            if (player is ServerPlayerEntity && isPlayerInRankedBattle(player)) false else true
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
                } catch (e: Exception) {
                    logger.error("Error handling player disconnect", e)
                    forceCleanupPlayerBattleData(player)
                }
            }
        }
    }

    private fun checkAndBlockBattleItem(entity: ItemEntity, world: ServerWorld): BlockResult? {
        if (battleToIdMap.isEmpty()) return null

        val droppedItemType = entity.stack.item
        val itemName = droppedItemType.name.string

        val activeBattleIds = battleToIdMap.values.toSet()

        for (battle in battleToIdMap.keys) {
            val battleId = battleToIdMap[battle]
            if (battleId == null || battleId !in activeBattleIds) continue

            try {
                val nearbyPlayer = battle.actors
                    .filterIsInstance<PlayerBattleActor>()
                    .mapNotNull { it.entity as? ServerPlayerEntity }
                    .firstOrNull { player ->
                        !player.isDisconnected &&
                                player.serverWorld == world &&
                                player.squaredDistanceTo(entity) < 2500.0
                    }

                if (nearbyPlayer != null) {
                    val isBattleItem = battle.actors
                        .filterIsInstance<PlayerBattleActor>()
                        .any { actor ->
                            actor.pokemonList.any { battleMon ->
                                battleMon.originalPokemon?.let { pokemon ->
                                    !pokemon.heldItem().isEmpty &&
                                            pokemon.heldItem().item == droppedItemType
                                } ?: false
                            }
                        }

                    if (isBattleItem) {
                        return BlockResult(itemName, nearbyPlayer.name.string)
                    }
                }
            } catch (e: Exception) {
                CobblemonRanked.logger.warn("Error checking battle item for battle ${battleToIdMap[battle]}: ${e.message}")
                continue
            }
        }
        return null
    }
    private data class BlockResult(val itemName: String, val playerName: String)

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

        markPlayerForCleanup(winner)
        markPlayerForCleanup(loser)
        if (!winner.isDisconnected) {
            RankUtils.sendMessage(winner, MessageConfig.get("battle.disconnect.winner", config.defaultLang, "elo" to winnerData.elo.toString()))
            sendBattleResultMessage(winner, winnerData, eloDiffWinner)
            grantVictoryRewards(winner, winner.server)
            teleportBackIfPossible(winner)
        }
    }

    private fun handleDisconnectAsFlee(battle: PokemonBattle, disconnected: ServerPlayerEntity) {
        val lang = CobblemonRanked.config.defaultLang
        var battleId: UUID? = null

        try {
            battleId = battleToIdMap[battle]
            val formatName = battleId?.let { rankedBattles[it] }
            if (battleId == null || formatName == null) return

            rankedBattles.remove(battleId)

            val arena = battleIdToArena.remove(battleId)
            playerToArena.remove(disconnected.uuid)

            val seasonId = seasonManager.currentSeasonId

            if (formatName == "2v2singles") {
                val fleeingActor = battle.sides.flatMap { it.actors.toList() }
                    .firstOrNull { (it as? PlayerBattleActor)?.uuid == disconnected.uuid } as? PlayerBattleActor

                if (fleeingActor != null) {
                    val loserUuid = disconnected.uuid
                    val allPlayers = battle.sides.flatMap { it.actors.toList() }.mapNotNull { (it as? PlayerBattleActor)?.entity as? ServerPlayerEntity }
                    val winner = allPlayers.firstOrNull { it.uuid != loserUuid }

                    DuoBattleManager.updateBattleState(battle)

                    if (Cobblemon.battleRegistry.getBattle(battle.battleId) != null) {
                        Cobblemon.battleRegistry.closeBattle(battle)
                    }

                    DuoBattleManager.markPokemonAsFainted(loserUuid, battle)
                    if (winner != null) {
                        DuoBattleManager.handleVictory(winner.uuid, loserUuid)
                        markPlayerForCleanup(winner)
                    }

                    markPlayerForCleanup(disconnected)
                    return
                }
            }

            val loserUuid = disconnected.uuid
            val winnerActor = battle.sides.flatMap { it.actors.toList() }
                .filterIsInstance<PlayerBattleActor>()
                .firstOrNull { it.uuid != loserUuid } ?: return

            val winnerUuid = winnerActor.uuid
            val winnerPlayer = disconnected.server.playerManager.getPlayer(winnerUuid)

            val loserData = getOrCreatePlayerData(loserUuid, seasonId, formatName)
            val winnerData = getOrCreatePlayerData(winnerUuid, seasonId, formatName)

            val oldWinnerElo = winnerData.elo
            val oldLoserElo = loserData.elo

            val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(winnerData.elo, loserData.elo, config.eloKFactor, config.minElo, config.loserProtectionRate)

            val eloDiffWinner = newWinnerElo - oldWinnerElo
            val eloDiffLoser = newLoserElo - oldLoserElo

            loserData.apply { elo = newLoserElo; losses++; winStreak = 0; fleeCount++ }
            winnerData.apply { elo = newWinnerElo; wins++; winStreak++; if (winStreak > bestWinStreak) bestWinStreak = winStreak }

            loserData.playerName = disconnected.name.string
            if (winnerPlayer != null) winnerData.playerName = winnerPlayer.name.string

            rankDao.savePlayerData(loserData)
            rankDao.savePlayerData(winnerData)

            if (Cobblemon.battleRegistry.getBattle(battle.battleId) != null) {
                Cobblemon.battleRegistry.closeBattle(battle)
            }

            val playersList = mutableListOf<ServerPlayerEntity>()
            playersList.add(disconnected)
            if (winnerPlayer != null) playersList.add(winnerPlayer)
            recordPokemonUsage(playersList, seasonId)

            if (winnerPlayer != null && !winnerPlayer.isDisconnected) {
                RankUtils.sendMessage(winnerPlayer, MessageConfig.get("battle.disconnect.winner", lang, "elo" to winnerData.elo.toString()))
                sendBattleResultMessage(winnerPlayer, winnerData, eloDiffWinner)
                teleportBackIfPossible(winnerPlayer)
                markPlayerForCleanup(winnerPlayer)
                playerToArena.remove(winnerUuid)
            }

            markPlayerForCleanup(disconnected)

            if (arena != null) {
                sweepArena(arena, disconnected.server, true)
                releaseArena(arena)
            }

        } catch (e: Exception) {
            logger.error("Error handling disconnect as flee", e)
        } finally {
            markPlayerForCleanup(disconnected)
            cleanupCooldowns.remove(disconnected.uuid)
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

                markPlayerForCleanup(winner)
                markPlayerForCleanup(loser)

                val arena = battleIdToArena[battleId]
                if (arena != null) {
                    teleportScheduler.schedule({
                        winner.server.execute {
                            sweepArena(arena, winner.server, false)
                        }
                    }, 1, TimeUnit.SECONDS)
                }

                teleportBackIfPossible(winner)
                teleportBackIfPossible(loser)

                playerToArena.remove(winner.uuid)
                playerToArena.remove(loser.uuid)
            }
        } finally {
            finalBattleCleanup(battle, battleId)
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

    fun restoreLevelsFromDatabase(player: ServerPlayerEntity) {
        teleportBackIfPossible(player)
    }

    fun forceCleanupPlayerBattleData(player: ServerPlayerEntity) {
        returnLocations.remove(player.uuid)
        playerToArena.remove(player.uuid)
        cleanupCooldowns.remove(player.uuid)
        markPlayerForCleanup(player)
    }
}