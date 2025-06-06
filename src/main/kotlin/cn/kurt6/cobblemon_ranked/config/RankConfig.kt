// RankConfig.kt
// 配置管理
package cn.kurt6.cobblemon_ranked.config

import blue.endless.jankson.Comment
import kotlin.text.toIntOrNull

data class ArenaCoordinate(
    val x: Double,
    val y: Double,
    val z: Double
)

data class BattleArena(
    val world: String = "minecraft:overworld",
    val playerPositions: List<ArenaCoordinate> = listOf(
        ArenaCoordinate(0.0, 70.0, 0.0),
        ArenaCoordinate(10.0, 70.0, 0.0),
    )
)

data class RankConfig(
    @Comment("Obtain configuration file examples and annotation documents：https://github.com/intellectmind/Cobblemon-Ranked/blob/main/README.md")
    val configDocumentation1: String = "Please refer to the link above",
    @Comment("获取配置文件示例及注释文档：https://github.com/intellectmind/Cobblemon-Ranked/blob/main/README_zh.md")
    val configDocumentation2: String = "请查阅上方链接",
    @Comment("Default language: zh or en")
    var defaultLang: String = "en",

//    @Comment("默认的战斗模式‘singles’（单打） / Default battle format: 'singles'")
    var defaultFormat: String = "singles",

//    @Comment("宝可梦限制最少数量 / Minimum number of Pokémon allowed in a team")
    var minTeamSize: Int = 1,

//    @Comment("宝可梦限制最多数量 / Maximum number of Pokémon allowed in a team")
    var maxTeamSize: Int = 6,

//    @Comment("队伍Elo差限制 / Maximum allowed Elo difference in matchmaking")
    var maxEloDiff: Int = 200,

//    @Comment("最大匹配等待时间（秒），Elo 差距将线性放宽，从1倍放宽至最大倍率（如3倍） / Max wait time for matchmaking (seconds), Elo range linearly expands to max multiplier")
    var maxQueueTime: Int = 300,

//    @Comment("最大 Elo 匹配放宽倍率（线性放宽）/ Max Elo multiplier for matchmaking range (linear expansion)")
    var maxEloMultiplier: Double = 3.0,

//    @Comment("每个赛季的持续时间（天）/ Season duration in days")
    val seasonDuration: Int = 30,

//    @Comment("赛季初始Elo / Initial Elo at the start of the season")
    val initialElo: Int = 1000,

//    @Comment("Elo计算中的K因子 / K-factor in Elo calculation")
    val eloKFactor: Int = 32,

//    @Comment("最低Elo分数限制 / Minimum possible Elo score")
    val minElo: Int = 0,

//    @Comment("禁止使用的宝可梦 / Banned Pokémon")
    var bannedPokemon: List<String> = listOf("Mewtwo", "Arceus"),
//    @Comment("允许的战斗模式：‘singles’（单打）, ‘doubles’（双打） / Allowed battle formats: 'singles', 'doubles'")
    var allowedFormats: List<String> = listOf("singles", "doubles"),
//    @Comment("允许的宝可梦等级，0 = 无限制 / Max Pokémon level allowed (0 = no limit)")
    var maxLevel: Int = 0,
//    @Comment("允许同一个队伍中出现相同的宝可梦 / Allowed to have the same species of Pokémon in a single team")
    var allowDuplicateSpecies: Boolean = false,

//    @Comment("匹配成功后可用的战斗场地列表，支持多个场地随机挑选，每个场地需要定义 2 个传送坐标/ Available battle arenas after matchmaking, each with 2 teleport coordinates")
    var battleArenas: List<BattleArena> = listOf(
        BattleArena(
            world = "minecraft:overworld",
            playerPositions = listOf(
                ArenaCoordinate(0.0, 70.0, 0.0),
                ArenaCoordinate(10.0, 70.0, 0.0),
            )
        ),
        BattleArena(
            world = "minecraft:overworld",
            playerPositions = listOf(
                ArenaCoordinate(100.0, 65.0, 100.0),
                ArenaCoordinate(110.0, 65.0, 100.0),
            )
        )
    ),

//    @Comment("段位奖励配置，每种模式可单独配置 / Rank rewards configuration per format")
    var rankRewards: Map<String, Map<String, List<String>>> = mapOf(
        "singles" to mapOf(
            "Bronze" to listOf("give {player} minecraft:apple 5"),
            "Silver" to listOf("give {player} minecraft:golden_apple 3"),
            "Gold" to listOf("give {player} minecraft:diamond 2", "give {player} minecraft:emerald 5"),
            "Platinum" to listOf("give {player} minecraft:diamond_block 1", "effect give {player} minecraft:strength 3600 1"),
            "Diamond" to listOf("give {player} minecraft:netherite_ingot 1", "give {player} minecraft:elytra 1"),
            "Master" to listOf("give {player} minecraft:netherite_block 2", "give {player} minecraft:totem_of_undying 1", "effect give {player} minecraft:resistance 7200 2")
        ),
        "doubles" to mapOf(
            "Bronze" to listOf("give {player} minecraft:bread 5"),
            "Silver" to listOf("give {player} minecraft:gold_nugget 10"),
            "Gold" to listOf("give {player} minecraft:emerald 1"),
            "Platinum" to listOf("give {player} minecraft:golden_apple 1"),
            "Diamond" to listOf("give {player} minecraft:totem_of_undying 1"),
            "Master" to listOf("give {player} minecraft:netherite_ingot 2")
        )
    ),

//    @Comment("段位名称配置（可增减）/ Elo thresholds for rank titles (customizable)")
    var rankTitles: Map<String, String> = mapOf(
        "3500" to "Master",
        "3000" to "Diamond",
        "2500" to "Platinum",
        "2000" to "Gold",
        "1500" to "Silver",
        "0" to "Bronze"
    )
)
{
    val parsedRankTitles: Map<Int, String>
        get() = rankTitles.mapNotNull { (k, v) ->
            k.toIntOrNull()?.let { it to v }
        }.toMap()
}