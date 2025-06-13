// RankConfig.kt
// 配置管理
package cn.kurt6.cobblemon_ranked.config

import blue.endless.jankson.Comment

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
    @Comment("Default language: zh or en")
    var defaultLang: String = "en",

    @Comment("Default battle format: 'singles' / 默认的战斗模式‘singles’（单打）")
    var defaultFormat: String = "singles",

    @Comment("Minimum number of Pokémon allowed in a team / 宝可梦限制最少数量")
    var minTeamSize: Int = 1,

    @Comment("Maximum number of Pokémon allowed in a team / 宝可梦限制最多数量")
    var maxTeamSize: Int = 6,

    @Comment("Maximum allowed Elo difference in matchmaking / 队伍Elo差限制")
    var maxEloDiff: Int = 200,

    @Comment("Max wait time for matchmaking (seconds), Elo range linearly expands to max multiplier / 最大匹配等待时间（秒），Elo 差距将线性放宽，从1倍放宽至最大倍率（如3倍）")
    var maxQueueTime: Int = 300,

    @Comment("Max Elo multiplier for matchmaking range (linear expansion) / 最大 Elo 匹配放宽倍率（线性放宽）")
    var maxEloMultiplier: Double = 3.0,

    @Comment("Season duration in days / 每个赛季的持续时间（天）")
    val seasonDuration: Int = 30,

    @Comment("Initial Elo at the start of the season / 赛季初始Elo")
    val initialElo: Int = 1000,

    @Comment("K-factor in Elo calculation / Elo计算中的K因子")
    val eloKFactor: Int = 32,

    @Comment("Minimum possible Elo score / 最低Elo分数限制")
    val minElo: Int = 0,

    @Comment("Banned Pokémon / 禁止使用的宝可梦")
    var bannedPokemon: List<String> = listOf("Mewtwo", "Arceus"),

    @Comment("Banned held items for Pokémon / 禁止宝可梦携带的道具")
    var bannedHeldItems: List<String> = listOf("cobblemon:leftovers"),

    @Comment("Banned items in player's inventory / 禁止玩家背包携带的物品")
    var bannedCarriedItems: List<String> = listOf("cobblemon:leftovers", "cobblemon:choice_band"),

    @Comment("禁止使用的技能")
    var bannedMoves: List<String> = listOf("leechseed"),

    @Comment("禁止使用的性格")
    var bannedNatures: List<String> = listOf("cobblemon:naughty"),

    @Comment("禁止使用的特性")
    var bannedGenders: List<String> = listOf("MALE"),

    @Comment("是否禁止闪光宝可梦参战")
    var bannedShiny: Boolean = false,

    @Comment("Allowed battle formats: 'singles', 'doubles', '2v2singles' / 允许的战斗模式：‘singles’（单打）, ‘doubles’（双打）, '2v2singles'（2v2单打）")
    var allowedFormats: List<String> = listOf("singles", "doubles", "2v2singles"),

    @Comment("Max Pokémon level allowed (0 = no limit) / 允许的宝可梦等级，0 = 无限制")
    var maxLevel: Int = 0,

    @Comment("Allowed to have the same species of Pokémon in a single team / 允许同一个队伍中出现相同的宝可梦")
    var allowDuplicateSpecies: Boolean = false,

    @Comment("Available battle arenas after matchmaking, each with 2 teleport coordinates / 匹配成功后可用的战斗场地列表，支持多个场地随机挑选，每个场地需要定义 2 个传送坐标")
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

    @Comment("Rank rewards configuration per format / 段位奖励配置，每种模式可单独配置 ")
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
        ),
        "2v2singles" to mapOf(
            "Bronze" to listOf("give {player} minecraft:bread 5"),
            "Silver" to listOf("give {player} minecraft:gold_nugget 10"),
            "Gold" to listOf("give {player} minecraft:emerald 1"),
            "Platinum" to listOf("give {player} minecraft:golden_apple 1"),
            "Diamond" to listOf("give {player} minecraft:totem_of_undying 1"),
            "Master" to listOf("give {player} minecraft:netherite_ingot 2")
        )
    ),

    @Comment("Elo thresholds for rank titles (customizable) / 段位名称配置（可增减）")
    var rankTitles: Map<Int, String> = mapOf(
        3500 to "Master",
        3000 to "Diamond",
        2500 to "Platinum",
        2000 to "Gold",
        1500 to "Silver",
        0 to "Bronze"
    ),

    @Comment("Minimum winning rate requirement for each rank reward / 每个段位奖励领取的最小胜率要求（0.0 ~ 1.0）")
    var rankRequirements: Map<String, Double> = mapOf(
        "Bronze" to 0.0,
        "Silver" to 0.3,
        "Gold" to 0.3,
        "Platinum" to 0.3,
        "Diamond" to 0.3,
        "Master" to 0.3
    )
)