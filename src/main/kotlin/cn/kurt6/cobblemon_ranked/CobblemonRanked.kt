package cn.kurt6.cobblemon_ranked

import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.battle.DuoBattleManager
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
import java.util.concurrent.CompletableFuture

class CobblemonRanked : ModInitializer {
    override fun onInitialize() {
        INSTANCE = this
        logger.info("Initializing Cobblemon Ranked Mod")

        dataPath = FabricLoader.getInstance().configDir.resolve(MOD_ID).apply { toFile().mkdirs() }
        config = ConfigManager.load()

        databaseConfig = ConfigManager.loadDatabaseConfig()

        MessageConfig.get("msg_example")

        rankDao = RankDao(databaseConfig, dataPath.toFile())
        rewardManager = RewardManager(rankDao)
        seasonManager = SeasonManager(rankDao)

        matchmakingQueue = MatchmakingQueue()

        registerCommands()
        registerEvents()
        setupSeasonCheck()

        RankPlaceholders.register()

        ServerPlayConnectionEvents.JOIN.register { handler, sender, server ->
            val player = handler.player
            server.execute {
                BattleHandler.teleportBackIfPossible(player)
                BattleHandler.restorePlayerPokemonLevels(player)
            }
            CrossServerSocket.handlePlayerJoin(handler.player)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            cn.kurt6.cobblemon_ranked.battle.TeamSelectionManager.handleDisconnect(handler.player)
            cn.kurt6.cobblemon_ranked.util.ClientVersionTracker.removePlayer(handler.player.uuid)
            CrossServerSocket.handlePlayerDisconnect(handler.player)
        }

        PayloadTypeRegistry.playC2S().register(
            RequestPlayerRankPayload.ID,
            RequestPlayerRankPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            ClientVersionPayload.ID,
            ClientVersionPayload.CODEC
        )

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
        PayloadTypeRegistry.playS2C().register(
            TeamSelectionStartPayload.ID,
            TeamSelectionStartPayload.CODEC
        )
        PayloadTypeRegistry.playS2C().register(
            TeamSelectionEndPayload.ID,
            TeamSelectionEndPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            TeamSelectionSubmitPayload.ID,
            TeamSelectionSubmitPayload.CODEC
        )

        ServerPlayNetworking.registerGlobalReceiver(TeamSelectionSubmitPayload.ID) { payload, context ->
            cn.kurt6.cobblemon_ranked.battle.TeamSelectionManager.handleSubmission(context.player(), payload.selectedUuids)
        }

        ServerPlayNetworking.registerGlobalReceiver(ClientVersionPayload.ID) { payload, context ->
            cn.kurt6.cobblemon_ranked.util.ClientVersionTracker.setPlayerVersion(context.player().uuid, payload.version)
        }

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
            try {
                BattleHandler.clearAllRankedBattleMarkers()
                BattleHandler.clearAllUsedPokemon()

                matchmakingQueue.shutdown()
                DuoMatchmakingQueue.shutdown()
                matchmakingQueue.clear()
                CrossServerSocket.disconnect()
                cn.kurt6.cobblemon_ranked.battle.TeamSelectionManager.shutdown()
                DuoBattleManager.clearAll()

                rankDao.close()
            } catch (e: Exception) {
                logger.warn("Error during server stopping", e)
            }
        }
    }

    private fun setupSeasonCheck() {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            BattleHandler.clearAllRankedBattleMarkers()
            BattleHandler.clearAllUsedPokemon()
            logger.info("Cleared all ranked battle markers and used Pokemon on server start")

            var tickCounter = 0
            val interval = 20 * 60 * 10
            var cleanupCounter = 0
            val cleanupInterval = 20 * 60 * 60 * 24

            ServerTickEvents.START_SERVER_TICK.register {
                if (++tickCounter >= interval) {
                    tickCounter = 0
                    seasonManager.checkSeasonEnd(server)
                }

                if (++cleanupCounter >= cleanupInterval) {
                    cleanupCounter = 0
                    CompletableFuture.runAsync {
                        try {
                            rankDao.cleanupOldReturnLocations()
                            logger.info("Performed daily cleanup of old return locations")
                        } catch (e: Exception) {
                            logger.warn("Failed to cleanup old return locations", e)
                        }
                    }
                    server.execute {
                        BattleHandler.cleanupStaleRankedBattleMarkers(server)
                        BattleHandler.cleanupStaleUsedPokemonMarkers(server)
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