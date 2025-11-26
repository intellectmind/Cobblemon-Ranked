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
    private val queue = ConcurrentHashMap<UUID, QueueEntry>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val formatMap = mutableMapOf<String, BattleFormat>()

    init {
        // 每5秒执行一次队列处理
        registerDisconnectHandler()
        registerRespawnHandler()

        scheduler.scheduleAtFixedRate({
            cleanupStaleEntries()
            processQueue()
        }, 5, 5, TimeUnit.SECONDS)
        initializeFormatMap()
    }

    // 初始化模式映射表
    private fun initializeFormatMap() {
        formatMap["singles"] = BattleFormat.GEN_9_SINGLES
        formatMap["doubles"] = BattleFormat.GEN_9_DOUBLES
        formatMap["2v2singles"] = BattleFormat.GEN_9_SINGLES
        // 待添加更多对战模式
    }

    // 玩家冷却时间记录
    private val cooldownMap = mutableMapOf<UUID, Long>()
    companion object {
        private const val BATTLE_COOLDOWN_MS = 10_000L // 战斗冷却时间10秒

        // 断线处理
        fun registerDisconnectHandler() {
            ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
                val player = handler.player
                val uuid = player.uuid

                try {
                    // 强制清理玩家的所有战斗相关数据
                    BattleHandler.forceCleanupPlayerBattleData(player)

                    // 从单人匹配队列中移除
                    matchmakingQueue.removePlayer(uuid)

                    // 从 2v2 匹配队列中移除
                    DuoMatchmakingQueue.removePlayer(player)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        // 死亡/重生处理
        fun registerRespawnHandler() {
            ServerPlayerEvents.AFTER_RESPAWN.register { oldPlayer, newPlayer, alive ->
                val uuid = newPlayer.uuid

                // 移除单人队列
                if (matchmakingQueue.queue.containsKey(uuid)) {
                    matchmakingQueue.removePlayer(uuid)
                    val lang = CobblemonRanked.config.defaultLang
                    RankUtils.sendMessage(newPlayer, MessageConfig.get("queue.leave", lang))
                }

                // 移除双人队列
                if (DuoMatchmakingQueue.removePlayer(oldPlayer)) {
                    val lang = CobblemonRanked.config.defaultLang
                    RankUtils.sendMessage(newPlayer, MessageConfig.get("queue.leave", lang))
                }

                // 确保清理战斗数据
                BattleHandler.forceCleanupPlayerBattleData(newPlayer)
            }
        }
    }

    /**
     * 添加玩家到匹配队列
     * @param player 要添加的玩家
     * @param formatName 对战模式名称
     */
    fun addPlayer(player: ServerPlayerEntity, formatName: String) {
        val lang = config.defaultLang
        val now = System.currentTimeMillis()
        val nextAllowedTime = cooldownMap[player.uuid] ?: 0L
        val server = player.server

        if (!canPlayerJoinQueue(player)) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.cannot_join", lang))
            return
        }

        // 检查冷却时间
        if (now < nextAllowedTime) {
            val secondsLeft = ((nextAllowedTime - now) / 1000).coerceAtLeast(1)
            RankUtils.sendMessage(player, MessageConfig.get("queue.cooldown", lang, "seconds" to secondsLeft))
            return
        }

        // 检查模式是否有效
        val format = formatMap[formatName] ?: run {
            RankUtils.sendMessage(player, MessageConfig.get("queue.invalid_format", lang, "format" to formatName))
            return
        }

        // 检查模式是否禁用
        if (!config.allowedFormats.contains(formatName.lowercase())) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.ban_format", lang, "format" to formatName))
            return
        }

        // 检查玩家是否已在战斗中
        val battleRegistry = Cobblemon.battleRegistry
        if (battleRegistry.getBattleByParticipatingPlayer(player) != null) {
            RankUtils.sendMessage(player, MessageConfig.get("queue.already_in_battle", lang))
            return
        }

        // 如果是2v2singles，则交由DuoMatchmakingQueue处理
        if (formatName.lowercase() == "2v2singles") {
            try {
                val team = getPlayerTeam(player)
                DuoMatchmakingQueue.joinQueue(player, team)

                // 发送全局加入消息
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
            // 获取玩家队伍并验证
            val team = getPlayerTeam(player)
            if (!BattleHandler.validateTeam(player, team, format)) return

            // 创建队列条目并加入队列
            val entry = QueueEntry(player, format, team, System.currentTimeMillis())
            queue[player.uuid] = entry
            val messageKey = when (formatName.lowercase()) {
                "singles" -> "queue.join_success_singles"
                "doubles" -> "queue.join_success_doubles"
                else -> "queue.join_success_unknown"
            }
            RankUtils.sendMessage(player, MessageConfig.get(messageKey, lang))

            // 发送全局加入消息
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

    /**
     * 从队列中移除玩家
     * @param playerId 要移除的玩家UUID
     */
    fun removePlayer(playerId: UUID) {
        queue.remove(playerId)?.player?.let {
        }
    }

    /**
     * 根据玩家ID和可选模式名称获取队列中的玩家实体
     *
     * @param playerId 要查找的玩家的UUID
     * @param format 可选参数，指定要匹配的模式名称。如果为null则忽略模式检查
     * @return 如果找到匹配的玩家则返回ServerPlayerEntity，否则返回null。当指定format时，只有玩家的模式匹配才会返回
     */
    fun getPlayer(playerId: UUID, format: String? = null): ServerPlayerEntity? {
        // 从队列中获取对应playerId的条目，如果不存在则返回null
        val entry = queue[playerId] ?: return null

        // 如果没有指定format参数，直接返回条目中的玩家
        if (format == null) return entry.player

        // 如果指定了format，则检查玩家条目中的模式是否匹配，匹配则返回玩家，否则返回null
        return if (getFormatName(entry.format) == format) entry.player else null
    }

    /**
     * 清空整个匹配队列
     */
    fun clear() {
        queue.values.forEach {
            RankUtils.sendMessage(it.player, MessageConfig.get("queue.clear", config.defaultLang))
        }
        queue.clear()
    }

    /**
     * 处理匹配队列的核心方法
     */
    private fun processQueue() {
        synchronized(queue) {
            if (queue.size < 2) return

            val entries = queue.values.toList()
            val processedPlayers = mutableSetOf<UUID>() // 追踪本次迭代中已处理的玩家

            // 双重循环寻找匹配的玩家
            for (i in entries.indices) {
                for (j in i + 1 until entries.size) {
                    val player1 = entries[i]
                    val player2 = entries[j]

                    // 跳过已经在本次迭代中被匹配的玩家
                    if (player1.player.uuid in processedPlayers ||
                        player2.player.uuid in processedPlayers) {
                        continue
                    }

                    // 检查对战模式是否相同
                    if (player1.format != player2.format) continue

                    // 检查ELO是否匹配
                    if (!isEloCompatible(player1, player2)) continue

                    // 确保玩家仍在队列中且未在战斗中
                    if (!queue.containsKey(player1.player.uuid) ||
                        !queue.containsKey(player2.player.uuid)) {
                        continue
                    }

                    val battleRegistry = Cobblemon.battleRegistry
                    if (battleRegistry.getBattleByParticipatingPlayer(player1.player) != null ||
                        battleRegistry.getBattleByParticipatingPlayer(player2.player) != null) {
                        // 移除正在战斗但仍在队列中的玩家
                        queue.remove(player1.player.uuid)
                        queue.remove(player2.player.uuid)
                        continue
                    }

                    // 检查玩家是否正在进行其他战斗
                    if (Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player1.player) != null ||
                        Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player2.player) != null) {
                        // 移除正在战斗但仍在队列中的玩家
                        queue.remove(player1.player.uuid)
                        queue.remove(player2.player.uuid)
                        continue
                    }

                    // 再次验证队伍合法性
                    if (!BattleHandler.validateTeam(player1.player, player1.team, player1.format) ||
                        !BattleHandler.validateTeam(player2.player, player2.team, player2.format)) {
                        // 验证失败则重新加入队列
                        queue[player1.player.uuid] = player1
                        queue[player2.player.uuid] = player2
                        continue
                    }

                    // 立即移除两个玩家
                    val removed1 = queue.remove(player1.player.uuid)
                    val removed2 = queue.remove(player2.player.uuid)

                    // 双重检查移除是否成功
                    if (removed1 == null || removed2 == null) {
                        // 如果移除失败，重新添加成功移除的玩家
                        removed1?.let { queue[player1.player.uuid] = it }
                        removed2?.let { queue[player2.player.uuid] = it }
                        continue
                    }

                    // 标记玩家为已处理
                    processedPlayers.add(player1.player.uuid)
                    processedPlayers.add(player2.player.uuid)

                    // 开始排位赛
                    startRankedBattle(player1, player2)
                    return
                }
            }
        }
    }

    /**
     * 检查两个玩家的ELO是否兼容
     * @param player1 玩家1的队列条目
     * @param player2 玩家2的队列条目
     * @return 是否匹配
     */
    private fun isEloCompatible(player1: QueueEntry, player2: QueueEntry): Boolean {
        val dao = CobblemonRanked.rankDao
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val formatName = getFormatName(player1.format)

        // 获取玩家ELO
        val elo1 = dao.getPlayerData(player1.player.uuid, seasonId, formatName)?.elo ?: 1000
        val elo2 = dao.getPlayerData(player2.player.uuid, seasonId, formatName)?.elo ?: 1000
        val eloDiff = kotlin.math.abs(elo1 - elo2)

        // 计算等待时间
        val waitTime = System.currentTimeMillis() - minOf(player1.joinTime, player2.joinTime)
        val maxWaitTime = config.maxQueueTime * 1000L

        // 动态调整ELO差值限制
        val ratio = (waitTime.toDouble() / maxWaitTime).coerceIn(0.0, 1.0)
        val maxMultiplier = config.maxEloMultiplier
        val dynamicMultiplier = 1.0 + (ratio * (maxMultiplier - 1.0))

        val maxDiff = (config.maxEloDiff * dynamicMultiplier).toInt()

        return eloDiff <= maxDiff
    }

    /**
     * 开始排位赛
     * @param player1 玩家1的队列条目
     * @param player2 玩家2的队列条目
     */
    private fun startRankedBattle(player1: QueueEntry, player2: QueueEntry) {
        val lang = config.defaultLang
        // 获取对战宝可梦列表
        val team1 = getBattlePokemonList(player1.player, player1.team)
        val team2 = getBattlePokemonList(player2.player, player2.team)

        // 检查队伍是否有效
        if (team1.isEmpty() || team2.isEmpty()) {
            RankUtils.sendMessage(player1.player, MessageConfig.get("queue.team_load_fail", lang))
            RankUtils.sendMessage(player2.player, MessageConfig.get("queue.team_load_fail", lang))
            // 重新加入队列
            queue[player1.player.uuid] = player1
            queue[player2.player.uuid] = player2
            return
        }

        val server = player1.player.server

        // 获取随机竞技场
        val arenaResult = BattleHandler.getRandomArenaForPlayers(2) ?: run {
            RankUtils.sendMessage(player1.player, MessageConfig.get("queue.no_arena", lang))
            RankUtils.sendMessage(player2.player, MessageConfig.get("queue.no_arena", lang))
            return
        }

        val (arena, positions) = arenaResult

        // 解析世界
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

        // 通知玩家匹配成功
        RankUtils.sendMessage(player1.player, MessageConfig.get("queue.match_success", lang))
        RankUtils.sendMessage(player2.player, MessageConfig.get("queue.match_success", lang))

        // 5秒后开始传送和战斗
        scheduler.schedule({
            server.execute {
                // 检查玩家是否掉线
                if (player1.player.isDisconnected || player2.player.isDisconnected) {
                    // 如果玩家1在线，发送消息并重新加入队列
                    if (!player1.player.isDisconnected) {
                        queue[player1.player.uuid] = player1
                        RankUtils.sendMessage(player1.player, MessageConfig.get("queue.opponent_disconnected", lang))
                    }
                    // 如果玩家2在线，发送消息并重新加入队列
                    if (!player2.player.isDisconnected) {
                        queue[player2.player.uuid] = player2
                        RankUtils.sendMessage(player2.player, MessageConfig.get("queue.opponent_disconnected", lang))
                    }
                    return@execute
                }

                // 再次验证队伍
                if (!BattleHandler.validateTeam(player1.player, player1.team, player1.format) ||
                    !BattleHandler.validateTeam(player2.player, player2.team, player2.format)) {
                    RankUtils.sendMessage(player1.player, MessageConfig.get("queue.cancel_team_changed", lang))
                    RankUtils.sendMessage(player2.player, MessageConfig.get("queue.cancel_team_changed", lang))
                    return@execute
                }

                // 记录返回位置
                BattleHandler.setReturnLocation(player1.player.uuid, player1.player.serverWorld, Triple(player1.player.x, player1.player.y, player1.player.z))
                BattleHandler.setReturnLocation(player2.player.uuid, player2.player.serverWorld, Triple(player2.player.x, player2.player.y, player2.player.z))

                // 传送玩家
                player1.player.teleport(world, positions[0].x, positions[0].y, positions[0].z, 0f, 0f)
                player2.player.teleport(world, positions[1].x, positions[1].y, positions[1].z, 0f, 0f)

                // 应用等级调整
                if (config.enableCustomLevel) {
                    RankUtils.sendMessage(
                        player1.player,
                        MessageConfig.get("queue.customBattleLevel", lang, "level" to config.customBattleLevel)
                    )
                    RankUtils.sendMessage(
                        player2.player,
                        MessageConfig.get("queue.customBattleLevel", lang, "level" to config.customBattleLevel)
                    )
                    // 记录当前经验
                    BattleHandler.prepareBattleSnapshot(player1.player, player1.team)
                    BattleHandler.prepareBattleSnapshot(player2.player, player2.team)
                    // 应用等级
                    BattleHandler.applyLevelAdjustments(player1.player)
                    BattleHandler.applyLevelAdjustments(player2.player)
                }

                // 创建战斗参与者
                val actor1 = PlayerBattleActor(player1.player.uuid, team1)
                val actor2 = PlayerBattleActor(player2.player.uuid, team2)
                val side1 = BattleSide(actor1)
                val side2 = BattleSide(actor2)

                // 开始战斗
                val result = Cobblemon.battleRegistry.startBattle(player1.format, side1, side2)

                var battle: PokemonBattle? = null
                var failReason: String? = null

                result.ifSuccessful { b -> battle = b }
                result.ifErrored { error -> failReason = error.toString() }

                // 处理战斗开始失败
                if (battle == null) {
                    val errorMsg = failReason ?: "未知错误"
                    RankUtils.sendMessage(player1.player, MessageConfig.get("queue.battle_start_fail", lang, "reason" to errorMsg))
                    RankUtils.sendMessage(player2.player, MessageConfig.get("queue.battle_start_fail", lang, "reason" to errorMsg))

                    // 设置冷却时间
                    val cooldownUntil = System.currentTimeMillis() + BATTLE_COOLDOWN_MS
                    cooldownMap[player1.player.uuid] = cooldownUntil
                    cooldownMap[player2.player.uuid] = cooldownUntil
                    return@execute
                }

                // 注册排位赛
                val battleId = UUID.randomUUID()
                val formatName = getFormatName(player1.format)
                val nonNullBattle = battle!!

                BattleHandler.markAsRanked(battleId, formatName)
                BattleHandler.registerBattle(nonNullBattle, battleId)

                // 通知玩家战斗开始
                RankUtils.sendMessage(player1.player, MessageConfig.get("queue.battle_start", lang, "opponent" to player2.player.name.string))
                RankUtils.sendMessage(player2.player, MessageConfig.get("queue.battle_start", lang, "opponent" to player1.player.name.string))
            }
        }, 5, TimeUnit.SECONDS)
    }

    /**
     * 获取玩家的队伍UUID列表
     * @param player 玩家实体
     * @return 宝可梦UUID列表
     */
    private fun getPlayerTeam(player: ServerPlayerEntity): List<UUID> {
        val party = Cobblemon.storage.getParty(player)

        // 空队伍检查
        if (party.count() == 0) {
            throw IllegalStateException("队伍为空")
        }

        // 返回所有宝可梦的UUID
        return party.mapNotNull { pokemon ->
            pokemon?.uuid
        }
    }

    /**
     * 获取BattlePokemon列表
     * @param player 玩家实体
     * @param team 宝可梦UUID列表
     * @return BattlePokemon列表
     */
    private fun getBattlePokemonList(player: ServerPlayerEntity, team: List<UUID>): List<BattlePokemon> {
        return team.mapNotNull { id -> getBattlePokemon(player, id) }
    }

    /**
     * 根据UUID获取BattlePokemon
     * @param player 玩家实体
     * @param uuid 宝可梦UUID
     * @return BattlePokemon或null
     */
    private fun getBattlePokemon(player: ServerPlayerEntity, uuid: UUID): BattlePokemon? {
        val party = Cobblemon.storage.getParty(player)
        val pokemon: Pokemon? = party.find { it?.uuid == uuid }
        return pokemon?.let { BattlePokemon(it) }
    }

    /**
     * 获取对战模式名称
     * @param format 对战模式
     * @return 模式名称字符串
     */
    private fun getFormatName(format: BattleFormat): String {
        return when (format) {
            BattleFormat.GEN_9_SINGLES -> "singles"
            BattleFormat.GEN_9_DOUBLES -> "doubles"
            else -> "custom"
        }
    }

    /**
     * 重新加载配置
     * @param newConfig 新配置
     */
    fun reloadConfig(newConfig: RankConfig) {
        // 重新初始化模式映射
        formatMap["singles"] = BattleFormat.GEN_9_SINGLES
        formatMap["doubles"] = BattleFormat.GEN_9_DOUBLES
        formatMap["2v2singles"] = BattleFormat.GEN_9_SINGLES
    }

    /**
     * 关闭队列处理器
     */
    fun shutdown() {
        scheduler.shutdownNow()
    }

    /**
     * 队列条目数据类
     */
    data class QueueEntry(
        val player: ServerPlayerEntity,  // 玩家实体
        val format: BattleFormat,       // 对战模式
        val team: List<UUID>,           // 队伍UUID列表
        val joinTime: Long              // 加入队列的时间戳
    )

    /**
     * 清理仍在队列但已在战斗中的玩家
     */
    fun cleanupStaleEntries() {
        synchronized(queue) {
            val toRemove = mutableListOf<UUID>()

            queue.values.forEach { entry ->
                val battleRegistry = Cobblemon.battleRegistry

                // 移除正在战斗的玩家
                if (battleRegistry.getBattleByParticipatingPlayer(entry.player) != null) {
                    toRemove.add(entry.player.uuid)
                }
                // 移除已断线的玩家
                else if (entry.player.isDisconnected) {
                    toRemove.add(entry.player.uuid)
                }
            }

            toRemove.forEach { uuid ->
                queue.remove(uuid)
            }
        }
    }

    /**
     * 检查玩家是否可以安全加入队列
     */
    fun canPlayerJoinQueue(player: ServerPlayerEntity): Boolean {
        // 已经在队列中
        if (queue.containsKey(player.uuid)) return false

        val battleRegistry = Cobblemon.battleRegistry
        if (battleRegistry.getBattleByParticipatingPlayer(player) != null) return false

        // 玩家已断线
        if (player.isDisconnected) return false

        return true
    }
}