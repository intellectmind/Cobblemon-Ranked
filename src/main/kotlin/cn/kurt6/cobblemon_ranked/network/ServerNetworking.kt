// ServerNetworking.kt
package cn.kurt6.cobblemon_ranked.network

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.config
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.data.RankDao
import cn.kurt6.cobblemon_ranked.util.RankUtils
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.io.File

class ServerNetworking {
    companion object {
        private val dao = RankDao(File("config/cobblemon_ranked/ranked.db"))

        fun handle(payload: RequestPlayerRankPayload, context: ServerPlayNetworking.Context) {
            val player = context.player()

            when (payload.type) {
                RequestType.PLAYER -> handlePlayerRequest(player, payload.format)
                RequestType.SEASON -> handleSeasonRequest(player)
                RequestType.LEADERBOARD -> handleLeaderboardRequest(player, payload.format, payload.extra)
            }
        }

        private fun handlePlayerRequest(player: ServerPlayerEntity, format: String) {
            val seasonId = dao.getLastSeasonInfo()?.seasonId ?: 1
            val data = dao.getPlayerData(player.uuid, seasonId, format)
            val lang = config.defaultLang

            if (data != null) {
                val fullList = dao.getLeaderboard(seasonId, format, limit = Int.MAX_VALUE)
                val rankIndex = fullList.indexOfFirst { it.playerId == player.uuid }

                val response = PlayerRankDataPayload(
                    playerName = data.playerName,
                    format = format,
                    seasonId = seasonId,
                    elo = data.elo,
                    wins = data.wins,
                    losses = data.losses,
                    winStreak = data.winStreak,
                    bestWinStreak = data.bestWinStreak,
                    fleeCount = data.fleeCount,
                    rankTitle = data.getRankTitle(),
                    globalRank = if (rankIndex != -1) rankIndex + 1 else null
                )

                ServerPlayNetworking.send(player, response)
            } else {
                RankUtils.sendMessage(player, MessageConfig.get("rank.not_found", lang))
            }
        }

        private fun handleSeasonRequest(player: ServerPlayerEntity) {
            val season = dao.getLastSeasonInfo()
            if (season != null) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                val startTime = java.time.LocalDateTime.parse(season.startDate, formatter)
                val endTime = java.time.LocalDateTime.parse(season.endDate, formatter)
                val now = java.time.LocalDateTime.now()

                val totalDurationDays = java.time.Duration.between(startTime, endTime).toDays()
                val remainingDuration = java.time.Duration.between(now, endTime).coerceAtLeast(java.time.Duration.ZERO)

                val days = remainingDuration.toDays()
                val hours = remainingDuration.minusDays(days).toHours()
                val minutes = remainingDuration.minusDays(days).minusHours(hours).toMinutes()

                val remainTime = "${days}d ${hours}h ${minutes}m"
                val participantCount = dao.getParticipationCount(season.seasonId)

                val lang = config.defaultLang
                val text = MessageConfig.get("season.info2", lang,
                        "season" to season.seasonId,
                        "start" to season.startDate,
                        "end" to season.endDate,
                        "duration" to totalDurationDays,
                        "remaining" to remainTime,
                        "players" to participantCount
                    )

                ServerPlayNetworking.send(player, SeasonInfoTextPayload(text))
            } else {
                val lang = config.defaultLang
                ServerPlayNetworking.send(player, SeasonInfoTextPayload(MessageConfig.get("season.not_found", lang)))
            }
        }

        private fun handleLeaderboardRequest(player: ServerPlayerEntity, format: String, pageStr: String?) {
            val page = pageStr?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val seasonId = dao.getLastSeasonInfo()?.seasonId ?: 1

            val fullList = dao.getLeaderboard(seasonId, format)
            val pageSize = 10
            val fromIndex = (page - 1) * pageSize
            val toIndex = (fromIndex + pageSize).coerceAtMost(fullList.size)

            val currentPageList = if (fromIndex >= fullList.size) emptyList() else fullList.subList(fromIndex, toIndex)

            val lang = config.defaultLang

            val leaderboardText = buildString {
                append(MessageConfig.get("leaderboard.header", lang, "page" to page, "format" to format))

                currentPageList.forEachIndexed { index, data ->
                    val rank = fromIndex + index + 1
                    append(
                        MessageConfig.get("leaderboard.entry", lang,
                            "rank" to rank,
                            "name" to data.playerName,
                            "elo" to data.elo,
                            "wins" to data.wins,
                            "losses" to data.losses,
                            "flees" to data.fleeCount)
                    )
                }

                if (currentPageList.isEmpty()) {
                    append(MessageConfig.get("leaderboard.empty", lang))
                }
            }

            ServerPlayNetworking.send(player, LeaderboardPayload(leaderboardText, page))
        }
    }
}