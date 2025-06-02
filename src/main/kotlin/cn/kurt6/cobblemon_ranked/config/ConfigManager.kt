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
                jankson.fromJson(json, RankConfig::class.java)
            } else {
                // 如果配置文件不存在，则创建默认文件
                val default = RankConfig()
                save(default)
                default
            }
        } catch (e: Exception) {
            // 更具体的错误信息
            throw RuntimeException("Configuration file loading failed：${e.message}", e)
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
