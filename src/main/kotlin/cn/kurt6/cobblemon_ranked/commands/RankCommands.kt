package cn.kurt6.cobblemon_ranked.commands

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.ConfigManager
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.matchmaking.DuoMatchmakingQueue
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style

object RankCommands {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("rank")
                .then(CommandManager.literal("pokemon_usage")
                    .executes { showPokemonUsage(it, CobblemonRanked.seasonManager.currentSeasonId, 1) }
                        .then(CommandManager.argument("season", IntegerArgumentType.integer(1))
                            .executes { ctx ->
                                val season = IntegerArgumentType.getInteger(ctx, "season")
                                showPokemonUsage(ctx, season, 1)
                            }
                            .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                .executes { ctx ->
                                    val season = IntegerArgumentType.getInteger(ctx, "season")
                                    val page = IntegerArgumentType.getInteger(ctx, "page")
                                    showPokemonUsage(ctx, season, page)
                                }
                            )
                        )
                )
                .then(CommandManager.literal("gui")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showRankGui(player)
                        1
                    }
                )
                .then(CommandManager.literal("gui_top")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showTopMenu(player)
                        1
                    }
                )
                .then(CommandManager.literal("gui_info")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showInfoMenu(player)
                        1
                    }
                )
                .then(CommandManager.literal("gui_info_players")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showInfoPlayerMenu(player, 1)
                        1
                    }
                )
                .then(CommandManager.literal("gui_queue")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showQueueMenu(player)
                        1
                    }
                )
                .then(CommandManager.literal("gui_reward")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showRewardFormatMenu(player)
                        1
                    }
                )
                .then(CommandManager.literal("gui_info_format")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .then(CommandManager.argument("format", StringArgumentType.word())
                            .executes { ctx ->
                                val source = ctx.source
                                val lang = CobblemonRanked.config.defaultLang
                                val playerName = StringArgumentType.getString(ctx, "player")
                                val format = StringArgumentType.getString(ctx, "format")
                                val season = CobblemonRanked.seasonManager.currentSeasonId
                                val target = source.server.playerManager.getPlayer(playerName)
                                if (target != null) {
                                    showInfoMenuForPlayer(source.player, target, format, season)
                                } else {
                                    source.sendMessage(Text.literal(MessageConfig.get("player.not_found", lang, "player" to playerName)))
                                }
                                1
                            }
                        )
                    )
                )
                .then(CommandManager.literal("gui_myinfo")
                    .executes { ctx ->
                        val season = CobblemonRanked.seasonManager.currentSeasonId
                        val player = ctx.source.player ?: return@executes 0
                        showMyInfoMenu(player, season)
                        1
                    }
                )
                .then(CommandManager.literal("gui_reset")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showResetPlayerList(player, 1)
                        1
                    }
                )
                .then(CommandManager.literal("reset")
                    .requires { it.hasPermissionLevel(4) }
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("format", StringArgumentType.word())
                            .executes { resetPlayerRank(it) }
                        )
                    )
                )
                .then(CommandManager.literal("reload")
                    .requires { source -> source.hasPermissionLevel(4) }
                    .executes {
                        val newConfig = ConfigManager.reload()
                        val lang = newConfig.defaultLang

                        it.source.sendMessage(Text.literal(MessageConfig.get("config.reloaded", lang)))
                        1
                    }
                )
                .then(CommandManager.literal("queue")
                    .then(CommandManager.literal("join")
                        .executes { joinQueue(it, null) }
                        .then(CommandManager.argument("format", StringArgumentType.word())
                            .executes { ctx ->
                                val format = StringArgumentType.getString(ctx, "format")
                                if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                joinQueue(ctx, format)
                            }
                        )
                    )
                    .then(CommandManager.literal("leave")
                        .executes {
                            val player = it.source.player ?: return@executes 0
                            CobblemonRanked.matchmakingQueue.removePlayer(player.uuid)
                            DuoMatchmakingQueue.removePlayer(player)
                            val lang = CobblemonRanked.config.defaultLang
                            player.sendMessage(Text.literal(MessageConfig.get("queue.leave", lang)))
                            1
                        }
                    )
                )
                .then(CommandManager.literal("info")
                    .then(CommandManager.argument("format", StringArgumentType.word())
                        .then(CommandManager.argument("season", IntegerArgumentType.integer(1))
                            .executes { ctx ->
                                val player = ctx.source.player ?: return@executes 0
                                val format = StringArgumentType.getString(ctx, "format")
                                if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                val season = IntegerArgumentType.getInteger(ctx, "season")
                                showPlayerRank(ctx, player, format, season)
                            }
                        )
                    )
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("format", StringArgumentType.word())
                            .executes { ctx ->
                                val player = EntityArgumentType.getPlayer(ctx, "player")
                                val format = StringArgumentType.getString(ctx, "format")
                                if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                val season = CobblemonRanked.seasonManager.currentSeasonId
                                showPlayerRank(ctx, player, format, season)
                            }
                            .then(CommandManager.argument("season", IntegerArgumentType.integer(1))
                                .executes { ctx ->
                                    val player = EntityArgumentType.getPlayer(ctx, "player")
                                    val format = StringArgumentType.getString(ctx, "format")
                                    if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                    val season = IntegerArgumentType.getInteger(ctx, "season")
                                    showPlayerRank(ctx, player, format, season)
                                }
                            )
                        )
                    )
                )
                .then(CommandManager.literal("top")
                    .executes { ctx ->
                        val format = CobblemonRanked.config.defaultFormat
                        val season = CobblemonRanked.seasonManager.currentSeasonId
                        showLeaderboard(ctx, format, season, 1, 10)
                    }
                )
                .then(CommandManager.literal("top")
                    .then(CommandManager.argument("format", StringArgumentType.word())
                        .executes { ctx ->
                            val format = StringArgumentType.getString(ctx, "format")
                            if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                            val season = CobblemonRanked.seasonManager.currentSeasonId
                            showLeaderboard(ctx, format, season, 1, 10)
                        }
                        .then(CommandManager.argument("season", IntegerArgumentType.integer(1))
                            .executes { ctx ->
                                val format = StringArgumentType.getString(ctx, "format")
                                if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                val season = IntegerArgumentType.getInteger(ctx, "season")
                                showLeaderboard(ctx, format, season, 1, 10)
                            }
                            .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                .executes { ctx ->
                                    val format = StringArgumentType.getString(ctx, "format")
                                    if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                    val season = IntegerArgumentType.getInteger(ctx, "season")
                                    val page = IntegerArgumentType.getInteger(ctx, "page")
                                    showLeaderboard(ctx, format, season, page, 10)
                                }
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 100))
                                    .executes { ctx ->
                                        val format = StringArgumentType.getString(ctx, "format")
                                        if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                        val season = IntegerArgumentType.getInteger(ctx, "season")
                                        val page = IntegerArgumentType.getInteger(ctx, "page")
                                        val count = IntegerArgumentType.getInteger(ctx, "count")
                                        showLeaderboard(ctx, format, season, page, count)
                                    }
                                )
                            )
                        )
                    )
                )
                .then(CommandManager.literal("season")
                    .executes { showSeasonInfo(it) }
                    .then(CommandManager.literal("end")
                        .requires { source -> source.hasPermissionLevel(4) }
                        .executes { endSeason(it) }
                    )
                )
                .then(CommandManager.literal("reward")
                    .requires { source -> source.hasPermissionLevel(4) }
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("format", StringArgumentType.word())
                            .then(CommandManager.argument("rank", StringArgumentType.greedyString())
                                .suggests { context, builder ->
                                    CobblemonRanked.config.rankTitles.values.forEach {
                                        builder.suggest(it)
                                    }
                                    builder.buildFuture()
                                }
                                .executes { grantRankReward(it) }
                            )
                        )
                    )
                )
                .then(CommandManager.literal("status")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        val lang = CobblemonRanked.config.defaultLang
                        val in1v1Queue = CobblemonRanked.matchmakingQueue.getPlayer(player.uuid, "singles") != null
                        val inFree2v2Queue = CobblemonRanked.matchmakingQueue.getPlayer(player.uuid, "doubles") != null
                        val inDuo2v2Queue = DuoMatchmakingQueue.isInQueue(player.uuid)

                        when {
                            inDuo2v2Queue -> {
                                player.sendMessage(Text.literal(MessageConfig.get("status.2v2.singles", lang)))
                            }

                            inFree2v2Queue -> {
                                player.sendMessage(Text.literal(
                                    MessageConfig.get("status.2v2.solo", lang)
                                ))
                            }

                            in1v1Queue -> {
                                player.sendMessage(Text.literal(
                                    MessageConfig.get("status.1v1", lang)
                                ))
                            }

                            else -> {
                                player.sendMessage(Text.literal(
                                    MessageConfig.get("status.none", lang)
                                ))
                            }
                        }
                        1
                    }
                )
                .then(CommandManager.literal("setseasonname")
                    .then(CommandManager.argument("seasonId", IntegerArgumentType.integer(1))
                        .then(CommandManager.argument("name", StringArgumentType.string())
                            .requires { it.hasPermissionLevel(4) }
                            .executes { ctx ->
                                val seasonId = IntegerArgumentType.getInteger(ctx, "seasonId")
                                val name = StringArgumentType.getString(ctx, "name")
                                setSeasonName(ctx.source, seasonId, name)
                                1
                            }
                        )
                    )
                )
        )
    }

    fun setSeasonName(source: ServerCommandSource, seasonId: Int, name: String) {
        val manager = CobblemonRanked.seasonManager
        val lang = CobblemonRanked.config.defaultLang

        val season = manager.rankDao.getSeasonInfo(seasonId)
            ?: run {
                source.sendMessage(Text.literal(MessageConfig.get("setSeasonName.error", lang, "seasonId" to seasonId)))
                return
            }

        manager.rankDao.saveSeasonInfo(
            seasonId = seasonId,
            startDate = season.startDate,
            endDate = season.endDate,
            ended = season.ended,
            name = name
        )

        if (seasonId == manager.currentSeasonId) {
            manager.currentSeasonName = name
        }

        source.sendMessage(Text.literal(MessageConfig.get("setSeasonName.success", lang, "seasonId" to seasonId, "name" to name)))
    }

    private fun validateFormatOrFail(format: String, source: ServerCommandSource): Boolean {
        val lang = CobblemonRanked.config.defaultLang
        if (!CobblemonRanked.config.allowedFormats.contains(format)) {
            source.sendMessage(Text.literal(MessageConfig.get("format.invalid", lang, "format" to format)))
            return false
        }
        return true
    }

    private fun grantRankReward(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val lang = CobblemonRanked.config.defaultLang
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val format = FormatArgumentType.getFormat(ctx, "format")
        val rank = StringArgumentType.getString(ctx, "rank")

        val validRanks = CobblemonRanked.config.rankTitles.values.toSet()
        val matchedRank = validRanks.firstOrNull { it.equals(rank.trim(), ignoreCase = true) }

        if (matchedRank == null) {
            source.sendMessage(Text.literal(MessageConfig.get("reward.invalid_rank", lang, "rank" to rank)))
            source.sendMessage(Text.literal(MessageConfig.get("reward.valid_ranks", lang, "ranks" to validRanks.joinToString())))
            return 0
        }

        CobblemonRanked.rewardManager.grantRankReward(player, matchedRank, format, source.server)
        source.sendMessage(Text.literal(MessageConfig.get("reward.granted_to", lang, "player" to player.name.string, "format" to format, "rank" to matchedRank)))
        return 1
    }

    private fun endSeason(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val lang = CobblemonRanked.config.defaultLang
        if (!source.hasPermissionLevel(4)) {
            source.sendMessage(Text.literal(MessageConfig.get("permission.denied", lang)))
            return 0
        }

        CobblemonRanked.seasonManager.endSeason(source.server)
        source.sendMessage(Text.literal(MessageConfig.get("season.ended", lang)))
        return 1
    }

    private fun joinQueue(ctx: CommandContext<ServerCommandSource>, format: String?): Int {
        val player = ctx.source.player ?: return 0
        val selectedFormat = format ?: CobblemonRanked.config.defaultFormat

        CobblemonRanked.matchmakingQueue.removePlayer(player.uuid)
        DuoMatchmakingQueue.removePlayer(player)

        CobblemonRanked.matchmakingQueue.addPlayer(player, selectedFormat)
        return 1
    }

    private fun showPlayerRank(
        ctx: CommandContext<ServerCommandSource>,
        player: ServerPlayerEntity,
        format: String,
        seasonId: Int
    ): Int {
        val lang = CobblemonRanked.config.defaultLang
        val dao = CobblemonRanked.rankDao
        val data = dao.getPlayerData(player.uuid, seasonId, format)

        if (data == null) {
            ctx.source.sendMessage(Text.literal(MessageConfig.get("rank.none", lang, "format" to format, "season" to seasonId.toString())))
            return 1
        }

        val rank = dao.getPlayerRank(player.uuid, seasonId, format)
        val rankString = if (rank != -1) "#$rank" else MessageConfig.get("rank.unranked", lang)

        val msg = MessageConfig.get("rank.summary", lang,
            "player" to player.name.string,
            "format" to format,
            "season" to seasonId.toString(),
            "name" to CobblemonRanked.seasonManager.currentSeasonName,
            "title" to data.getRankTitle(),
            "elo" to data.elo.toString(),
            "rank" to rankString,
            "wins" to data.wins.toString(),
            "losses" to data.losses.toString(),
            "rate" to String.format("%.2f", data.winRate),
            "streak" to data.winStreak.toString(),
            "best" to data.bestWinStreak.toString(),
            "flee" to data.fleeCount.toString()
        )

        ctx.source.sendMessage(Text.literal(msg.replace("\\n", "\n")))
        return 1
    }

    private fun showLeaderboard(ctx: CommandContext<ServerCommandSource>, format: String, seasonId: Int, page: Int, count: Int): Int {
        val lang = CobblemonRanked.config.defaultLang
        val source = ctx.source
        val dao = CobblemonRanked.rankDao
        val totalPlayers = dao.getPlayerCount(seasonId, format)

        if (totalPlayers == 0) {
            source.sendMessage(Text.literal(MessageConfig.get("leaderboard.empty", lang,
                "season" to seasonId.toString(),
                "name" to CobblemonRanked.seasonManager.currentSeasonName,
                "format" to format)))
            return 1
        }

        val pageSize = count
        val totalPages = (totalPlayers + pageSize - 1) / pageSize
        val currentPage = page.coerceIn(1, totalPages)
        val offset = (currentPage - 1).toLong() * pageSize
        val pageData = dao.getLeaderboard(seasonId, format, offset, pageSize)

        val header = MessageConfig.get("leaderboard.header", lang,
            "format" to format,
            "season" to seasonId.toString(),
            "name" to CobblemonRanked.seasonManager.currentSeasonName,
            "page" to currentPage.toString(),
            "total" to totalPages.toString()
        )
        source.sendMessage(Text.literal(header))

        pageData.forEachIndexed { index, data ->
            val rank = (offset + index + 1).toString()
            val entry = MessageConfig.get("leaderboard.entry", lang,
                "rank" to rank,
                "name" to data.playerName,
                "elo" to data.elo.toString(),
                "wins" to data.wins.toString(),
                "losses" to data.losses.toString(),
                "flee" to data.fleeCount.toString()
            )
            source.sendMessage(Text.literal(entry))
        }

        if (totalPages > 1) {
            val nav = Text.empty()
            if (currentPage > 1) {
                nav.append(link(MessageConfig.get("leaderboard.prev_page", lang), "/rank top $format $seasonId ${currentPage - 1} $count"))
            }

            if (currentPage < totalPages) {
                nav.append(Text.literal("   "))
                nav.append(link(MessageConfig.get("leaderboard.next_page", lang), "/rank top $format $seasonId ${currentPage + 1} $count"))
            }

            if (!nav.siblings.isEmpty()) {
                source.sendMessage(nav)
            }
        }

        return 1
    }

    private fun showSeasonInfo(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val lang = CobblemonRanked.config.defaultLang
        val season = CobblemonRanked.seasonManager
        val remaining = season.getRemainingTime()
        val seasonId = season.currentSeasonId

        val formats = CobblemonRanked.config.allowedFormats
        val participationByFormat = formats.associate { format ->
            val count = CobblemonRanked.rankDao.getParticipationCount(seasonId, format)
            format to count
        }

        val playersText = formats.joinToString(" ") { format ->
            val count = participationByFormat[format] ?: 0
            val formatName = when (format) {
                "singles" -> if (lang == "zh") "单打" else "Singles"
                "doubles" -> if (lang == "zh") "双打" else "Doubles"
                "2v2singles" -> if (lang == "zh") "2v2单打" else "2v2 Singles"
                else -> format
            }
            "§a$formatName: §f$count"
        }

        val message = MessageConfig.get("season.info2", lang,
            "season" to season.currentSeasonId.toString(),
            "name" to season.currentSeasonName,
            "start" to season.formatDate(season.startDate),
            "end" to season.formatDate(season.endDate),
            "duration" to CobblemonRanked.config.seasonDuration.toString(),
            "remaining" to remaining.toString(),
            "players" to playersText
        )

        source.sendMessage(Text.literal(message.replace("\\n", "\n")))
        return 1
    }

    private fun resetPlayerRank(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val lang = CobblemonRanked.config.defaultLang
        if (!source.hasPermissionLevel(4)) {
            source.sendMessage(Text.literal(MessageConfig.get("permission.denied", lang)))
            return 0
        }

        val player = EntityArgumentType.getPlayer(ctx, "player")
        val format = StringArgumentType.getString(ctx, "format")
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId

        val success = CobblemonRanked.rankDao.deletePlayerData(player.uuid, seasonId, format)

        return if (success) {
            source.sendMessage(Text.literal(MessageConfig.get("rank.reset.success", lang, "player" to player.name.string, "format" to format)))
            1
        } else {
            source.sendMessage(Text.literal(MessageConfig.get("rank.reset.fail", lang, "format" to format)))
            0
        }
    }

    private fun showRankGui(player: ServerPlayerEntity) {
        val lang = CobblemonRanked.config.defaultLang
        val isOp = player.hasPermissionLevel(4)

        player.sendMessage(Text.literal(MessageConfig.get("gui.main_title", lang)))

        val row1 = Text.empty()
            .append(link(MessageConfig.get("gui.my_info", lang), "/rank gui_myinfo"))
            .append(space())
            .append(link(MessageConfig.get("gui.season_info", lang), "/rank season"))
            .append(space())
            .append(link(MessageConfig.get("gui.rank_info", lang), "/rank gui_info_players"))
            .append(space())
            .append(link(MessageConfig.get("gui.leaderboard", lang), "/rank gui_top"))

        val row2 = Text.empty()
            .append(link(MessageConfig.get("gui.queue_join", lang), "/rank gui_queue"))
            .append(space())
            .append(link(MessageConfig.get("gui.status", lang), "/rank status"))
            .append(space())
            .append(link(MessageConfig.get("gui.queue_leave", lang), "/rank queue leave"))
            .append(space())
            .append(link(MessageConfig.get("pokemon_usage.statistics", lang), "/rank pokemon_usage"))

        val row3 = Text.empty()
            .append(link(MessageConfig.get("gui.cross_join_singles", lang), "/rank cross join singles"))
            .append(space())
            .append(link(MessageConfig.get("gui.cross_leave", lang), "/rank cross leave"))

        player.sendMessage(row1)
        player.sendMessage(row2)
        if (CobblemonRanked.config.enableCrossServer) {
            player.sendMessage(row3)
        }

        if (isOp) {
            val opRow = Text.empty()
                .append(link(MessageConfig.get("gui.op.reward", lang), "/rank gui_reward"))
                .append(space())
                .append(link(MessageConfig.get("gui.op.season_end", lang), "/rank season end"))
                .append(space())
                .append(link(MessageConfig.get("gui.op.reload", lang), "/rank reload"))
                .append(space())
                .append(link(MessageConfig.get("gui.op.reset", lang), "/rank gui_reset"))

            val opRow2 = Text.empty()
                .append(link(MessageConfig.get("gui.op.cross_start", lang), "/rank cross start"))
                .append(space())
                .append(link(MessageConfig.get("gui.op.cross_stop", lang), "/rank cross stop"))

            player.sendMessage(Text.literal(MessageConfig.get("gui.op.title", lang)))
            player.sendMessage(opRow)
            if (CobblemonRanked.config.enableCrossServer) {
                player.sendMessage(opRow2)
            }
        }
    }

    private fun showTopMenu(player: ServerPlayerEntity) {
        val lang = CobblemonRanked.config.defaultLang
        val current = CobblemonRanked.seasonManager.currentSeasonId
        player.sendMessage(Text.literal(MessageConfig.get("gui.top_title", lang)))

        for (season in current downTo maxOf(1, current - 4)) {
            val row = Text.empty()
                .append(link(MessageConfig.get("gui.top.1v1", lang, "season" to season.toString()), "/rank top singles $season"))
                .append(space())
                .append(link(MessageConfig.get("gui.top.2v2", lang, "season" to season.toString()), "/rank top doubles $season"))
                .append(space())
                .append(link(MessageConfig.get("gui.top.2v2singles", lang, "season" to season.toString()), "/rank top 2v2singles $season"))
            player.sendMessage(row)
        }
    }

    private fun showInfoMenu(player: ServerPlayerEntity) {
        val lang = CobblemonRanked.config.defaultLang
        val current = CobblemonRanked.seasonManager.currentSeasonId
        player.sendMessage(Text.literal(MessageConfig.get("gui.info_title", lang)))

        for (season in current downTo maxOf(1, current - 4)) {
            val row = Text.empty()
                .append(link(MessageConfig.get("gui.info.1v1", lang, "season" to season.toString(), "player" to player.name.string), "/rank info ${player.name.string} singles $season"))
                .append(space())
                .append(link(MessageConfig.get("gui.info.2v2", lang, "season" to season.toString(), "player" to player.name.string), "/rank info ${player.name.string} doubles $season"))
                .append(space())
                .append(link(MessageConfig.get("gui.info.2v2singles", lang, "season" to season.toString(), "player" to player.name.string), "/rank info ${player.name.string} 2v2singles $season"))
            player.sendMessage(row)
        }
    }

    private fun showQueueMenu(player: ServerPlayerEntity) {
        val lang = CobblemonRanked.config.defaultLang
        player.sendMessage(Text.literal(MessageConfig.get("gui.queue_title", lang)))
        player.sendMessage(
            Text.empty()
                .append(link(MessageConfig.get("gui.queue.1v1", lang), "/rank queue join singles"))
                .append(space())
                .append(link(MessageConfig.get("gui.queue.2v2", lang), "/rank queue join doubles"))
                .append(space())
                .append(link(MessageConfig.get("gui.queue.2v2singles", lang), "/rank queue join 2v2singles"))
        )
    }

    private fun showRewardFormatMenu(player: ServerPlayerEntity) {
        val lang = CobblemonRanked.config.defaultLang
        val formats = CobblemonRanked.config.allowedFormats

        val ranks = CobblemonRanked.config.rankTitles.entries
            .sortedByDescending { it.key }

        player.sendMessage(Text.literal(MessageConfig.get("gui.reward.top", lang)))

        for (format in formats) {
            player.sendMessage(Text.literal(MessageConfig.get("gui.reward.title", lang, "format" to format)))

            for ((elo, rankName) in ranks) {
                val row = link(
                    MessageConfig.get("gui.reward.claim", lang, "rank" to rankName),
                    "/rank reward ${player.name.string} $format $rankName"
                )
                player.sendMessage(row)
            }
        }
    }

    private fun showResetPlayerList(player: ServerPlayerEntity, page: Int) {
        val lang = CobblemonRanked.config.defaultLang
        if (!player.hasPermissionLevel(4)) {
            player.sendMessage(Text.literal(MessageConfig.get("permission.denied", lang)))
            return
        }

        val allPlayers = player.server.playerManager.playerList
        val perPage = 20
        val start = (page - 1) * perPage
        val end = minOf(start + perPage, allPlayers.size)
        val totalPages = (allPlayers.size + perPage - 1) / perPage

        player.sendMessage(Text.literal(MessageConfig.get("gui.reset.title", lang, "page" to page.toString(), "total" to totalPages.toString())))

        for (i in start until end) {
            val target = allPlayers[i]
            val name = target.name.string
            player.sendMessage(Text.empty()
                .append(link(MessageConfig.get("gui.reset.1v1", lang), "/rank reset $name singles"))
                .append(space())
                .append(link(MessageConfig.get("gui.reset.2v2", lang), "/rank reset $name doubles"))
                .append(space())
                .append(link(MessageConfig.get("gui.reset.2v2singles", lang), "/rank reset $name 2v2singles"))
                .append(space())
                .append(Text.literal("§f$name"))
            )
        }

        val nav = Text.empty()
        if (page > 1) nav.append(link(MessageConfig.get("gui.invite.prev", lang), "/rank gui_reset ${page - 1}")).append(space())
        if (end < allPlayers.size) nav.append(link(MessageConfig.get("gui.invite.next", lang), "/rank gui_reset ${page + 1}"))
        if (!nav.siblings.isEmpty()) player.sendMessage(nav)

        player.sendMessage(Text.literal(MessageConfig.get("gui.reset.tip", lang)))
    }

    private fun showInfoPlayerMenu(player: ServerPlayerEntity, page: Int) {
        val lang = CobblemonRanked.config.defaultLang
        val allPlayers = player.server.playerManager.playerList
        val perPage = 20
        val start = (page - 1) * perPage
        val end = minOf(start + perPage, allPlayers.size)
        val totalPages = (allPlayers.size + perPage - 1) / perPage

        player.sendMessage(Text.literal(MessageConfig.get("gui.info_player.title", lang, "page" to page.toString(), "total" to totalPages.toString())))

        for (i in start until end) {
            val p = allPlayers[i]
            player.sendMessage(Text.empty()
                .append(link(MessageConfig.get("gui.info_player.1v1", lang), "/rank gui_info_format ${p.name.string} singles"))
                .append(space())
                .append(link(MessageConfig.get("gui.info_player.2v2", lang), "/rank gui_info_format ${p.name.string} doubles"))
                .append(space())
                .append(link(MessageConfig.get("gui.info_player.2v2singles", lang), "/rank gui_info_format ${p.name.string} 2v2singles"))
                .append(space())
                .append(Text.literal("§f${p.name.string}"))
            )
        }

        val nav = Text.empty()
        if (page > 1) nav.append(link(MessageConfig.get("gui.invite.prev", lang), "/rank gui_info_players ${page - 1}")).append(space())
        if (end < allPlayers.size) nav.append(link(MessageConfig.get("gui.invite.next", lang), "/rank gui_info_players ${page + 1}"))
        if (!nav.siblings.isEmpty()) player.sendMessage(nav)
    }

    private fun showInfoMenuForPlayer(requester: ServerPlayerEntity?, target: ServerPlayerEntity, format: String, season: Int) {
        val lang = CobblemonRanked.config.defaultLang
        requester?.sendMessage(Text.literal(MessageConfig.get("gui.info_target.title", lang, "player" to target.name.string, "format" to format)))
        for (s in season downTo maxOf(1, season - 4)) {
            val line = link(MessageConfig.get("gui.info_target.season", lang, "season" to s.toString()), "/rank info ${target.name.string} $format $s")
            requester?.sendMessage(line)
        }
    }

    private fun showMyInfoMenu(player: ServerPlayerEntity, season: Int) {
        val lang = CobblemonRanked.config.defaultLang
        for (s in season downTo maxOf(1, season - 4)) {
            val row1 = Text.empty()
                .append(MessageConfig.get("gui.info_target.season", lang, "season" to s.toString(), "name" to CobblemonRanked.seasonManager.currentSeasonName))
                .append(space())
                .append(link(MessageConfig.get("gui.myinfo.1v1", lang), "/rank info ${player.name.string} singles $s"))
                .append(space())
                .append(link(MessageConfig.get("gui.myinfo.2v2", lang), "/rank info ${player.name.string} doubles $s"))
                .append(space())
                .append(link(MessageConfig.get("gui.myinfo.2v2singles", lang), "/rank info ${player.name.string} 2v2singles $s"))
            player.sendMessage(row1)
        }
    }

    private fun link(label: String, command: String, hoverKey: String? = null): Text {
        val lang = CobblemonRanked.config.defaultLang
        val hoverText = hoverKey?.let {
            MessageConfig.get(it, lang, "command" to command)
        } ?: MessageConfig.get(
            "command.hint",
            lang,
            "command" to command
        )

        return Text.literal(label).setStyle(
            Style.EMPTY
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hoverText)))
        )
    }

    private fun showPokemonUsage(ctx: CommandContext<ServerCommandSource>, seasonId: Int, page: Int): Int {
        val source = ctx.source
        val lang = CobblemonRanked.config.defaultLang
        val dao = CobblemonRanked.rankDao

        val pageSize = 10
        val offset = (page - 1) * pageSize
        val usageList = dao.getPokemonUsage(seasonId, pageSize, offset)
        val total = dao.getTotalPokemonUsage(seasonId)
        val totalPages = (total + pageSize - 1) / pageSize

        if (usageList.isEmpty()) {
            source.sendMessage(Text.literal(MessageConfig.get("pokemon_usage.empty", lang, "season" to seasonId.toString(), "name" to CobblemonRanked.seasonManager.currentSeasonName)))
            return 1
        }

        val totalUsageCount = dao.getTotalPokemonUsageCount(seasonId)

        val header = MessageConfig.get("pokemon_usage.header", lang,
            "season" to seasonId.toString(),
            "page" to page.toString(),
            "total" to totalPages.toString(),
            "name" to CobblemonRanked.seasonManager.currentSeasonName
        )
        source.sendMessage(Text.literal(header))

        usageList.forEachIndexed { index, (species, count) ->
            val usageRate = if (totalUsageCount > 0) {
                String.format("%.2f", count.toDouble() / totalUsageCount * 100)
            } else {
                "0.00"
            }

            val entry = MessageConfig.get("pokemon_usage.entry", lang,
                "rank" to (offset + index + 1).toString(),
                "species" to species,
                "count" to count.toString(),
                "rate" to usageRate
            )
            source.sendMessage(Text.literal(entry))
        }

        val nav = Text.empty()
        if (page > 1) {
            nav.append(link(MessageConfig.get("leaderboard.prev_page", lang), "/rank pokemon_usage $seasonId ${page - 1}"))
        }
        if (page < totalPages) {
            if (nav.siblings.isNotEmpty()) nav.append(Text.literal("   "))
            nav.append(link(MessageConfig.get("leaderboard.next_page", lang), "/rank pokemon_usage $seasonId ${page + 1}"))
        }

        if (nav.siblings.isNotEmpty()) {
            source.sendMessage(nav)
        }

        return 1
    }

    private fun space(): Text = Text.literal(" ")
}