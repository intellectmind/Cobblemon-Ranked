package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.*
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion
import cn.kurt6.cobblemon_ranked.config.ArenaCoordinate
import cn.kurt6.cobblemon_ranked.config.BattleArena
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.data.PlayerRankData
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.registry.Registries
import net.minecraft.item.ItemStack
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.slf4j.LoggerFactory
import java.util.*

object BattleHandler {
    private val logger = LoggerFactory.getLogger(BattleHandler::class.java)
    private val rankedBattles = mutableMapOf<UUID, String>()
    private val battleToIdMap = mutableMapOf<PokemonBattle, UUID>()
    private val config get() = CobblemonRanked.config
    val rankDao get() = CobblemonRanked.rankDao
    val rewardManager get() = CobblemonRanked.rewardManager
    private val seasonManager get() = CobblemonRanked.seasonManager

    private val returnLocations = mutableMapOf<UUID, Pair<ServerWorld, Triple<Double, Double, Double>>>()
    private data class OriginalLevelData(val originalLevel: Int, val originalExp: Int)
    private val pendingLevelAdjustments = mutableMapOf<UUID, MutableMap<UUID, OriginalLevelData>>()

    fun getRandomArenaForPlayers(count: Int): Pair<BattleArena, List<ArenaCoordinate>>? {
        val arenas = CobblemonRanked.config.battleArenas
        val suitable = arenas.filter { it.playerPositions.size >= count }
        if (suitable.isEmpty()) return null

        val selected = suitable.random()
        return Pair(selected, selected.playerPositions.take(count))
    }

    fun setReturnLocation(uuid: UUID, world: ServerWorld, location: Triple<Double, Double, Double>) {
        returnLocations[uuid] = Pair(world, location)
    }

    fun validateTeam(player: ServerPlayerEntity, teamUuids: List<UUID>, format: BattleFormat): Boolean {
        val lang = CobblemonRanked.config.defaultLang
        val partyUuids = Cobblemon.storage.getParty(player).mapNotNull { it?.uuid }.toSet()

        if (!teamUuids.all { it in partyUuids }) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.invalid_team_selection", lang))
            return false
        }

        val pokemonList = teamUuids.mapNotNull { uuid -> getPokemonFromPlayer(player, uuid) }
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

        val bannedPokemon = config.bannedPokemon.map { it.lowercase() }
        val bannedInTeam = pokemonList.filter { bannedPokemon.contains(it.species.name.lowercase()) }
        if (bannedInTeam.isNotEmpty()) {
            val bannedNames = bannedInTeam.joinToString(", ") { it.species.name }
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_pokemon", lang, "names" to bannedNames))
            return false
        }

        if (config.maxLevel > 0) {
            val overleveled = pokemonList.filter { it.level > config.maxLevel }
            if (overleveled.isNotEmpty()) {
                val names = overleveled.joinToString(", ") { "${it.species.name} (Lv.${it.level})" }
                RankUtils.sendMessage(player, MessageConfig.get("battle.team.overleveled", lang, "max" to config.maxLevel.toString(), "names" to names))
                return false
            }
        }

        if (!config.allowDuplicateSpecies) {
            val speciesCount = mutableMapOf<String, Int>()
            pokemonList.forEach {
                val name = it.species.name
                speciesCount[name] = speciesCount.getOrDefault(name, 0) + 1
            }
            val duplicates = speciesCount.filter { it.value > 1 }.keys
            if (duplicates.isNotEmpty()) {
                RankUtils.sendMessage(player, MessageConfig.get("battle.team.duplicates", lang, "names" to duplicates.joinToString()))
                return false
            }
        }

        val invalidPokemon = pokemonList.filter { isEgg(it) || isFainted(it) }
        if (invalidPokemon.isNotEmpty()) {
            val invalidEntries = invalidPokemon.map {
                val status = when {
                    isEgg(it) -> MessageConfig.get("battle.status.egg", lang)
                    isFainted(it) -> MessageConfig.get("battle.status.fainted", lang)
                    else -> MessageConfig.get("battle.status.unknown", lang)
                }
                "${it.species.name}($status)"
            }
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.invalid", lang, "entries" to invalidEntries.joinToString()))
            return false
        }

        val bannedHeldItems = config.bannedHeldItems.map { it.lowercase() }
        val heldItemViolations = pokemonList.filter { poke ->
            val stack = poke.heldItem()
            if (stack.isEmpty) return@filter false
            val id = Registries.ITEM.getId(stack.item).toString().lowercase()
            id in bannedHeldItems
        }
        if (heldItemViolations.isNotEmpty()) {
            val names = heldItemViolations.joinToString(", ") {
                val item = it.heldItem().item
                val itemId = Registries.ITEM.getId(item).path
                "${it.species.name}($itemId)"
            }
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_held_items", lang, "names" to names))
            return false
        }

        val bannedItems = config.bannedCarriedItems.map { it.lowercase() }
        val inventory = player.inventory
        val violatedItems = inventory.main
            .filterNot { it.isEmpty }
            .map { Registries.ITEM.getId(it.item).toString().lowercase() }
            .filter { it in bannedItems }

        if (violatedItems.isNotEmpty()) {
            val itemList = violatedItems.joinToString(", ")
            RankUtils.sendMessage(player, MessageConfig.get("battle.player.banned_items", lang, "items" to itemList))
            return false
        }

        val bannedNatures = config.bannedNatures.map { it.lowercase() }
        val hasBannedNature = pokemonList.filter {
            it.nature.name.toString().lowercase() in bannedNatures
        }
        if (hasBannedNature.isNotEmpty()) {
            val names = hasBannedNature.joinToString(", ") {
                "${it.species.name}(${it.nature.name})"
            }
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_nature", lang, "names" to names))
            return false
        }

        val bannedAbilities = config.bannedAbilities.map { it.uppercase() }
        val hasBannedAbility = pokemonList.filter {
            it.ability.name.uppercase() in bannedAbilities
        }
        if (hasBannedAbility.isNotEmpty()) {
            val names = hasBannedAbility.joinToString(", ") {
                "${it.species.name}(${it.ability.name})"
            }
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_ability", lang, "names" to names))
            return false
        }

        val bannedGenders = config.bannedGenders.map { it.uppercase() }
        val hasBannedGender = pokemonList.filter {
            it.gender?.name?.uppercase() in bannedGenders
        }
        if (hasBannedGender.isNotEmpty()) {
            val names = hasBannedGender.joinToString(", ") {
                "${it.species.name}(${it.gender?.name})"
            }
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_gender", lang, "names" to names))
            return false
        }

        val bannedMoves = config.bannedMoves.map { it.lowercase().trim() }
        val hasBannedMoves = pokemonList.mapNotNull { poke ->
            val banned = poke.moveSet.getMovesWithNulls()
                .mapNotNull { move ->
                    val moveName = move?.name?.toString()?.lowercase()
                    if (moveName in bannedMoves) moveName else null
                }

            if (banned.isNotEmpty()) {
                "${poke.species.name}(${banned.joinToString(", ")})"
            } else null
        }
        if (hasBannedMoves.isNotEmpty()) {
            val names = hasBannedMoves.joinToString(", ")
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_moves", lang, "names" to names))
            return false
        }

        if (config.bannedShiny) {
            val shinyPokemon = pokemonList.filter { it.shiny }
            if (shinyPokemon.isNotEmpty()) {
                val names = shinyPokemon.joinToString(", ") { it.species.name }
                RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_shiny", lang, "names" to names))
                return false
            }
        }

        return true
    }

    private fun isEgg(pokemon: Pokemon): Boolean = pokemon.state.name == "egg"
    private fun isFainted(pokemon: Pokemon): Boolean = pokemon.isFainted()

    private fun getPokemonFromPlayer(player: ServerPlayerEntity, uuid: UUID): Pokemon? {
        val party = Cobblemon.storage.getParty(player)
        return party.find { it?.uuid == uuid }?.takeIf { pokemon ->
            party.any { it?.uuid == uuid }
        }
    }

    fun register() {
        CobblemonEvents.BATTLE_VICTORY.subscribe { event: BattleVictoryEvent ->
            val battle = event.battle
            val battleId = battleToIdMap[battle] ?: return@subscribe
            val format = rankedBattles[battleId] ?: return@subscribe

            if (format == "2v2singles") {
                val winners = event.winners.filterIsInstance<PlayerBattleActor>()
                val losers = event.losers.filterIsInstance<PlayerBattleActor>()

                if (winners.size == 1 && losers.size == 1) {
                    val winnerId = winners.first().uuid
                    val loserId = losers.first().uuid
                    DuoBattleManager.handleVictory(winnerId, loserId)
                }

                return@subscribe
            }

            onBattleVictory(event)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player

            try {
                val battleRegistry = Cobblemon.battleRegistry
                val battle = battleRegistry.getBattleByParticipatingPlayer(player)
                val battleId = battle?.let { battleToIdMap[it] }

                if (battle != null && battleId != null && rankedBattles.containsKey(battleId)) {
                    handleDisconnectAsFlee(battle, player)
                } else {
                    forceCleanupPlayerBattleData(player)
                }
            } catch (e: Exception) {
                try {
                    forceCleanupPlayerBattleData(player)
                } catch (cleanupError: Exception) {
                    logger.error("Failed to cleanup player data", cleanupError)
                }
            }
        }
    }

    private fun handleDisconnectAsFlee(battle: PokemonBattle, disconnected: ServerPlayerEntity) {
        val lang = CobblemonRanked.config.defaultLang
        val battleId = battleToIdMap.remove(battle) ?: return
        val formatName = rankedBattles.remove(battleId) ?: return
        val seasonId = seasonManager.currentSeasonId

        val allPlayers = battle.sides
            .flatMap { it.actors.toList() }
            .mapNotNull { (it as? PlayerBattleActor)?.entity as? ServerPlayerEntity }

        if (formatName == "2v2singles" && allPlayers.size == 4) {
            val disconnectMsg = MessageConfig.get("battle.disconnect.broadcast", lang, "player" to disconnected.name.string)
            allPlayers.forEach {
                RankUtils.sendMessage(it, disconnectMsg)
            }

            val loser = allPlayers.firstOrNull { it.getUuid() == disconnected.getUuid() } ?: return

            val sidesList = battle.sides.toList()
            val side1 = sidesList[0].actors.mapNotNull { (it as? PlayerBattleActor)?.entity as? ServerPlayerEntity }
            val side2 = sidesList[1].actors.mapNotNull { (it as? PlayerBattleActor)?.entity as? ServerPlayerEntity }

            val loserTeam = if (side1.any { it.getUuid() == loser.getUuid() }) side1 else side2
            val winnerTeam = if (loserTeam == side1) side2 else side1

            val loserData = loserTeam.map {
                getOrCreatePlayerData(it.getUuid(), seasonId, formatName).apply {
                    playerName = it.name.string
                }
            }
            val winnerData = winnerTeam.map {
                getOrCreatePlayerData(it.getUuid(), seasonId, formatName).apply {
                    playerName = it.name.string
                }
            }

            val loserAvgElo = loserData.map { it.elo }.average().toInt()
            val winnerAvgElo = winnerData.map { it.elo }.average().toInt()

            val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(
                winnerAvgElo, loserAvgElo,
                CobblemonRanked.config.eloKFactor,
                CobblemonRanked.config.minElo
            )

            loserTeam.forEachIndexed { i, player ->
                val data = loserData[i]
                data.apply {
                    elo = newLoserElo
                    losses++
                    winStreak = 0
                    fleeCount++
                }
                rankDao.savePlayerData(data)
                RankUtils.sendMessage(player, MessageConfig.get("battle.disconnect.loser", lang))

                restoreLevelAdjustments(player)
                teleportBackIfPossible(player)
            }

            winnerTeam.forEachIndexed { i, player ->
                val data = winnerData[i]
                data.apply {
                    elo = newWinnerElo
                    wins++
                    winStreak++
                    if (winStreak > bestWinStreak) bestWinStreak = winStreak
                }
                rankDao.savePlayerData(data)
                RankUtils.sendMessage(player, MessageConfig.get("battle.disconnect.winner", lang))

                restoreLevelAdjustments(player)
                teleportBackIfPossible(player)
            }

            Cobblemon.battleRegistry.closeBattle(battle)

            DuoBattleManager.cleanupTeamData(
                DuoBattleManager.DuoTeam(loserTeam[0], loserTeam[1], emptyList(), emptyList()),
                DuoBattleManager.DuoTeam(winnerTeam[0], winnerTeam[1], emptyList(), emptyList())
            )

            recordPokemonUsage(allPlayers, seasonId)
            return
        }

        val fleeingActor = battle.sides
            .flatMap { it.actors.toList() }
            .firstOrNull {
                (it as? PlayerBattleActor)?.entity?.getUuid() == disconnected.getUuid()
            } as? PlayerBattleActor ?: return

        val loser: ServerPlayerEntity = fleeingActor.entity as? ServerPlayerEntity ?: return

        val winner: ServerPlayerEntity = battle.sides
            .flatMap { it.actors.toList() }
            .mapNotNull { (it as? PlayerBattleActor)?.entity as? ServerPlayerEntity }
            .firstOrNull { it.getUuid() != loser.getUuid() } ?: return

        val loserData = getOrCreatePlayerData(loser.getUuid(), seasonId, formatName)
        val winnerData = getOrCreatePlayerData(winner.getUuid(), seasonId, formatName)

        loserData.playerName = loser.name.string
        winnerData.playerName = winner.name.string

        val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(
            winnerData.elo,
            loserData.elo,
            CobblemonRanked.config.eloKFactor,
            CobblemonRanked.config.minElo
        )

        val eloDiffWinner = newWinnerElo - winnerData.elo
        val eloDiffLoser = newLoserElo - loserData.elo

        loserData.apply {
            elo = newLoserElo
            losses++
            winStreak = 0
            fleeCount++
        }
        winnerData.apply {
            elo = newWinnerElo
            wins++
            winStreak++
            if (winStreak > bestWinStreak) bestWinStreak = winStreak
        }

        rankDao.savePlayerData(loserData)
        rankDao.savePlayerData(winnerData)

        Cobblemon.battleRegistry.closeBattle(battle)

        restoreLevelAdjustments(winner)
        restoreLevelAdjustments(loser)

        recordPokemonUsage(allPlayers, seasonId)

        RankUtils.sendMessage(loser, MessageConfig.get("battle.disconnect.loser", lang, "elo" to loserData.elo.toString()))
        RankUtils.sendMessage(winner, MessageConfig.get("battle.disconnect.winner", lang, "elo" to winnerData.elo.toString()))

        sendBattleResultMessage(winner, winnerData, eloDiffWinner)
        sendBattleResultMessage(loser, loserData, eloDiffLoser)

        teleportBackIfPossible(loser)
        teleportBackIfPossible(winner)
    }

    fun markAsRanked(battleId: UUID, formatName: String) {
        rankedBattles[battleId] = formatName
    }

    fun registerBattle(battle: PokemonBattle, battleId: UUID) {
        battleToIdMap[battle] = battleId
    }

    fun onBattleVictory(event: BattleVictoryEvent) {
        val battle = event.battle
        val battleId = battleToIdMap.remove(battle) ?: return
        val formatName = rankedBattles.remove(battleId) ?: return

        val allPlayers = battle.sides
            .flatMap { it.actors.toList() }
            .mapNotNull { (it as? PlayerBattleActor)?.entity as? ServerPlayerEntity }

        allPlayers.forEach { player ->
            restoreLevelAdjustments(player)
        }

        val playerWinners = extractPlayerActors(event.winners)
        val playerLosers = extractPlayerActors(event.losers)

        if (playerWinners.size != 1 || playerLosers.size != 1) return

        val winner = playerWinners.first().entity ?: return
        val loser = playerLosers.first().entity ?: return

        if (winner.isDisconnected || loser.isDisconnected) return

        val server = winner.server
        val seasonId = seasonManager.currentSeasonId

        val winnerData = getOrCreatePlayerData(winner.uuid, seasonId, formatName)
        val loserData = getOrCreatePlayerData(loser.uuid, seasonId, formatName)

        winnerData.playerName = winner.name.string
        loserData.playerName = loser.name.string

        val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(
            winnerData.elo,
            loserData.elo,
            CobblemonRanked.config.eloKFactor,
            CobblemonRanked.config.minElo
        )

        val eloDiffWinner = newWinnerElo - winnerData.elo
        val eloDiffLoser = newLoserElo - loserData.elo

        winnerData.apply {
            elo = newWinnerElo
            wins++
            winStreak++
            if (winStreak > bestWinStreak) bestWinStreak = winStreak
        }

        loserData.apply {
            elo = newLoserElo
            losses++
            winStreak = 0
        }

        rankDao.savePlayerData(winnerData)
        rankDao.savePlayerData(loserData)

        grantVictoryRewards(winner, server)

        recordPokemonUsage(listOf(winner, loser), seasonId)

        sendBattleResultMessage(winner, winnerData, eloDiffWinner)
        sendBattleResultMessage(loser, loserData, eloDiffLoser)

        rewardManager.grantRankRewardIfEligible(winner, winnerData.getRankTitle(), formatName, server)
        rewardManager.grantRankRewardIfEligible(loser, loserData.getRankTitle(), formatName, server)

        teleportBackIfPossible(winner)
        teleportBackIfPossible(loser)
    }

    fun grantVictoryRewards(winner: ServerPlayerEntity, server: MinecraftServer) {
        val rewards = CobblemonRanked.config.victoryRewards
        val lang = CobblemonRanked.config.defaultLang
        if (rewards.isNotEmpty()) {
            RankUtils.sendMessage(winner, MessageConfig.get("battle.VictoryRewards", lang))
            rewards.forEach { command ->
                executeRewardCommand(command, winner, server)
            }
        }
    }

    fun teleportBackIfPossible(player: PlayerEntity) {
        if (player !is ServerPlayerEntity) return

        val lang = CobblemonRanked.config.defaultLang
        val data = returnLocations.remove(player.uuid)
        if (data != null) {
            val (originalWorld, loc) = data
            player.teleport(originalWorld, loc.first, loc.second, loc.third, 0f, 0f)
            RankUtils.sendMessage(player, MessageConfig.get("battle.teleport.back", lang))
        }
    }

    private fun extractPlayerActors(actors: List<BattleActor>): List<PlayerBattleActor> {
        return actors.filterIsInstance<PlayerBattleActor>()
    }

    private fun getOrCreatePlayerData(
        playerId: UUID,
        seasonId: Int,
        format: String
    ): PlayerRankData {
        return rankDao.getPlayerData(playerId, seasonId, format) ?: PlayerRankData(
            playerId = playerId,
            seasonId = seasonId,
            format = format
        ).apply {
            elo = config.initialElo
        }
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
        val formattedCommand = command
            .replace("{player}", player.name.string)
            .replace("{uuid}", player.uuid.toString())
        server.commandManager.executeWithPrefix(server.commandSource, formattedCommand)
    }

    private fun recordPokemonUsage(players: List<ServerPlayerEntity>, seasonId: Int) {
        val dao = CobblemonRanked.rankDao
        players.forEach { player ->
            Cobblemon.storage.getParty(player).forEach { pokemon ->
                pokemon?.species?.name?.toString()?.let { speciesName ->
                    dao.incrementPokemonUsage(seasonId, speciesName)
                }
            }
        }
    }

    /**
     * 记录当前战斗队伍的等级快照。
     */
    fun prepareBattleSnapshot(player: ServerPlayerEntity, teamUuids: List<UUID>) {
        if (!CobblemonRanked.config.enableCustomLevel) return

        // 获取（或创建）该玩家的挂起调整记录
        val playerAdjustments = pendingLevelAdjustments.getOrPut(player.uuid) { mutableMapOf() }

        // 清空旧数据
        playerAdjustments.clear()
        CobblemonRanked.rankDao.deletePokemonOriginalData(player.uuid)

        // 遍历队伍并记录快照
        teamUuids.mapNotNull { getPokemonFromPlayer(player, it) }.forEach { pokemon ->
            prepareLevelAdjustment(player, pokemon)
        }
    }

    private fun prepareLevelAdjustment(player: ServerPlayerEntity, pokemon: Pokemon) {
        if (!CobblemonRanked.config.enableCustomLevel) return

        val playerAdjustments = pendingLevelAdjustments.getOrPut(player.uuid) { mutableMapOf() }
        playerAdjustments[pokemon.uuid] = OriginalLevelData(pokemon.level, pokemon.experience)

        CobblemonRanked.rankDao.savePokemonOriginalData(
            player.uuid,
            pokemon.uuid,
            pokemon.level,
            pokemon.experience
        )
    }

    fun applyLevelAdjustments(player: ServerPlayerEntity) {
        val config = CobblemonRanked.config
        if (!config.enableCustomLevel) return

        val playerAdjustments = pendingLevelAdjustments[player.uuid] ?: return

        Cobblemon.storage.getParty(player).forEach { pokemon ->
            pokemon?.let {
                if (playerAdjustments.containsKey(it.uuid)) {
                    val targetLevel = config.customBattleLevel
                    it.level = targetLevel
                    it.currentHealth = it.maxHealth
                    it.onChange()
                }
            }
        }
    }

    fun restoreLevelAdjustments(player: ServerPlayerEntity) {
        val playerAdjustments = pendingLevelAdjustments.remove(player.uuid) ?: return
        val lang = CobblemonRanked.config.defaultLang

        Cobblemon.storage.getParty(player).forEach { pokemon ->
            pokemon?.let {
                playerAdjustments[it.uuid]?.let { original ->
                    it.setExperienceAndUpdateLevel(original.originalExp)

                    // 确保等级完全一致
                    if (it.level != original.originalLevel) {
                        it.level = original.originalLevel
                    }

                    it.currentHealth = it.maxHealth
                    it.onChange()
                }
            }
        }

        CobblemonRanked.rankDao.deletePokemonOriginalData(player.uuid)
        RankUtils.sendMessage(player, MessageConfig.get("customBattleLevel.restore", lang))
    }

    fun restoreLevelsFromDatabase(player: ServerPlayerEntity) {
        val originalData = CobblemonRanked.rankDao.getPokemonOriginalData(player.uuid)

        Cobblemon.storage.getParty(player).forEach { pokemon ->
            pokemon?.let {
                originalData[it.uuid]?.let { (level, exp) ->
                    it.setExperienceAndUpdateLevel(exp)
                    if (it.level != level) {
                        it.level = level
                    }
                    it.currentHealth = it.maxHealth
                    it.onChange()
                }
            }
        }

        CobblemonRanked.rankDao.deletePokemonOriginalData(player.uuid)
        pendingLevelAdjustments.remove(player.uuid)
    }

    fun forceCleanupPlayerBattleData(player: ServerPlayerEntity) {
        returnLocations.remove(player.uuid)
        pendingLevelAdjustments.remove(player.uuid)

        try {
            restoreLevelAdjustments(player)
        } catch (e: Exception) {
            logger.warn("Failed to restore level adjustments during cleanup", e)
        }
    }
}