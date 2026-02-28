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

        val formats = listOf("singles", "doubles", "2v2singles")

        formats.forEach { format ->
            // ELO 分数 - %cobblemon_ranked:elo_singles%
            registerSimplePapi("elo_$format", CobblemonRanked.config.initialElo.toString(), format) {
                it.elo.toString()
            }

            // 段位称号 - %cobblemon_ranked:rank_title_singles%
            registerSimplePapi("rank_title_$format", "Unranked", format) {
                it.getRankTitle()
            }

            // 胜率 - %cobblemon_ranked:win_rate_singles%
            registerSimplePapi("win_rate_$format", "0.0%", format) {
                String.format("%.1f%%", it.winRate)
            }

            // 胜场 - %cobblemon_ranked:wins_singles%
            registerSimplePapi("wins_$format", "0", format) {
                it.wins.toString()
            }

            // 负场 - %cobblemon_ranked:losses_singles%
            registerSimplePapi("losses_$format", "0", format) {
                it.losses.toString()
            }

            // 总场次 - %cobblemon_ranked:total_games_singles%
            registerSimplePapi("total_games_$format", "0", format) {
                (it.wins + it.losses).toString()
            }

            // 当前连胜 - %cobblemon_ranked:streak_singles%
            registerSimplePapi("streak_$format", "0", format) {
                it.winStreak.toString()
            }

            // 最佳连胜 - %cobblemon_ranked:best_streak_singles%
            registerSimplePapi("best_streak_$format", "0", format) {
                it.bestWinStreak.toString()
            }

            // 逃跑次数 - %cobblemon_ranked:flee_count_singles%
            registerSimplePapi("flee_count_$format", "0", format) {
                it.fleeCount.toString()
            }
        }

        registerSimplePapi("elo", CobblemonRanked.config.initialElo.toString(), CobblemonRanked.config.defaultFormat) { it.elo.toString() }
        registerSimplePapi("rank_title", "Unranked", CobblemonRanked.config.defaultFormat) { it.getRankTitle() }
        registerSimplePapi("win_rate", "0.0%", CobblemonRanked.config.defaultFormat) { String.format("%.1f%%", it.winRate) }
        registerSimplePapi("wins", "0", CobblemonRanked.config.defaultFormat) { it.wins.toString() }
        registerSimplePapi("losses", "0", CobblemonRanked.config.defaultFormat) { it.losses.toString() }
        registerSimplePapi("total_games", "0", CobblemonRanked.config.defaultFormat) { (it.wins + it.losses).toString() }
        registerSimplePapi("streak", "0", CobblemonRanked.config.defaultFormat) { it.winStreak.toString() }
        registerSimplePapi("best_streak", "0", CobblemonRanked.config.defaultFormat) { it.bestWinStreak.toString() }
        registerSimplePapi("flee_count", "0", CobblemonRanked.config.defaultFormat) { it.fleeCount.toString() }

        // 排名 - %cobblemon_ranked:rank% 或 %cobblemon_ranked:rank_singles%
        registerRankPlaceholder()

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

        // 下一段位所需 ELO - %cobblemon_ranked:next_rank_elo% 或 %cobblemon_ranked:next_rank_elo_singles%
        registerNextRankPlaceholder()

        // 下一段位名称 - %cobblemon_ranked:next_rank_name% 或 %cobblemon_ranked:next_rank_name_singles%
        registerNextRankNamePlaceholder()

        // 排队状态 - %cobblemon_ranked:queue_status%
        Placeholders.register(Identifier.of("cobblemon_ranked", "queue_status")) { ctx, _ ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")

            val in1v1 = CobblemonRanked.matchmakingQueue.getPlayer(player.uuid, "singles") != null
            val in2v2 = CobblemonRanked.matchmakingQueue.getPlayer(player.uuid, "doubles") != null
            val inDuo = DuoMatchmakingQueue.isInQueue(player.uuid)

            val status = when {
                inDuo -> "2v2Singles Queue"
                in2v2 -> "Doubles Queue"
                in1v1 -> "Singles Queue"
                else -> "Not in Queue"
            }

            PlaceholderResult.value(status)
        }

        val totalPlaceholders = 14 + formats.size * 9
        CobblemonRanked.logger.info("Successfully registered $totalPlaceholders placeholders for Cobblemon Ranked")
    }

    private fun registerSimplePapi(
        key: String,
        defaultVal: String,
        format: String,
        extractor: (PlayerRankData) -> String
    ) {
        Placeholders.register(Identifier.of("cobblemon_ranked", key)) { ctx, _ ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")

            val seasonId = CobblemonRanked.seasonManager.currentSeasonId

            try {
                val data = CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, format)

                if (data == null) {
                    when {
                        key.startsWith("elo") -> PlaceholderResult.value(CobblemonRanked.config.initialElo.toString())
                        key.startsWith("rank_title") -> PlaceholderResult.value("Unranked")
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

    private fun registerRankPlaceholder() {
        Placeholders.register(Identifier.of("cobblemon_ranked", "rank")) { ctx, _ ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
            val format = CobblemonRanked.config.defaultFormat
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

        val formats = listOf("singles", "doubles", "2v2singles")
        formats.forEach { format ->
            Placeholders.register(Identifier.of("cobblemon_ranked", "rank_$format")) { ctx, _ ->
                val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
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
        }
    }

    private fun registerNextRankPlaceholder() {
        Placeholders.register(Identifier.of("cobblemon_ranked", "next_rank_elo")) { ctx, _ ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
            val format = CobblemonRanked.config.defaultFormat
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

        val formats = listOf("singles", "doubles", "2v2singles")
        formats.forEach { format ->
            Placeholders.register(Identifier.of("cobblemon_ranked", "next_rank_elo_$format")) { ctx, _ ->
                val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
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
        }
    }

    private fun registerNextRankNamePlaceholder() {
        Placeholders.register(Identifier.of("cobblemon_ranked", "next_rank_name")) { ctx, _ ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
            val format = CobblemonRanked.config.defaultFormat
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

        val formats = listOf("singles", "doubles", "2v2singles")
        formats.forEach { format ->
            Placeholders.register(Identifier.of("cobblemon_ranked", "next_rank_name_$format")) { ctx, _ ->
                val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
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
        }
    }
}