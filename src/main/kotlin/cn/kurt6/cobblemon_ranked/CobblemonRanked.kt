// CobblemonRanked.kt
package cn.kurt6.cobblemon_ranked

import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.commands.RankCommands
import cn.kurt6.cobblemon_ranked.config.ConfigManager
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.config.RankConfig
import cn.kurt6.cobblemon_ranked.data.RankDao
import cn.kurt6.cobblemon_ranked.data.RewardManager
import cn.kurt6.cobblemon_ranked.data.SeasonManager
import cn.kurt6.cobblemon_ranked.matchmaking.DuoMatchmakingQueue
import cn.kurt6.cobblemon_ranked.matchmaking.MatchmakingQueue
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
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

        // 初始化消息配置
        MessageConfig.get("msg_example")

        // 初始化核心组件
        rankDao = RankDao(dataPath.resolve("ranked.db").toFile())
        rewardManager = RewardManager(rankDao)
        seasonManager = SeasonManager(rankDao, rewardManager)

        matchmakingQueue = MatchmakingQueue()

        // 注册系统和事件
        registerCommands()
        registerEvents()
        setupSeasonCheck()

        logger.info("Cobblemon Ranked Mod initialized")
    }

    private fun registerCommands() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            RankCommands.register(dispatcher)
        }
    }

    private fun registerEvents() {
        BattleHandler.register()
        ServerLifecycleEvents.SERVER_STOPPING.register {
            matchmakingQueue.clear()
            matchmakingQueue.shutdown()
            DuoMatchmakingQueue.shutdown()
            rankDao.close()
        }
    }

    private fun setupSeasonCheck() {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            var tickCounter = 0
            val interval = 20 * 60 * 10 // 10分钟

            ServerTickEvents.START_SERVER_TICK.register {
                if (++tickCounter >= interval) {
                    tickCounter = 0
                    seasonManager.checkSeasonEnd(server)
                }
            }
        }
    }

    companion object {
        const val MOD_ID = "cobblemon-ranked"
        private val logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var INSTANCE: CobblemonRanked
        lateinit var config: RankConfig
        lateinit var dataPath: Path
        lateinit var rankDao: RankDao
        lateinit var matchmakingQueue: MatchmakingQueue
        lateinit var seasonManager: SeasonManager
        lateinit var rewardManager: RewardManager
    }
}