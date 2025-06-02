// ConfigManager.kt
package cn.kurt6.cobblemon_ranked.config

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import com.google.gson.JsonParseException
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
                val config = jankson.fromJson(json, RankConfig::class.java)

                applyDefaults(config)
                save(config)
                config
            } else {
                val default = RankConfig()
                save(default)
                default
            }
        } catch (e: JsonParseException) {
            e.printStackTrace()
            RankConfig()
        }
    }

    fun save(config: RankConfig) {
        val json = jankson.toJson(config).toJson(true, true)
        Files.writeString(path, json, StandardCharsets.UTF_8)
    }

    private fun applyDefaults(config: RankConfig) {
        if (config.allowedFormats.isEmpty()) {
            config.allowedFormats = listOf("singles", "doubles")
        }

        if (config.bannedPokemon.isEmpty()) {
            config.bannedPokemon = listOf("Mewtwo", "Arceus")
        }

        // 保证奖励配置格式正确（fallback）
        if (config.rankRewards.isEmpty()) {
            config.rankRewards = mapOf(
                "singles" to mapOf("青铜" to listOf("give {player} minecraft:apple 5")),
                "doubles" to mapOf("青铜" to listOf("give {player} minecraft:bread 5"))
            )
        }
    }

    fun reload(): RankConfig {
        val newConfig = load()
        CobblemonRanked.config = newConfig

        CobblemonRanked.matchmakingQueue.reloadConfig(newConfig)

        return newConfig
    }
}
