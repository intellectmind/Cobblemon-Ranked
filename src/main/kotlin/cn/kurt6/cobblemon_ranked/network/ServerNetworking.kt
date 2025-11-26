// ServerNetworking.kt
package cn.kurt6.cobblemon_ranked.network

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.config
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.data.RankDao
import cn.kurt6.cobblemon_ranked.util.RankUtils
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import java.io.File

class ServerNetworking {
    companion object {
        private val dao = RankDao(File("config/cobblemon_ranked/ranked.db"))

        fun handle(payload: RequestPlayerRankPayload, context: ServerPlayNetworking.Context) {
            val player = context.player()

            when (payload.type) {
                RequestType.PLAYER -> handlePlayerRequest(player, payload.format)
                RequestType.SEASON -> handleSeasonRequest(player, payload.format)
                RequestType.LEADERBOARD -> handleLeaderboardRequest(player, payload.format, payload.extra)
            }
        }

        private fun handlePlayerRequest(player: ServerPlayerEntity, format: String) {
            val seasonId = dao.getLastSeasonInfo()?.seasonId ?: 1
            val data = dao.getPlayerData(player.uuid, seasonId, format)
            val lang = config.defaultLang

            if (data != null) {
                val fullList = dao.getLeaderboard(seasonId, format, offset = 0, limit = Int.MAX_VALUE)
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

        private fun handleSeasonRequest(player: ServerPlayerEntity, format: String) {
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
                val participantCount = dao.getParticipationCount(season.seasonId, format)

                val lang = config.defaultLang
                val text = MessageConfig.get("season.info2", lang,
                        "season" to season.seasonId,
                        "name" to CobblemonRanked.seasonManager.currentSeasonName,
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

            // 每页显示10条记录
            val pageSize = 10
            val offset = (page - 1) * pageSize

            // 使用分页查询获取当前页的数据
            val currentPageList = dao.getLeaderboard(seasonId, format, offset.toLong(), pageSize)

            // 获取总记录数
            val totalPlayers = dao.getPlayerCount(seasonId, format)
            val totalPages = (totalPlayers + pageSize - 1) / pageSize

            val lang = config.defaultLang

            val leaderboardText = buildString {
                append(MessageConfig.get("leaderboard.header", lang,
                    "page" to page,
                    "total" to totalPages,
                    "format" to format,
                    "season" to seasonId))

                currentPageList.forEachIndexed { index, data ->
                    val rank = offset + index + 1
                    append(
                        MessageConfig.get("leaderboard.entry2", lang,
                            "rank" to rank,
                            "name" to data.playerName,
                            "elo" to data.elo,
                            "wins" to data.wins,
                            "losses" to data.losses,
                            "flees" to data.fleeCount)
                    )
                    append("\n") // 添加换行符
                }

                if (currentPageList.isEmpty()) {
                    append(MessageConfig.get("leaderboard.empty", lang))
                }
            }

            ServerPlayNetworking.send(player, LeaderboardPayload(leaderboardText, page))
        }
    }
}