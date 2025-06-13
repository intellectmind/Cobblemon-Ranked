// ConfigManager.kt
package cn.kurt6.cobblemon_ranked.config

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import kotlinx.serialization.json.JsonPrimitive
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object ConfigManager {
    private val path: Path = CobblemonRanked.dataPath.resolve("cobblemon_ranked.json")
    private val jankson = blue.endless.jankson.Jankson.builder().build()

    fun decodeUnicode(input: String): String {
        val regex = Regex("""\\u([0-9a-fA-F]{4})""")
        return regex.replace(input) {
            val hex = it.groupValues[1]
            hex.toInt(16).toChar().toString()
        }
    }

    fun load(): RankConfig {
        return try {
            if (Files.exists(path)) {
                val jsonText = Files.readString(path)
                val json = jankson.load(jsonText) as blue.endless.jankson.JsonObject
                val rawConfig = jankson.fromJson(json, RankConfig::class.java)

                // 解析 rankTitles（将字符串键转为 Int）
                val rawTitlesJson = json.getObject("rankTitles")
                val fixedRankTitles = rawTitlesJson?.entries?.mapNotNull { (k, v) ->
                    k.toIntOrNull()?.let { elo ->
                        val encoded = v.toString().trim('"')
                        elo to decodeUnicode(encoded)
                    }
                }?.toMap() ?: emptyMap()

                // 解析 rankRewards
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

                // 解析 bannedCarriedItems
                val rawBannedCarriedItems = json.get("bannedCarriedItems") as? blue.endless.jankson.JsonArray
                val fixedBannedCarriedItems = rawBannedCarriedItems
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedCarriedItems

                // 解析 bannedHeldItems
                val rawBannedHeldItems = json.get("bannedHeldItems") as? blue.endless.jankson.JsonArray
                val fixedBannedHeldItems = rawBannedHeldItems
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedHeldItems

                // 解析 bannedPokemon
                val rawBannedPokemon = json.get("bannedPokemon") as? blue.endless.jankson.JsonArray
                val fixedBannedPokemon = rawBannedPokemon
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedPokemon

                // 解析 allowedFormats
                val rawAllowedFormats = json.get("allowedFormats") as? blue.endless.jankson.JsonArray
                val fixedAllowedFormats = rawAllowedFormats
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.allowedFormats

                // 解析 maxQueueTime
                val rawMaxQueueTime = json.get("maxQueueTime") as? JsonPrimitive
                val fixedMaxQueueTime = rawMaxQueueTime?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.maxQueueTime

                // 解析 maxEloMultiplier
                val rawMaxEloMultiplier = json.get("maxEloMultiplier") as? JsonPrimitive
                val fixedMaxEloMultiplier = rawMaxEloMultiplier?.toString()?.removeSurrounding("\"")?.toDoubleOrNull() ?: rawConfig.maxEloMultiplier

                // 解析 battleArenas
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

                // 解析 defaultLang
                val rawDefaultLang = json.get("defaultLang")
                val fixedDefaultLang = rawDefaultLang?.toString()?.removeSurrounding("\"") ?: rawConfig.defaultLang

                // 解析 defaultFormat
                val rawDefaultFormat = json.get("defaultFormat")
                val fixedDefaultFormat = rawDefaultFormat?.toString()?.removeSurrounding("\"") ?: rawConfig.defaultFormat

                // 解析 minTeamSize
                val rawMinTeamSize = json.get("minTeamSize")
                val fixedMinTeamSize = rawMinTeamSize?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.minTeamSize

                // 解析 maxTeamSize
                val rawMaxTeamSize = json.get("maxTeamSize")
                val fixedMaxTeamSize = rawMaxTeamSize?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.maxTeamSize

                // 解析 maxEloDiff
                val rawMaxEloDiff = json.get("maxEloDiff")
                val fixedMaxEloDiff = rawMaxEloDiff?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.maxEloDiff

                // 解析 seasonDuration
                val rawSeasonDuration = json.get("seasonDuration")
                val fixedSeasonDuration = rawSeasonDuration?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.seasonDuration

                // 解析 initialElo
                val rawInitialElo = json.get("initialElo")
                val fixedInitialElo = rawInitialElo?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.initialElo

                // 解析 eloKFactor
                val rawEloKFactor = json.get("eloKFactor")
                val fixedEloKFactor = rawEloKFactor?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.eloKFactor

                // 解析 minElo
                val rawMinElo = json.get("minElo")
                val fixedMinElo = rawMinElo?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.minElo

                // 解析 maxLevel
                val rawMaxLevel = json.get("maxLevel")
                val fixedMaxLevel = rawMaxLevel?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: rawConfig.maxLevel

                // 解析 allowDuplicateSpecies
                val rawAllowDuplicateSpecies = json.get("allowDuplicateSpecies")
                val fixedAllowDuplicateSpecies = rawAllowDuplicateSpecies?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.allowDuplicateSpecies

                // 解析 rankRequirements
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

                // 解析 bannedMoves
                val rawBannedMoves = json.get("bannedMoves") as? blue.endless.jankson.JsonArray
                val fixedBannedMoves = rawBannedMoves
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedMoves

                // 解析 bannedNatures
                val rawBannedNatures = json.get("bannedNatures") as? blue.endless.jankson.JsonArray
                val fixedBannedNatures = rawBannedNatures
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedCarriedItems

                // 解析 bannedGenders
                val rawBannedGenders = json.get("bannedGenders") as? blue.endless.jankson.JsonArray
                val fixedBannedGenders = rawBannedGenders
                    ?.mapNotNull { it as? blue.endless.jankson.JsonPrimitive }
                    ?.map { it.value as String }
                    ?: rawConfig.bannedGenders

                // 解析 bannedShiny
                val rawBannedShiny = json.get("bannedShiny")
                val fixedBannedShiny = rawBannedShiny?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull() ?: rawConfig.bannedShiny


                // 替换字段并返回配置对象
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
                    rankRequirements = fixedRankRequirements,
                    bannedMoves = fixedBannedMoves,
                    bannedNatures = fixedBannedNatures,
                    bannedGenders = fixedBannedGenders,
                    bannedShiny = fixedBannedShiny
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
        Files.writeString(path, json, StandardCharsets.UTF_8)
    }

    fun reload(): RankConfig {
        val newConfig = load()
        CobblemonRanked.config = newConfig

        CobblemonRanked.matchmakingQueue.reloadConfig(newConfig)

        return newConfig
    }
}
