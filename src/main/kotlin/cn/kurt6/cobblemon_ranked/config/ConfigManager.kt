package cn.kurt6.cobblemon_ranked.config

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.matchmaking.DuoMatchmakingQueue
import cn.kurt6.cobblemon_ranked.util.RankUtils
import kotlinx.serialization.json.JsonPrimitive
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object ConfigManager {
    private val configPath: Path = CobblemonRanked.dataPath.resolve("cobblemon_ranked.json")
    private val dbConfigPath: Path = CobblemonRanked.dataPath.resolve("database.json")
    private val jankson = blue.endless.jankson.Jankson.builder().build()

    fun decodeUnicode(input: String): String {
        val regex = Regex("""\\u([0-9a-fA-F]{4})""")
        return regex.replace(input) {
            val hex = it.groupValues[1]
            hex.toInt(16).toChar().toString()
        }
    }

    fun loadDatabaseConfig(): DatabaseConfig {
        return try {
            if (Files.exists(dbConfigPath)) {
                val jsonText = Files.readString(dbConfigPath)
                val json = jankson.load(jsonText) as blue.endless.jankson.JsonObject
                val rawConfig = jankson.fromJson(json, DatabaseConfig::class.java)

                val mysqlJson = json.getObject("mysql")
                val mysqlConfig = if (mysqlJson != null) {
                    MySQLConfig(
                        host = mysqlJson.get(String::class.java, "host") ?: "localhost",
                        port = mysqlJson.get(Int::class.java, "port") ?: 3306,
                        database = mysqlJson.get(String::class.java, "database") ?: "cobblemon_ranked",
                        username = mysqlJson.get(String::class.java, "username") ?: "root",
                        password = mysqlJson.get(String::class.java, "password") ?: "",
                        poolSize = mysqlJson.get(Int::class.java, "poolSize") ?: 10,
                        connectionTimeout = mysqlJson.get(Long::class.java, "connectionTimeout") ?: 5000,
                        parameters = mysqlJson.get(String::class.java, "parameters")
                            ?: "useSSL=false&serverTimezone=UTC&characterEncoding=utf8"
                    )
                } else {
                    rawConfig.mysql
                }

                rawConfig.copy(mysql = mysqlConfig)
            } else {
                val default = DatabaseConfig()
                saveDatabaseConfig(default)
                default
            }
        } catch (e: Exception) {
            CobblemonRanked.logger.error("Failed to load database config, using defaults", e)
            DatabaseConfig()
        }
    }

    fun saveDatabaseConfig(config: DatabaseConfig) {
        try {
            val json = jankson.toJson(config).toJson(true, true)
            Files.writeString(dbConfigPath, json, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            CobblemonRanked.logger.error("Failed to save database config", e)
        }
    }

    fun load(): RankConfig {
        return try {
            if (Files.exists(configPath)) {
                val jsonText = Files.readString(configPath)
                val json = jankson.load(jsonText) as blue.endless.jankson.JsonObject
                val rawConfig = jankson.fromJson(json, RankConfig::class.java)

                val rawTitlesJson = json.getObject("rankTitles")
                val fixedRankTitles = rawTitlesJson?.entries?.mapNotNull { (k, v) ->
                    k.toIntOrNull()?.let { elo ->
                        val encoded = v.toString().trim('"')
                        elo to decodeUnicode(encoded)
                    }
                }?.toMap() ?: emptyMap()

                val rawRankRewards = json.getObject("rankRewards")
                val fixedRankRewards: Map<String, Map<String, List<String>>> = rawRankRewards?.entries?.mapNotNull { (formatKey, rankMapElement) ->
                    val rankMap = rankMapElement as? blue.endless.jankson.JsonObject ?: return@mapNotNull null

                    val rankToCommands = rankMap.entries.associate { (rankKey, commandsElement) ->
                        val jsonArray = commandsElement as? blue.endless.jankson.JsonArray
                        val commandsList = jsonArray?.map { it.toString().trim('"') } ?: emptyList()
                        rankKey.trim() to commandsList
                    }

                    formatKey to rankToCommands
                }?.toMap() ?: emptyMap()

                val rawBannedCarriedItems = json.get("bannedCarriedItems") as? blue.endless.jankson.JsonArray
                val fixedBannedCarriedItems = rawBannedCarriedItems
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedCarriedItems

                val rawBannedHeldItems = json.get("bannedHeldItems") as? blue.endless.jankson.JsonArray
                val fixedBannedHeldItems = rawBannedHeldItems
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedHeldItems

                val rawBannedPokemon = json.get("bannedPokemon") as? blue.endless.jankson.JsonArray
                val fixedBannedPokemon = rawBannedPokemon
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedPokemon

                val rawAllowedFormats = json.get("allowedFormats") as? blue.endless.jankson.JsonArray
                val fixedAllowedFormats = rawAllowedFormats
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.allowedFormats

                val rawMaxQueueTime = json.get("maxQueueTime") as? JsonPrimitive
                val fixedMaxQueueTime = rawMaxQueueTime?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.maxQueueTime

                val rawMaxEloMultiplier = json.get("maxEloMultiplier") as? JsonPrimitive
                val fixedMaxEloMultiplier = rawMaxEloMultiplier?.toString()?.removeSurrounding("\"")?.toDoubleOrNull() ?: rawConfig.maxEloMultiplier

                val rawBattleArenas = json.get("battleArenas") as? blue.endless.jankson.JsonArray
                val fixedBattleArenas = rawBattleArenas?.mapNotNull { arenaElement ->
                    val arenaObject = arenaElement as? blue.endless.jankson.JsonObject ?: return@mapNotNull null
                    val world = (arenaObject["world"] as? blue.endless.jankson.JsonPrimitive)?.value as? String ?: "minecraft:overworld"
                    val positionsArray = arenaObject["playerPositions"] as? blue.endless.jankson.JsonArray ?: return@mapNotNull null
                    val positions = positionsArray.mapNotNull { posElement ->
                        val posObject = posElement as? blue.endless.jankson.JsonObject ?: return@mapNotNull null
                        val x = (posObject["x"] as? blue.endless.jankson.JsonPrimitive)?.value.toString().toDoubleOrNull() ?: return@mapNotNull null
                        val y = (posObject["y"] as? blue.endless.jankson.JsonPrimitive)?.value.toString().toDoubleOrNull() ?: return@mapNotNull null
                        val z = (posObject["z"] as? blue.endless.jankson.JsonPrimitive)?.value.toString().toDoubleOrNull() ?: return@mapNotNull null
                        ArenaCoordinate(x, y, z)
                    }
                    if (positions.size == 2) BattleArena(world, positions) else null
                } ?: rawConfig.battleArenas

                val rawDefaultLang = json.get("defaultLang")
                val fixedDefaultLang = rawDefaultLang?.toString()?.removeSurrounding("\"") ?: rawConfig.defaultLang

                val rawDefaultFormat = json.get("defaultFormat")
                val fixedDefaultFormat = rawDefaultFormat?.toString()?.removeSurrounding("\"") ?: rawConfig.defaultFormat

                val rawMinTeamSize = json.get("minTeamSize")
                val fixedMinTeamSize = rawMinTeamSize?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.minTeamSize

                val rawMaxTeamSize = json.get("maxTeamSize")
                val fixedMaxTeamSize = rawMaxTeamSize?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.maxTeamSize

                val rawMaxEloDiff = json.get("maxEloDiff")
                val fixedMaxEloDiff = rawMaxEloDiff?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.maxEloDiff

                val rawSeasonDuration = json.get("seasonDuration")
                val fixedSeasonDuration = rawSeasonDuration?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.seasonDuration

                val rawInitialElo = json.get("initialElo")
                val fixedInitialElo = rawInitialElo?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.initialElo

                val rawEloKFactor = json.get("eloKFactor")
                val fixedEloKFactor = rawEloKFactor?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.eloKFactor

                val rawMinElo = json.get("minElo")
                val fixedMinElo = rawMinElo?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.minElo

                val rawMaxLevel = json.get("maxLevel")
                val fixedMaxLevel = rawMaxLevel?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.maxLevel

                val rawAllowDuplicateSpecies = json.get("allowDuplicateSpecies")
                val fixedAllowDuplicateSpecies = rawAllowDuplicateSpecies?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.allowDuplicateSpecies

                val rawCustomBattleLevel = json.get("customBattleLevel")
                val fixedCustomBattleLevel = rawCustomBattleLevel?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.customBattleLevel

                val rawEnableCustomLevel = json.get("enableCustomLevel")
                val fixedEnableCustomLevel = rawEnableCustomLevel?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.enableCustomLevel

                val rawRankRequirementsElement = json.get("rankRequirements")
                val fixedRankRequirements: Map<String, Double> = if (rawRankRequirementsElement is blue.endless.jankson.JsonObject) {
                    rawRankRequirementsElement.entries.mapNotNull { (rank, jsonValue) ->
                        val primitive = jsonValue as? blue.endless.jankson.JsonPrimitive
                        val number = primitive?.value?.toString()?.toDoubleOrNull()
                        if (number != null) rank to number else null
                    }.toMap()
                } else {
                    rawConfig.rankRequirements
                }

                val rawBannedMoves = json.get("bannedMoves") as? blue.endless.jankson.JsonArray
                val fixedBannedMoves = rawBannedMoves
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedMoves

                val rawBannedNatures = json.get("bannedNatures") as? blue.endless.jankson.JsonArray
                val fixedBannedNatures = rawBannedNatures
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedCarriedItems

                val rawBannedAbilities = json.get("bannedAbilities") as? blue.endless.jankson.JsonArray
                val fixedBannedAbilities = rawBannedAbilities
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedAbilities

                val rawBannedGenders = json.get("bannedGenders") as? blue.endless.jankson.JsonArray
                val fixedBannedGenders = rawBannedGenders
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedGenders

                val rawBannedShiny = json.get("bannedShiny")
                val fixedBannedShiny = rawBannedShiny?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.bannedShiny

                val rawEnableCrossServer = json.get("enableCrossServer")
                val fixedEnableCrossServer = rawEnableCrossServer?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.enableCrossServer

                val fixedCloudServerId = when (val element = json.get("cloudServerId")) {
                    is blue.endless.jankson.JsonPrimitive -> {
                        decodeUnicode(element.asString().trim('"'))
                    }
                    is blue.endless.jankson.JsonNull -> rawConfig.cloudServerId
                    null -> rawConfig.cloudServerId
                    else -> element.toString().trim('"')
                }

                val rawcloudToken = json.get("cloudToken")
                val fixedcloudToken = rawcloudToken?.toString()?.removeSurrounding("\"") ?: rawConfig.cloudToken

                val rawcloudApiUrl = json.get("cloudApiUrl")
                val fixedcloudApiUrl = rawcloudApiUrl?.toString()?.removeSurrounding("\"") ?: rawConfig.cloudApiUrl

                val rawcloudWebSocketUrl = json.get("cloudWebSocketUrl")
                val fixedcloudWebSocketUrl = rawcloudWebSocketUrl?.toString()?.removeSurrounding("\"") ?: rawConfig.cloudWebSocketUrl

                val rawBanUsageBelow = json.get("banUsageBelow")
                val fixedBanUsageBelow = rawBanUsageBelow?.toString()?.removeSurrounding("\"")?.toDoubleOrNull() ?: rawConfig.banUsageBelow

                val rawBanUsageAbove = json.get("banUsageAbove")
                val fixedBanUsageAbove = rawBanUsageAbove?.toString()?.removeSurrounding("\"")?.toDoubleOrNull() ?: rawConfig.banUsageAbove

                val rawBanTopUsed = json.get("banTopUsed")
                val fixedBanTopUsed = rawBanTopUsed?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.banTopUsed

                val rawOnlyBaseFormWithEvolution = json.get("onlyBaseFormWithEvolution")
                val fixedOnlyBaseFormWithEvolution = rawOnlyBaseFormWithEvolution?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.onlyBaseFormWithEvolution

                val rawVictoryRewards = json.get("victoryRewards") as? blue.endless.jankson.JsonArray
                val fixedVictoryRewards = rawVictoryRewards
                    ?.mapNotNull {
                        when (it) {
                            is blue.endless.jankson.JsonPrimitive -> {
                                val rawValue = it.asString()
                                decodeUnicode(rawValue)
                            }
                            is blue.endless.jankson.JsonNull -> null
                            else -> {
                                val stringValue = it.toString().trim('"')
                                decodeUnicode(stringValue)
                            }
                        }
                    }
                    ?: rawConfig.victoryRewards

                rawConfig.copy(
                    rankTitles = fixedRankTitles,
                    rankRewards = fixedRankRewards,
                    allowedFormats = fixedAllowedFormats,
                    bannedCarriedItems = fixedBannedCarriedItems,
                    bannedHeldItems = fixedBannedHeldItems,
                    bannedPokemon = fixedBannedPokemon,
                    maxQueueTime = fixedMaxQueueTime,
                    maxEloMultiplier = fixedMaxEloMultiplier,
                    battleArenas = fixedBattleArenas,
                    defaultFormat = fixedDefaultFormat,
                    defaultLang = fixedDefaultLang,
                    minTeamSize = fixedMinTeamSize,
                    maxTeamSize = fixedMaxTeamSize,
                    maxEloDiff = fixedMaxEloDiff,
                    seasonDuration = fixedSeasonDuration,
                    initialElo = fixedInitialElo,
                    eloKFactor = fixedEloKFactor,
                    minElo = fixedMinElo,
                    allowDuplicateSpecies = fixedAllowDuplicateSpecies,
                    maxLevel = fixedMaxLevel,
                    customBattleLevel = fixedCustomBattleLevel,
                    enableCustomLevel = fixedEnableCustomLevel,
                    banUsageBelow = fixedBanUsageBelow,
                    banUsageAbove = fixedBanUsageAbove,
                    banTopUsed = fixedBanTopUsed,
                    onlyBaseFormWithEvolution = fixedOnlyBaseFormWithEvolution,
                    rankRequirements = fixedRankRequirements,
                    bannedMoves = fixedBannedMoves,
                    bannedNatures = fixedBannedNatures,
                    bannedAbilities = fixedBannedAbilities,
                    bannedGenders = fixedBannedGenders,
                    bannedShiny = fixedBannedShiny,
                    enableCrossServer = fixedEnableCrossServer,
                    cloudServerId = fixedCloudServerId,
                    cloudToken = fixedcloudToken,
                    cloudApiUrl = fixedcloudApiUrl,
                    cloudWebSocketUrl = fixedcloudWebSocketUrl,
                    victoryRewards = fixedVictoryRewards
                )
            } else {
                val default = RankConfig()
                save(default)
                default
            }
        } catch (e: Exception) {
            throw RuntimeException("Configuration file loading failed: ${e.message}", e)
        }
    }

    fun save(config: RankConfig) {
        val json = jankson.toJson(config).toJson(true, true)
        Files.writeString(configPath, json, StandardCharsets.UTF_8)
    }

    fun reload(): RankConfig {
        val newConfig = load()

        CobblemonRanked.matchmakingQueue.clear()
        synchronized(DuoMatchmakingQueue) {
            // DuoMatchmakingQueue 内部的清理
        }

        CobblemonRanked.config = newConfig
        CobblemonRanked.matchmakingQueue.reloadConfig(newConfig)

        try {
            val server = CobblemonRanked.INSTANCE.javaClass.getDeclaredField("server")
                .apply { isAccessible = true }
                .get(CobblemonRanked.INSTANCE) as? net.minecraft.server.MinecraftServer

            server?.playerManager?.playerList?.forEach { player ->
                RankUtils.sendMessage(
                    player,
                    MessageConfig.get("config.reloaded", newConfig.defaultLang)
                )
            }
        } catch (e: Exception) {
            // 忽略通知失败
        }

        return newConfig
    }
}