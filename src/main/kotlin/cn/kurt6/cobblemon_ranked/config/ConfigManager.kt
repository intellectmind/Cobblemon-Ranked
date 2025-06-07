// ConfigManager.kt
package cn.kurt6.cobblemon_ranked.config

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object ConfigManager {
    private val path: Path = CobblemonRanked.dataPath.resolve("cobblemon_ranked.json")
    private val jankson = blue.endless.jankson.Jankson.builder().build()

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
                        elo to v.toString().trim('"')
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

                // 替换字段并返回配置对象
                rawConfig.copy(
                    rankTitles = fixedRankTitles,
                    rankRewards = fixedRankRewards
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
