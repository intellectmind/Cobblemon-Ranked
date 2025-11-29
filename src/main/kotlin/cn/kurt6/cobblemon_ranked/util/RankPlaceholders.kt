package cn.kurt6.cobblemon_ranked.util

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.data.PlayerRankData
import cn.kurt6.cobblemon_ranked.matchmaking.DuoMatchmakingQueue
import eu.pb4.placeholders.api.PlaceholderResult
import eu.pb4.placeholders.api.Placeholders
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Identifier

object RankPlaceholders {

    fun register() {
        if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            CobblemonRanked.logger.info("PlaceholderAPI not found, skipping placeholder registration")
            return
        }

        CobblemonRanked.logger.info("Registering PlaceholderAPI support for Cobblemon Ranked")

        // ELO 分数 - %cobblemon_ranked:elo% 或 %cobblemon_ranked:elo_singles%
        registerPapi("elo", CobblemonRanked.config.initialElo.toString()) { it.elo.toString() }

        // 段位称号 - %cobblemon_ranked:rank_title% 或 %cobblemon_ranked:rank_title_doubles%
        registerPapi("rank_title", "Unranked") { it.getRankTitle() }

        // 胜率 - %cobblemon_ranked:win_rate% 或 %cobblemon_ranked:win_rate_2v2singles%
        registerPapi("win_rate", "0.0%") { String.format("%.1f%%", it.winRate) }

        // 胜场 - %cobblemon_ranked:wins%
        registerPapi("wins", "0") { it.wins.toString() }

        // 负场 - %cobblemon_ranked:losses%
        registerPapi("losses", "0") { it.losses.toString() }

        // 总场次 - %cobblemon_ranked:total_games%
        registerPapi("total_games", "0") { (it.wins + it.losses).toString() }

        // 当前连胜 - %cobblemon_ranked:streak%
        registerPapi("streak", "0") { it.winStreak.toString() }

        // 最佳连胜 - %cobblemon_ranked:best_streak%
        registerPapi("best_streak", "0") { it.bestWinStreak.toString() }

        // 逃跑次数 - %cobblemon_ranked:flee_count%
        registerPapi("flee_count", "0") { it.fleeCount.toString() }

        // 排名 - %cobblemon_ranked:rank% 或 %cobblemon_ranked:rank_singles%
        Placeholders.register(Identifier.of("cobblemon_ranked", "rank")) { ctx, arg ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
            val format = if (arg.isNullOrBlank()) CobblemonRanked.config.defaultFormat else arg
            val seasonId = CobblemonRanked.seasonManager.currentSeasonId

            try {
                val rank = CobblemonRanked.rankDao.getPlayerRank(player.uuid, seasonId, format)
                if (rank > 0) {
                    PlaceholderResult.value("#$rank")
                } else {
                    PlaceholderResult.value("Unranked")
                }
            } catch (e: Exception) {
                PlaceholderResult.value("Error")
            }
        }

        // 赛季名称 - %cobblemon_ranked:season_name%
        Placeholders.register(Identifier.of("cobblemon_ranked", "season_name")) { _, _ ->
            val name = CobblemonRanked.seasonManager.currentSeasonName
            PlaceholderResult.value(if (name.isBlank()) "Season ${CobblemonRanked.seasonManager.currentSeasonId}" else name)
        }

        // 赛季 ID - %cobblemon_ranked:season_id%
        Placeholders.register(Identifier.of("cobblemon_ranked", "season_id")) { _, _ ->
            PlaceholderResult.value(CobblemonRanked.seasonManager.currentSeasonId.toString())
        }

        // 赛季剩余时间(天) - %cobblemon_ranked:season_days_left%
        Placeholders.register(Identifier.of("cobblemon_ranked", "season_days_left")) { _, _ ->
            val remaining = CobblemonRanked.seasonManager.getRemainingTime()
            PlaceholderResult.value(remaining.days.toString())
        }

        // 赛季剩余时间(格式化) - %cobblemon_ranked:season_time_left%
        Placeholders.register(Identifier.of("cobblemon_ranked", "season_time_left")) { _, _ ->
            val remaining = CobblemonRanked.seasonManager.getRemainingTime()
            PlaceholderResult.value("${remaining.days}d ${remaining.hours}h ${remaining.minutes}m")
        }

        // 下一段位所需 ELO - %cobblemon_ranked:next_rank_elo%
        Placeholders.register(Identifier.of("cobblemon_ranked", "next_rank_elo")) { ctx, arg ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
            val format = if (arg.isNullOrBlank()) CobblemonRanked.config.defaultFormat else arg
            val seasonId = CobblemonRanked.seasonManager.currentSeasonId

            try {
                val data = CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, format)
                val currentElo = data?.elo ?: CobblemonRanked.config.initialElo

                val nextRankElo = CobblemonRanked.config.rankTitles.keys
                    .sorted()
                    .firstOrNull { it > currentElo }

                PlaceholderResult.value(nextRankElo?.toString() ?: "MAX")
            } catch (e: Exception) {
                PlaceholderResult.value("Error")
            }
        }

        // 下一段位名称 - %cobblemon_ranked:next_rank_name%
        Placeholders.register(Identifier.of("cobblemon_ranked", "next_rank_name")) { ctx, arg ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
            val format = if (arg.isNullOrBlank()) CobblemonRanked.config.defaultFormat else arg
            val seasonId = CobblemonRanked.seasonManager.currentSeasonId

            try {
                val data = CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, format)
                val currentElo = data?.elo ?: CobblemonRanked.config.initialElo

                val nextRank = CobblemonRanked.config.rankTitles
                    .filterKeys { it > currentElo }
                    .minByOrNull { it.key }

                PlaceholderResult.value(nextRank?.value ?: "MAX RANK")
            } catch (e: Exception) {
                PlaceholderResult.value("Error")
            }
        }

        // 排队状态 - %cobblemon_ranked:queue_status%
        Placeholders.register(Identifier.of("cobblemon_ranked", "queue_status")) { ctx, _ ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")

            val in1v1 = CobblemonRanked.matchmakingQueue.getPlayer(player.uuid, "singles") != null
            val in2v2 = CobblemonRanked.matchmakingQueue.getPlayer(player.uuid, "doubles") != null
            val inDuo = DuoMatchmakingQueue.isInQueue(player.uuid)

            val status = when {
                inDuo -> "2v2 Singles Queue"
                in2v2 -> "2v2 Doubles Queue"
                in1v1 -> "1v1 Queue"
                else -> "Not in Queue"
            }

            PlaceholderResult.value(status)
        }

        CobblemonRanked.logger.info("Successfully registered ${14} placeholders for Cobblemon Ranked")
    }

    private fun registerPapi(
        key: String,
        defaultVal: String = "0",
        extractor: (PlayerRankData) -> String
    ) {
        Placeholders.register(Identifier.of("cobblemon_ranked", key)) { ctx, arg ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")

            // 参数为格式(如 singles/doubles/2v2singles)，否则使用默认格式
            val format = if (arg.isNullOrBlank()) {
                CobblemonRanked.config.defaultFormat
            } else {
                arg
            }

            val seasonId = CobblemonRanked.seasonManager.currentSeasonId

            try {
                val data = CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, format)

                if (data == null) {
                    when (key) {
                        "elo" -> PlaceholderResult.value(CobblemonRanked.config.initialElo.toString())
                        "rank_title" -> PlaceholderResult.value("Unranked")
                        else -> PlaceholderResult.value(defaultVal)
                    }
                } else {
                    PlaceholderResult.value(extractor(data))
                }
            } catch (e: Exception) {
                CobblemonRanked.logger.warn("Error getting placeholder value for $key: ${e.message}")
                PlaceholderResult.value(defaultVal)
            }
        }
    }
}