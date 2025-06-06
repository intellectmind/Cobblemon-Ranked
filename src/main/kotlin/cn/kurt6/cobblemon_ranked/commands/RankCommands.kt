// RankCommands.kt
package cn.kurt6.cobblemon_ranked.commands

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.ConfigManager
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.matchmaking.DuoMatchmakingQueue
import com.cobblemon.mod.common.Cobblemon
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
                        ConfigManager.reload()
                        val lang = CobblemonRanked.config.defaultLang
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
                            // 省略 season 参数时使用当前赛季
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
                        // 当 season 省略时使用当前赛季
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

                        when {
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
//                .then(CommandManager.literal("duo")
//                    .then(CommandManager.literal("invite")
//                        .then(CommandManager.argument("player", EntityArgumentType.player())
//                            .executes {
//                                val sender = it.source.player ?: return@executes 0
//                                val target = EntityArgumentType.getPlayer(it, "player")
//                                val lang = CobblemonRanked.config.defaultLang
//
//                                if (sender.uuid == target.uuid) {
//                                    sender.sendMessage(Text.literal("§c不能邀请自己组队。"));
//                                    return@executes 0
//                                }
//                                if (!target.isAlive || Cobblemon.battleRegistry.getBattleByParticipatingPlayer(target) != null) {
//                                    sender.sendMessage(Text.literal("§c目标玩家正在战斗或离线，无法邀请。"));
//                                    return@executes 0
//                                }
//                                if (DuoMatchmakingQueue.getPartner(sender.uuid) == target.uuid) {
//                                    sender.sendMessage(Text.literal("§e你们已经是队友，无需重复邀请"));
//                                    return@executes 0
//                                }
//                                if (DuoMatchmakingQueue.isPending(target.uuid)) {
//                                    sender.sendMessage(Text.literal("§e该玩家已在匹配队列中，无法重复邀请"));
//                                    return@executes 0
//                                }
//
//                                DuoMatchmakingQueue.pendingInvites[target.uuid] = sender.uuid
//                                sender.sendMessage(Text.literal("§a已向 ${target.name.string} 发出组队邀请"));
//                                target.sendMessage(Text.literal("§e${sender.name.string} 邀请你组队，输入 /rank duo accept 同意"));
//                                1
//                            }
//                        )
//                    )
//                    .then(CommandManager.literal("leave")
//                        .executes {
//                            val player = it.source.player ?: return@executes 0
//                            val lang = CobblemonRanked.config.defaultLang
//                            val removed = DuoMatchmakingQueue.removePlayer(player)
//
//                            if (removed) {
//                                player.sendMessage(Text.literal("§c你已退出双人排位队列"));
//                            } else {
//                                player.sendMessage(Text.literal("§7你当前未在双人队列或队伍中"));
//                            }
//                            1
//                        }
//                    )
//                    .then(CommandManager.literal("accept")
//                        .executes {
//                            val player = it.source.player ?: return@executes 0
//                            val inviterId = DuoMatchmakingQueue.pendingInvites.remove(player.uuid)
//                            val lang = CobblemonRanked.config.defaultLang
//
//                            if (inviterId == null) {
//                                player.sendMessage(Text.literal("§c你没有待处理的邀请。")); return@executes 0
//                            }
//                            val inviter = it.source.server.playerManager.getPlayer(inviterId)
//                            if (inviter == null) {
//                                player.sendMessage(Text.literal("§c邀请者已离线。")); return@executes 0
//                            }
//
//                            val playerTeam = Cobblemon.storage.getParty(player).mapNotNull { it?.uuid }
//                            val inviterTeam = Cobblemon.storage.getParty(inviter).mapNotNull { it?.uuid }
//
//                            if (playerTeam.isEmpty() || inviterTeam.isEmpty()) {
//                                player.sendMessage(Text.literal("§c请确认你和对方都有宝可梦。"));
//                                return@executes 0
//                            }
//
//                            DuoMatchmakingQueue.joinQueue(inviter, inviterTeam)
//                            DuoMatchmakingQueue.joinQueue(player, playerTeam)
//                            DuoMatchmakingQueue.formTeam(inviter, player)
//
//                            player.sendMessage(Text.literal("§a你已接受组队邀请，与 ${inviter.name.string} 成为队友"));
//                            inviter.sendMessage(Text.literal("§a${player.name.string} 已接受你的邀请"));
//                            1
//                        }
//                    )
//                )
        )
    }

    /**
     * 验证对战模式是否有效
     * @param format 要验证的模式名称
     * @param source 命令源
     * @return 如果模式有效返回true，否则返回false并发送错误消息
     */
    private fun validateFormatOrFail(format: String, source: ServerCommandSource): Boolean {
        val lang = CobblemonRanked.config.defaultLang
        if (!CobblemonRanked.config.allowedFormats.contains(format)) {
            source.sendMessage(Text.literal(MessageConfig.get("format.invalid", lang, "format" to format)))
            return false
        }
        return true
    }

    /**
     * 发放段位奖励给指定玩家
     * @param ctx 命令上下文
     * @return 执行结果(1成功/0失败)
     */
    private fun grantRankReward(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val lang = CobblemonRanked.config.defaultLang
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val format = FormatArgumentType.getFormat(ctx, "format")
        val rank = StringArgumentType.getString(ctx, "rank")

        // 验证段位是否有效
        val validRanks = CobblemonRanked.config.rankTitles.values.toSet()
        val matchedRank = validRanks.firstOrNull { it.equals(rank.trim(), ignoreCase = true) }

        if (matchedRank == null) {
            source.sendMessage(Text.literal(MessageConfig.get("reward.invalid_rank", lang, "rank" to rank)))
            source.sendMessage(Text.literal(MessageConfig.get("reward.valid_ranks", lang, "ranks" to validRanks.joinToString())))
            return 0
        }

        // 调用奖励管理器发放奖励
        // 强制发放奖励，不记录为“已领取”
        CobblemonRanked.rewardManager.grantRankReward(player, matchedRank, format, source.server, markClaimed = false)
        source.sendMessage(Text.literal(MessageConfig.get("reward.granted_to", lang, "player" to player.name.string, "format" to format, "rank" to matchedRank)))
        return 1
    }

    /**
     * 结束当前赛季
     * @param ctx 命令上下文
     * @return 执行结果(1成功/0失败)
     */
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

    /**
     * 加入匹配队列
     * @param ctx 命令上下文
     * @param format 对战格式(可选)
     * @return 执行结果(1成功/0失败)
     */
    private fun joinQueue(ctx: CommandContext<ServerCommandSource>, format: String?): Int {
        val player = ctx.source.player ?: return 0
        val selectedFormat = format ?: CobblemonRanked.config.defaultFormat

        // 直接调用加入队列，内部会获取并验证队伍
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

        val leaderboard = dao.getLeaderboard(seasonId, format, limit = Int.MAX_VALUE)
        val rankIndex = leaderboard.indexOfFirst { it.playerId == player.uuid }
        val rankString = if (rankIndex != -1) "#${rankIndex + 1}" else MessageConfig.get("rank.unranked", lang)

        val msg = MessageConfig.get("rank.summary", lang,
            "player" to player.name.string,
            "format" to format,
            "season" to seasonId.toString(),
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
        val fullLeaderboard = CobblemonRanked.rankDao.getLeaderboard(seasonId, format)

        if (fullLeaderboard.isEmpty()) {
            source.sendMessage(Text.literal(MessageConfig.get("leaderboard.empty", lang, "season" to seasonId.toString(), "format" to format)))
            return 1
        }

        val pageSize = count
        val totalPages = (fullLeaderboard.size + pageSize - 1) / pageSize
        val currentPage = page.coerceIn(1, totalPages)
        val startIndex = (currentPage - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, fullLeaderboard.size)
        val pageData = fullLeaderboard.subList(startIndex, endIndex)

        val header = MessageConfig.get("leaderboard.header", lang,
            "format" to format,
            "season" to seasonId.toString(),
            "page" to currentPage.toString(),
            "total" to totalPages.toString()
        )
        source.sendMessage(Text.literal(header))

        for ((index, data) in pageData.withIndex()) {
            val name = data.playerName
            val rank = (startIndex + index + 1).toString()
            val entry = MessageConfig.get("leaderboard.entry", lang,
                "rank" to rank,
                "name" to name,
                "elo" to data.elo.toString(),
                "wins" to data.wins.toString(),
                "losses" to data.losses.toString(),
                "flee" to data.fleeCount.toString()
            )
            source.sendMessage(Text.literal(entry))
        }

        if (totalPages > 1) {
            val hint = MessageConfig.get("leaderboard.more_hint", lang,
                "format" to format,
                "season" to seasonId.toString()
            )
            source.sendMessage(Text.literal(hint))
        }

        return 1
    }

    private fun showSeasonInfo(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val lang = CobblemonRanked.config.defaultLang
        val season = CobblemonRanked.seasonManager
        val remaining = season.getRemainingTime()
        val participation = CobblemonRanked.rankDao.getParticipationCount(season.currentSeasonId)

        val message = MessageConfig.get("season.info", lang,
            "season" to season.currentSeasonId.toString(),
            "start" to season.formatDate(season.startDate),
            "end" to season.formatDate(season.endDate),
            "duration" to CobblemonRanked.config.seasonDuration.toString(),
            "remaining" to remaining.toString(),
            "players" to participation.toString()
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

        player.sendMessage(row1)
        player.sendMessage(row2)

        if (isOp) {
            val opRow = Text.empty()
                .append(link(MessageConfig.get("gui.op.reward", lang), "/rank gui_reward"))
                .append(space())
                .append(link(MessageConfig.get("gui.op.season_end", lang), "/rank season end"))
                .append(space())
                .append(link(MessageConfig.get("gui.op.reload", lang), "/rank reload"))
                .append(space())
                .append(link(MessageConfig.get("gui.op.reset", lang), "/rank gui_reset"))

            player.sendMessage(Text.literal(MessageConfig.get("gui.op.title", lang)))
            player.sendMessage(opRow)
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
        )
    }

    private fun showRewardFormatMenu(player: ServerPlayerEntity) {
        val lang = CobblemonRanked.config.defaultLang
        val formats = CobblemonRanked.config.allowedFormats

        val ranks = CobblemonRanked.config.parsedRankTitles.entries
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
                .append(link(MessageConfig.get("gui.myinfo.1v1", lang), "/rank info ${player.name.string} singles $s"))
                .append(space())
                .append(link(MessageConfig.get("gui.myinfo.2v2", lang), "/rank info ${player.name.string} doubles $s"))
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

    private fun space(): Text = Text.literal(" ")
}