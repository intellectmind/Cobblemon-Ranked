package cn.kurt6.cobblemon_ranked.network

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.config
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.util.RankUtils
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity

class ServerNetworking {
    companion object {
        private val dao = CobblemonRanked.rankDao

        fun handle(payload: RequestPlayerRankPayload, context: ServerPlayNetworking.Context) {
            val player = context.player()

            when (payload.type) {
                RequestType.PLAYER -> handlePlayerRequest(player, payload.format)
                RequestType.SEASON -> handleSeasonRequest(player, payload.format)
                RequestType.LEADERBOARD -> handleLeaderboardRequest(player, payload.format, payload.extra)
            }
        }

        private fun handlePlayerRequest(player: ServerPlayerEntity, format: String) {
            val seasonId = CobblemonRanked.seasonManager.currentSeasonId
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
            val season = CobblemonRanked.seasonManager
            val seasonId = season.currentSeasonId
            val remaining = season.getRemainingTime()
            val lang = config.defaultLang

            val formats = CobblemonRanked.config.allowedFormats
            val participationByFormat = formats.associate { format ->
                val count = dao.getParticipationCount(seasonId, format)
                format to count
            }

            val playersText = formats.joinToString(" ") { format ->
                val count = participationByFormat[format] ?: 0
                val formatName = when (format) {
                    "singles" -> if (lang == "zh") "单打" else "Singles"
                    "doubles" -> if (lang == "zh") "双打" else "Doubles"
                    "2v2singles" -> if (lang == "zh") "2v2单打" else "2v2Singles"
                    else -> format
                }
                "§a$formatName: §f$count"
            }

            val message = MessageConfig.get("season.info2", lang,
                "season" to seasonId.toString(),
                "name" to season.currentSeasonName,
                "start" to season.formatDate(season.startDate),
                "end" to season.formatDate(season.endDate),
                "duration" to CobblemonRanked.config.seasonDuration.toString(),
                "remaining" to remaining.toString(),
                "players" to playersText
            )

            ServerPlayNetworking.send(player, SeasonInfoTextPayload(message.replace("\\n", "\n")))
        }

        private fun handleLeaderboardRequest(player: ServerPlayerEntity, format: String, pageStr: String?) {
            val page = pageStr?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val seasonId = CobblemonRanked.seasonManager.currentSeasonId
            val lang = config.defaultLang

            val pageSize = 10
            val offset = (page - 1) * pageSize

            val currentPageList = dao.getLeaderboard(seasonId, format, offset.toLong(), pageSize)

            val totalPlayers = dao.getPlayerCount(seasonId, format)
            val totalPages = (totalPlayers + pageSize - 1) / pageSize

            val leaderboardText = buildString {
                append(MessageConfig.get("leaderboard.header", lang,
                    "page" to page.toString(),
                    "total" to totalPages.toString(),
                    "format" to format,
                    "season" to seasonId.toString(),
                    "name" to CobblemonRanked.seasonManager.currentSeasonName
                ))

                currentPageList.forEachIndexed { index, data ->
                    val rank = offset + index + 1
                    append("\n")
                    append(
                        MessageConfig.get("leaderboard.entry", lang,
                            "rank" to rank.toString(),
                            "name" to data.playerName,
                            "elo" to data.elo.toString(),
                            "wins" to data.wins.toString(),
                            "losses" to data.losses.toString(),
                            "flee" to data.fleeCount.toString()
                        )
                    )
                }

                if (currentPageList.isEmpty()) {
                    append(MessageConfig.get("leaderboard.empty", lang,
                        "season" to seasonId.toString(),
                        "name" to CobblemonRanked.seasonManager.currentSeasonName,
                        "format" to format
                    ))
                }
            }

            ServerPlayNetworking.send(player, LeaderboardPayload(leaderboardText, page))
        }
    }
}