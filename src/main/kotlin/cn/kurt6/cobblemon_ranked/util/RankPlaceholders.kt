package cn.kurt6.cobblemon_ranked.util

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import eu.pb4.placeholders.api.PlaceholderResult
import eu.pb4.placeholders.api.Placeholders
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Identifier

object RankPlaceholders {
    fun register() {
        if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            CobblemonRanked.logger.info("Registering PlaceholderAPI support for Cobblemon Ranked")

            // 通用 Elo (默认 singles)
            Placeholders.register(Identifier("cobblemon_ranked", "elo")) { ctx, _ ->
                val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
                val seasonId = CobblemonRanked.seasonManager.currentSeasonId
                val data = CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, "singles")
                PlaceholderResult.value(data?.elo?.toString() ?: CobblemonRanked.config.initialElo.toString())
            }

            // 指定模式 Elo
            Placeholders.register(Identifier("cobblemon_ranked", "elo_singles")) { ctx, _ ->
                val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
                val seasonId = CobblemonRanked.seasonManager.currentSeasonId
                val data = CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, "singles")
                PlaceholderResult.value(data?.elo?.toString() ?: CobblemonRanked.config.initialElo.toString())
            }

            Placeholders.register(Identifier("cobblemon_ranked", "elo_doubles")) { ctx, _ ->
                val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
                val seasonId = CobblemonRanked.seasonManager.currentSeasonId
                val data = CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, "doubles")
                PlaceholderResult.value(data?.elo?.toString() ?: CobblemonRanked.config.initialElo.toString())
            }

            // 段位称号 (默认 singles)
            Placeholders.register(Identifier("cobblemon_ranked", "rank_title")) { ctx, _ ->
                val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
                val seasonId = CobblemonRanked.seasonManager.currentSeasonId
                val data = CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, "singles")
                PlaceholderResult.value(data?.getRankTitle() ?: "Unranked")
            }

            // 胜率 (默认 singles)
            Placeholders.register(Identifier("cobblemon_ranked", "win_rate")) { ctx, _ ->
                val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
                val seasonId = CobblemonRanked.seasonManager.currentSeasonId
                val data = CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, "singles")
                PlaceholderResult.value(String.format("%.1f%%", data?.winRate ?: 0.0))
            }

            // 胜场
            Placeholders.register(Identifier("cobblemon_ranked", "wins")) { ctx, _ ->
                val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
                val seasonId = CobblemonRanked.seasonManager.currentSeasonId
                val data = CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, "singles")
                PlaceholderResult.value(data?.wins?.toString() ?: "0")
            }

            // 负场
            Placeholders.register(Identifier("cobblemon_ranked", "losses")) { ctx, _ ->
                val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
                val seasonId = CobblemonRanked.seasonManager.currentSeasonId
                val data = CobblemonRanked.rankDao.getPlayerData(player.uuid, seasonId, "singles")
                PlaceholderResult.value(data?.losses?.toString() ?: "0")
            }
        }
    }
}