// BattleHandler.kt
package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.*
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
    /**
     * 为指定数量的玩家随机选择一个合适的战斗场地
     * @param count 玩家数量
     * @return 返回选中的战斗场地和对应数量的玩家坐标位置，如果没有合适的场地则返回null
     */
    fun getRandomArenaForPlayers(count: Int): Pair<BattleArena, List<ArenaCoordinate>>? {
        val arenas = CobblemonRanked.config.battleArenas
        val suitable = arenas.filter { it.playerPositions.size >= count }
        if (suitable.isEmpty()) return null

        val selected = suitable.random()
        return Pair(selected, selected.playerPositions.take(count))
    }

    // 存储玩家返回位置的映射表
    private val returnLocations = mutableMapOf<UUID, Pair<ServerWorld, Triple<Double, Double, Double>>>()

    /**
     * 设置玩家的返回位置
     * @param uuid 玩家UUID
     * @param world 玩家所在的世界
     * @param location 玩家的位置坐标(x, y, z)
     */
    fun setReturnLocation(uuid: UUID, world: ServerWorld, location: Triple<Double, Double, Double>) {
        returnLocations[uuid] = Pair(world, location)
    }

    /**
     * 验证玩家队伍是否合法
     * @param player 玩家实体
     * @param teamUuids 队伍中宝可梦的UUID列表
     * @param format 战斗格式
     * @return 如果队伍合法返回true，否则返回false
     */
    fun validateTeam(player: ServerPlayerEntity, teamUuids: List<UUID>, format: BattleFormat): Boolean {
        val lang = CobblemonRanked.config.defaultLang
        val partyUuids = Cobblemon.storage.getParty(player).mapNotNull { it?.uuid }.toSet()
        if (!teamUuids.all { it in partyUuids }) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.invalid_team_selection", lang))
            return false
        }

        val pokemonList = teamUuids.mapNotNull { uuid -> getPokemonFromPlayer(player, uuid) }
        val config = CobblemonRanked.config

        // doubles 模式下检查至少需要 2 只宝可梦
        if (format == BattleFormat.GEN_9_DOUBLES && pokemonList.size < 2) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.too_small", lang, "min" to "2"))
            return false
        }

        // 检查队伍大小
        if (pokemonList.size < config.minTeamSize) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.too_small", lang, "min" to config.minTeamSize.toString()))
            return false
        }
        if (pokemonList.size > config.maxTeamSize) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.too_large", lang, "max" to config.maxTeamSize.toString()))
            return false
        }

        // 检查禁用的宝可梦
        val bannedPokemon = config.bannedPokemon.map { it.lowercase() }
        val bannedInTeam = pokemonList.filter { bannedPokemon.contains(it.species.name.lowercase()) }
        if (bannedInTeam.isNotEmpty()) {
            val bannedNames = bannedInTeam.joinToString(", ") { it.species.name }
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_pokemon", lang, "names" to bannedNames))
            return false
        }

        // 检查等级限制
        if (config.maxLevel > 0) {
            val overleveled = pokemonList.filter { it.level > config.maxLevel }
            if (overleveled.isNotEmpty()) {
                val names = overleveled.joinToString(", ") { "${it.species.name} (Lv.${it.level})" }
                RankUtils.sendMessage(player, MessageConfig.get("battle.team.overleveled", lang, "max" to config.maxLevel.toString(), "names" to names))
                return false
            }
        }

        // 检查重复宝可梦
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

        // 检查无效状态(蛋或濒死)
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

        // 禁用持有物品
        val bannedHeldItems = config.bannedHeldItems.map { it.lowercase() }
        val heldItemViolations = pokemonList.filter { poke ->
            val stack = getHeldItemReflectively(poke) ?: return@filter false
            val id = Registries.ITEM.getId(stack.item).toString().lowercase()
            id in bannedHeldItems
        }
        if (heldItemViolations.isNotEmpty()) {
            val names = heldItemViolations.joinToString(", ") {
                val item = getHeldItemReflectively(it)?.item
                val itemId = if (item != null) Registries.ITEM.getId(item).path else "Unknown"
                "${it.species.name}($itemId)"
            }
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_held_items", lang, "names" to names))
            return false
        }

        // 用玩家背包道具
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

        return true
    }

    fun getHeldItemReflectively(pokemon: Any): ItemStack? {
        return try {
            val field = pokemon.javaClass.getDeclaredField("heldItem")
            field.isAccessible = true
            field.get(pokemon) as? ItemStack
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 处理战斗中止
     * @param battleId 战斗ID
     */
    fun handleBattleAbort(battleId: UUID) {
        rankedBattles.remove(battleId)
        battleToIdMap.values.remove(battleId)
    }

    // 检查宝可梦是否是蛋
    private fun isEgg(pokemon: Pokemon): Boolean = pokemon.state.name == "egg"
    // 检查宝可梦是否濒死
    private fun isFainted(pokemon: Pokemon): Boolean = pokemon.isFainted()

    /**
     * 从玩家队伍中获取指定UUID的宝可梦
     * @param player 玩家实体
     * @param uuid 宝可梦UUID
     * @return 返回找到的宝可梦，如果不存在则返回null
     */
    private fun getPokemonFromPlayer(player: ServerPlayerEntity, uuid: UUID): Pokemon? {
        val party = Cobblemon.storage.getParty(player)
        return party.find { it?.uuid == uuid }?.takeIf { pokemon ->
            // 确保该宝可梦真的是这个玩家的当前队伍成员
            party.any { it?.uuid == uuid }
        }
    }

    private val logger = LoggerFactory.getLogger(BattleHandler::class.java)
    // 存储排位战斗的映射表
    private val rankedBattles = mutableMapOf<UUID, String>()
    // 战斗对象到战斗ID的映射表
    private val battleToIdMap = mutableMapOf<PokemonBattle, UUID>()
    private val config get() = CobblemonRanked.config
    val rankDao get() = CobblemonRanked.rankDao
    val rewardManager get() = CobblemonRanked.rewardManager
    private val seasonManager get() = CobblemonRanked.seasonManager

    /**
     * 注册事件处理器
     */
    fun register() {
        // 胜利处理逻辑
        CobblemonEvents.BATTLE_VICTORY.subscribe { event: BattleVictoryEvent ->
            val battle = event.battle
            val battleId = battleToIdMap[battle] ?: return@subscribe
            val format = rankedBattles[battleId] ?: return@subscribe

            // 2v2singles模式特殊处理
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

        // 断线处理逻辑
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player
            val battle = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) ?: return@register
            val battleId = battleToIdMap[battle] ?: return@register

            if (!rankedBattles.containsKey(battleId)) return@register

            handleDisconnectAsFlee(battle, player)
        }
    }

    /**
     * 处理玩家断线,fleeCount定义为断线次数
     * @param battle 战斗对象
     * @param disconnected 断线的玩家
     */
    private fun handleDisconnectAsFlee(battle: PokemonBattle, disconnected: ServerPlayerEntity) {
        val lang = CobblemonRanked.config.defaultLang
        val battleId = battleToIdMap.remove(battle) ?: return
        val formatName = rankedBattles.remove(battleId) ?: return
        val seasonId = seasonManager.currentSeasonId

        val allPlayers = battle.sides
            .flatMap { it.actors.toList() }
            .mapNotNull { (it as? PlayerBattleActor)?.entity as? ServerPlayerEntity }

        // 处理 2v2 单打
        if (formatName == "2v2singles" && allPlayers.size == 4) {
            // 广播断线通知
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
                teleportBackIfPossible(player)
            }

            Cobblemon.battleRegistry.closeBattle(battle)

            // 清理 Duo 战斗状态
            DuoBattleManager.cleanupTeamData(
                DuoBattleManager.DuoTeam(loserTeam[0], loserTeam[1], emptyList(), emptyList()),
                DuoBattleManager.DuoTeam(winnerTeam[0], winnerTeam[1], emptyList(), emptyList())
            )
            return
        }

        // 1v1 处理逻辑
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

        RankUtils.sendMessage(loser, MessageConfig.get("battle.disconnect.loser", lang, "elo" to loserData.elo.toString()))
        RankUtils.sendMessage(winner, MessageConfig.get("battle.disconnect.winner", lang, "elo" to winnerData.elo.toString()))

        sendBattleResultMessage(winner, winnerData, eloDiffWinner)
        sendBattleResultMessage(loser, loserData, eloDiffLoser)

        teleportBackIfPossible(loser)
        teleportBackIfPossible(winner)
    }


    /**
     * 标记战斗为排位赛
     * @param battleId 战斗ID
     * @param formatName 战斗格式名称
     */
    fun markAsRanked(battleId: UUID, formatName: String) {
        rankedBattles[battleId] = formatName
    }

    /**
     * 注册战斗
     * @param battle 战斗对象
     * @param battleId 战斗ID
     */
    fun registerBattle(battle: PokemonBattle, battleId: UUID) {
        battleToIdMap[battle] = battleId
    }

    /**
     * 处理战斗胜利事件
     * @param event 胜利事件
     */
    private fun onBattleVictory(event: BattleVictoryEvent) {
        val battle = event.battle
        val battleId = battleToIdMap.remove(battle) ?: return
        val formatName = rankedBattles.remove(battleId) ?: return

        val playerWinners = extractPlayerActors(event.winners)
        val playerLosers = extractPlayerActors(event.losers)

        // 确保胜负方只有1个玩家
        if (playerWinners.size != 1 || playerLosers.size != 1) return

        val winner = playerWinners.first().entity ?: return
        val loser = playerLosers.first().entity ?: return

        if (winner.isDisconnected || loser.isDisconnected) return

        val server = winner.server
        val seasonId = seasonManager.currentSeasonId

        // 获取胜者和败者的数据
        val winnerData = getOrCreatePlayerData(winner.uuid, seasonId, formatName)
        val loserData = getOrCreatePlayerData(loser.uuid, seasonId, formatName)

        winnerData.playerName = winner.name.string
        loserData.playerName = loser.name.string

        // 计算Elo变化
        val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(
            winnerData.elo,
            loserData.elo,
            CobblemonRanked.config.eloKFactor,
            CobblemonRanked.config.minElo
        )

        val eloDiffWinner = newWinnerElo - winnerData.elo
        val eloDiffLoser = newLoserElo - loserData.elo

        // 更新胜者的数据
        winnerData.apply {
            elo = newWinnerElo
            wins++
            winStreak++
            if (winStreak > bestWinStreak) bestWinStreak = winStreak
        }

        // 更新败者的数据
        loserData.apply {
            elo = newLoserElo
            losses++
            winStreak = 0
        }

        // 保存数据
        rankDao.savePlayerData(winnerData)
        rankDao.savePlayerData(loserData)

        // 发送战斗结果消息
        sendBattleResultMessage(winner, winnerData, eloDiffWinner)
        sendBattleResultMessage(loser, loserData, eloDiffLoser)

        // 发放奖励
        rewardManager.grantRankRewardIfEligible(winner, winnerData.getRankTitle(), formatName, server)
        rewardManager.grantRankRewardIfEligible(loser, loserData.getRankTitle(), formatName, server)

        // 传送回原位
        teleportBackIfPossible(winner)
        teleportBackIfPossible(loser)
    }

    /**
     * 如果可能，将玩家传送回原位置
     * @param player 玩家实体
     */
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

    /**
     * 从战斗参与者中提取玩家战斗角色
     * @param actors 战斗参与者列表
     * @return 玩家战斗角色列表
     */
    private fun extractPlayerActors(actors: List<BattleActor>): List<PlayerBattleActor> {
        return actors.filterIsInstance<PlayerBattleActor>()
    }

    /**
     * 处理排位赛结果
     * @param winner 胜利者
     * @param loser 失败者
     * @param formatName 战斗格式名称
     * @param server 服务器实例
     */
    private fun processRankedBattleResult(
        winner: PlayerEntity,
        loser: PlayerEntity,
        formatName: String,
        server: MinecraftServer
    ) {
        val winnerId = winner.uuid
        val loserId = loser.uuid
        val seasonId = seasonManager.currentSeasonId

        val winnerData = getOrCreatePlayerData(winnerId, seasonId, formatName)
        val loserData = getOrCreatePlayerData(loserId, seasonId, formatName)

        winnerData.playerName = winner.name.string
        loserData.playerName = loser.name.string

        // 计算Elo变化
        val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(
            winnerData.elo,
            loserData.elo,
            CobblemonRanked.config.eloKFactor,
            CobblemonRanked.config.minElo
        )

        val eloDiffWinner = newWinnerElo - winnerData.elo
        val eloDiffLoser = newLoserElo - loserData.elo

        // 更新胜利者数据
        winnerData.apply {
            elo = newWinnerElo
            wins++
            winStreak++
            if (winStreak > bestWinStreak) bestWinStreak = winStreak
        }

        // 更新失败者数据
        loserData.apply {
            elo = newLoserElo
            losses++
            winStreak = 0
        }

        rankDao.savePlayerData(winnerData)
        rankDao.savePlayerData(loserData)

        // 发送战斗结果消息
        sendBattleResultMessage(winner, winnerData, eloDiffWinner)
        sendBattleResultMessage(loser, loserData, eloDiffLoser)

        // 发放奖励
        rewardManager.grantRankRewardIfEligible(winner, winnerData.getRankTitle(), formatName, server)
        rewardManager.grantRankRewardIfEligible(loser, loserData.getRankTitle(), formatName, server)
    }

    /**
     * 获取或创建玩家数据
     * @param playerId 玩家UUID
     * @param seasonId 赛季ID
     * @param format 战斗格式
     * @return 玩家排名数据
     */
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

    /**
     * 发送战斗结果消息
     * @param player 玩家实体
     * @param data 玩家排名数据
     * @param eloChange Elo变化值
     */
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

    /**
     * 发放排名奖励
     * @param player 玩家实体
     * @param rank 排名等级
     * @param format 战斗格式
     * @param server 服务器实例
     */
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

    /**
     * 执行奖励命令
     * @param command 命令字符串
     * @param player 玩家实体
     * @param server 服务器实例
     */
    private fun executeRewardCommand(command: String, player: PlayerEntity, server: MinecraftServer) {
        val formattedCommand = command
            .replace("{player}", player.name.string)
            .replace("{uuid}", player.uuid.toString())
        server.commandManager.executeWithPrefix(server.commandSource, formattedCommand)
    }
}