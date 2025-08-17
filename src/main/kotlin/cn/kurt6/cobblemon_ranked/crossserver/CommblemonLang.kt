package cn.kurt6.cobblemon_ranked.crossserver

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader

object CommblemonLang {
    private val messagesCache = mutableMapOf<String, Map<String, String>>()
    private val jsonParser = JsonParser()

    fun get(key: String, lang: String, vararg params: Pair<String, Any>): String {
        val normalizedLang = when (lang.lowercase()) {
            "zh", "zh_cn" -> "zh"
            else -> "en"
        }

        val messages = messagesCache.getOrPut(normalizedLang) {
            loadLanguageFile(normalizedLang)
        }

        var template = messages[key] ?: key
        params.forEach { (param, value) ->
            template = template.replace("{$param}", value.toString())
        }
        return template
    }

    private fun loadLanguageFile(lang: String): Map<String, String> {
        val resource = "/lang/$lang.json"
        return CommblemonLang::class.java.getResourceAsStream(resource)?.use { inputStream ->
            InputStreamReader(inputStream, "UTF-8").use { reader ->
                // +++ 修复：手动解析JSON并处理重复键 +++
                val jsonElement = jsonParser.parse(reader)
                if (!jsonElement.isJsonObject) {
                    CobblemonRanked.logger.error("Language file is not a JSON object")
                    return emptyMap()
                }

                val jsonObject = jsonElement.asJsonObject
                val map = mutableMapOf<String, String>()

                // 遍历所有属性，处理重复键
                jsonObject.entrySet().forEach { (key, value) ->
                    // 如果键已存在，记录警告但继续处理
                    if (map.containsKey(key)) {
                        CobblemonRanked.logger.warn(
                            "[CommblemonLang] Duplicate key found in $lang.json: '$key'. Using last occurrence."
                        )
                    }

                    // 将值转换为字符串
                    map[key] = when {
                        value.isJsonPrimitive -> value.asString
                        else -> value.toString()
                    }
                }
                map
            }
        } ?: emptyMap()
    }
}