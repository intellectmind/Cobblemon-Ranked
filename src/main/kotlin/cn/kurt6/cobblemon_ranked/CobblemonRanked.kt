package cn.kurt6.cobblemon_ranked

import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.commands.RankCommands
import cn.kurt6.cobblemon_ranked.config.ConfigManager
import cn.kurt6.cobblemon_ranked.config.DatabaseConfig
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.config.RankConfig
import cn.kurt6.cobblemon_ranked.crossserver.CrossCommand
import cn.kurt6.cobblemon_ranked.crossserver.CrossServerSocket
import cn.kurt6.cobblemon_ranked.data.RankDao
import cn.kurt6.cobblemon_ranked.data.RewardManager
import cn.kurt6.cobblemon_ranked.data.SeasonManager
import cn.kurt6.cobblemon_ranked.matchmaking.DuoMatchmakingQueue
import cn.kurt6.cobblemon_ranked.matchmaking.MatchmakingQueue
import cn.kurt6.cobblemon_ranked.util.RankPlaceholders
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import cn.kurt6.cobblemon_ranked.network.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Path

class CobblemonRanked : ModInitializer {
    override fun onInitialize() {
        INSTANCE = this
        logger.info("Initializing Cobblemon Ranked Mod")

        // 初始化路径和配置
        dataPath = FabricLoader.getInstance().configDir.resolve(MOD_ID).apply { toFile().mkdirs() }
        config = ConfigManager.load()

        // 加载数据库配置
        databaseConfig = ConfigManager.loadDatabaseConfig()

        // 初始化消息配置
        MessageConfig.get("msg_example")

        // 初始化核心组件 - 使用数据库配置
        rankDao = RankDao(databaseConfig, dataPath.toFile())
        rewardManager = RewardManager(rankDao)
        seasonManager = SeasonManager(rankDao, rewardManager)

        matchmakingQueue = MatchmakingQueue()

        // 注册系统和事件
        registerCommands()
        registerEvents()
        setupSeasonCheck()

        // 注册 Placeholders
        RankPlaceholders.register()

        // 注册玩家登录事件
        ServerPlayConnectionEvents.JOIN.register { handler, sender, server ->
            val player = handler.player
            // 检查并恢复原始等级（如果数据库中有记录）
            BattleHandler.restoreLevelsFromDatabase(player)

            CrossServerSocket.handlePlayerJoin(handler.player)
        }

        // 注册玩家退出事件
        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            CrossServerSocket.handlePlayerDisconnect(handler.player)
        }

        // 注册客户端到服务器负载 (C2S)
        PayloadTypeRegistry.playC2S().register(
            RequestPlayerRankPayload.ID,
            RequestPlayerRankPayload.CODEC
        )

        // 注册服务器到客户端负载 (S2C)
        PayloadTypeRegistry.playS2C().register(
            PlayerRankDataPayload.ID,
            PlayerRankDataPayload.CODEC
        )
        PayloadTypeRegistry.playS2C().register(
            SeasonInfoTextPayload.ID,
            SeasonInfoTextPayload.CODEC
        )
        PayloadTypeRegistry.playS2C().register(
            LeaderboardPayload.ID,
            LeaderboardPayload.CODEC
        )

        // 注册网络处理器
        ServerPlayNetworking.registerGlobalReceiver(RequestPlayerRankPayload.ID) { payload, context ->
            ServerNetworking.handle(payload, context)
        }

        logger.info("Cobblemon Ranked Mod initialized with database: ${databaseConfig.databaseType}")
    }

    private fun registerCommands() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            RankCommands.register(dispatcher)
            dispatcher.register(CrossCommand.register())
        }
    }

    private fun registerEvents() {
        BattleHandler.register()
        ServerLifecycleEvents.SERVER_STOPPING.register {
            matchmakingQueue.clear()
            matchmakingQueue.shutdown()
            DuoMatchmakingQueue.shutdown()
            BattleHandler.shutdown()
            rankDao.close()
            CrossServerSocket.disconnect()
        }
    }

    private fun setupSeasonCheck() {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            var tickCounter = 0
            val interval = 20 * 60 * 10 // 10分钟
            var cleanupCounter = 0
            val cleanupInterval = 20 * 60 * 60 * 24 // 24小时

            ServerTickEvents.START_SERVER_TICK.register {
                if (++tickCounter >= interval) {
                    tickCounter = 0
                    seasonManager.checkSeasonEnd(server)
                }

                if (++cleanupCounter >= cleanupInterval) {
                    cleanupCounter = 0
                    try {
                        rankDao.cleanupOldReturnLocations()
                    } catch (e: Exception) {
                        logger.warn("Failed to cleanup old return locations", e)
                    }
                }
            }
        }
    }

    companion object {
        const val MOD_ID = "cobblemon_ranked"
        val logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var INSTANCE: CobblemonRanked
        lateinit var config: RankConfig
        lateinit var databaseConfig: DatabaseConfig
        lateinit var dataPath: Path
        lateinit var rankDao: RankDao
        lateinit var matchmakingQueue: MatchmakingQueue
        lateinit var seasonManager: SeasonManager
        lateinit var rewardManager: RewardManager
    }
}