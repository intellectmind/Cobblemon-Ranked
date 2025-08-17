// CrossCommand.kt
package cn.kurt6.cobblemon_ranked.crossserver

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.crossserver.CrossServerSocket.webSocket
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import com.mojang.brigadier.arguments.IntegerArgumentType

object CrossCommand {
    fun register(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("rank").then(
            CommandManager.literal("cross").apply {
                then(
                    CommandManager.literal("join")
                        .then(CommandManager.argument("mode", StringArgumentType.word())
                            .suggests { _, builder ->
                                builder.suggest("singles")
//                                builder.suggest("doubles")
                                builder.buildFuture()
                            }
                            .executes { context ->
                                val source = context.source
                                val player = source.player ?: run {
                                    source.sendError(
                                        MessageConfig.get("command.only_player",
                                        CobblemonRanked.config.defaultLang))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                if (webSocket == null) {
                                    RankUtils.sendMessage(player, MessageConfig.get("cross.queue.not_connected", CobblemonRanked.config.defaultLang))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                // +++ 使用命令标签检查状态 +++
                                if (player.commandTags.contains("ranked_cross_in_queue") ||
                                    player.commandTags.contains("ranked_cross_in_battle1")) {
                                    source.sendError(MessageConfig.get("command.battle.in_queue_or_battle",
                                        CobblemonRanked.config.defaultLang))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                // +++ 验证是否为正版玩家 +++
                                if (!player.gameProfile.properties.containsKey("textures")) {
                                    source.sendError(MessageConfig.get("command.join.authenticated_only", CobblemonRanked.config.defaultLang))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                val mode = StringArgumentType.getString(context, "mode")
                                val team = Utils.getPokemonTeam(player)

                                if (team.isEmpty()) {
                                    source.sendError(MessageConfig.get("command.join.empty_team",
                                        CobblemonRanked.config.defaultLang))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                // +++ 检查是否有重复宝可梦 +++
                                val speciesSet = mutableSetOf<String>()
                                val duplicateSpecies = mutableListOf<String>()

                                for (pokemon in team) {
                                    val species = pokemon["species"]?.asString ?: pokemon["name"]?.asString ?: continue
                                    if (species in speciesSet) {
                                        duplicateSpecies.add(species)
                                    } else {
                                        speciesSet.add(species)
                                    }
                                }

                                if (duplicateSpecies.isNotEmpty()) {
                                    // 有重复宝可梦，阻止加入队列
                                    source.sendError(MessageConfig.get("command.join.duplicate_pokemon",
                                        CobblemonRanked.config.defaultLang,
                                        "species" to duplicateSpecies.joinToString(", ")
                                    ))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                CrossServerSocket.joinMatchmakingQueue(player, team, mode)
                                Command.SINGLE_SUCCESS
                            }
                        )
                )
                .then(
                    CommandManager.literal("leave")
                        .executes { context ->
                            val source = context.source
                            val player = source.player ?: run {
                                source.sendError(MessageConfig.get("command.only_player",
                                    CobblemonRanked.config.defaultLang))
                                return@executes Command.SINGLE_SUCCESS
                            }

                            if (webSocket == null) {
                                RankUtils.sendMessage(player, MessageConfig.get("cross.queue.not_connected", CobblemonRanked.config.defaultLang))
                                return@executes Command.SINGLE_SUCCESS
                            }

                            // +++ 使用命令标签检查状态 +++
                            if (!player.commandTags.contains("ranked_cross_in_queue")) {
                                source.sendError(MessageConfig.get("command.not_in_queue",
                                    CobblemonRanked.config.defaultLang))
                                return@executes Command.SINGLE_SUCCESS
                            }

                            CrossServerSocket.leaveMatchmakingQueue(player)
                            Command.SINGLE_SUCCESS
                        }
                )
                .then(
                    CommandManager.literal("start")
                        .requires { it.hasPermissionLevel(2) }
                        .executes {context ->
                            CrossServerSocket.connect(context.source)
                            context.source.sendSuccess(MessageConfig.get("command.connect.start",
                                CobblemonRanked.config.defaultLang))
                            Command.SINGLE_SUCCESS
                        }
                    )
                .then(
                    CommandManager.literal("stop")
                        .requires { it.hasPermissionLevel(2) }
                        .executes {
                            CrossServerSocket.disconnect()
                            it.source.sendSuccess(MessageConfig.get("command.connect.stop",
                                CobblemonRanked.config.defaultLang))
                            Command.SINGLE_SUCCESS
                        }
                )
                // 战斗相关指令 - 只在战斗状态下可用
                .then(
                    CommandManager.literal("battle").apply {
                        requires { source ->
                        val player = source.player
                        player != null && player.commandTags.contains("ranked_cross_in_battle1")
                        }
                        .apply {
                            then(CommandManager.literal("move")
                                .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 4))
                                    .suggests { context, builder ->
                                        val player = context.source.player ?: return@suggests builder.buildFuture()
                                        val team = Utils.getPokemonTeam(player)
                                        if (team.isEmpty()) return@suggests builder.buildFuture()

                                        val activePokemon = team[0]
                                        val moves = activePokemon.getAsJsonArray("moves")
                                        moves.forEachIndexed { index, moveElement ->
                                            val moveName = try {
                                                moveElement.asJsonObject["name"]?.asString ?: "???"
                                            } catch (e: Exception) {
                                                "???"
                                            }
                                            builder.suggest("${index + 1}", Text.literal(moveName))
                                        }
                                        builder.buildFuture()
                                    }
                                    .executes { context ->
                                        val source = context.source
                                        val player = source.player ?: run {
                                            source.sendError(MessageConfig.get("command.only_player",
                                                CobblemonRanked.config.defaultLang))
                                            return@executes Command.SINGLE_SUCCESS
                                        }

                                        // 获取 slot 参数，确保其在有效范围内
                                        val slot = IntegerArgumentType.getInteger(context, "slot")
                                        if (slot < 1 || slot > 4) {
                                            source.sendError(MessageConfig.get("command.battle.invalid_move_slot",
                                                CobblemonRanked.config.defaultLang))
                                            return@executes Command.SINGLE_SUCCESS
                                        }

                                        // 发送战斗指令，确保 slot 转换为字符串传递
                                        CrossServerSocket.sendBattleCommand(player, "move", slot.toString())
                                        Command.SINGLE_SUCCESS
                                    }
                                )
                            )
                            .then(
                                CommandManager.literal("switch")
                                    .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 6))
                                        .suggests { context, builder ->
                                            val player = context.source.player ?: return@suggests builder.buildFuture()
                                            val team = Utils.getPokemonTeam(player)
                                            if (team.isEmpty()) return@suggests builder.buildFuture()

                                            // 每个槽位的宝可梦名称
                                            team.forEachIndexed { index, pokemon ->
                                                val name = pokemon.get("name").asString
                                                builder.suggest("${index + 1}", Text.literal(name))
                                            }
                                            builder.buildFuture()
                                        }
                                        .executes { context ->
                                            val source = context.source
                                            val player = source.player ?: run {
                                                source.sendError(MessageConfig.get("command.only_player",
                                                    CobblemonRanked.config.defaultLang))
                                                return@executes Command.SINGLE_SUCCESS
                                            }

                                            val slot = IntegerArgumentType.getInteger(context, "slot")
                                            // 检查槽位是否有效
                                            if (slot < 1 || slot > 6) {
                                                source.sendError(MessageConfig.get("command.battle.invalid_switch_slot",
                                                    CobblemonRanked.config.defaultLang))
                                                return@executes Command.SINGLE_SUCCESS
                                            }

                                            // 发送战斗指令，确保 slot 转换为字符串传递
                                            CrossServerSocket.sendBattleCommand(player, "switch", slot.toString())
                                            Command.SINGLE_SUCCESS
                                        }
                                    )
                            )
                            .then(
                                CommandManager.literal("forfeit")
                                    .executes { context ->
                                        val source = context.source
                                        val player = source.player ?: run {
                                            source.sendError(MessageConfig.get("command.only_player",
                                                CobblemonRanked.config.defaultLang))
                                            return@executes Command.SINGLE_SUCCESS
                                        }

                                        CrossServerSocket.sendBattleCommand(player, "forfeit", "")
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                        }
                    }
                )

                // 聊天指令 - 只在战斗状态下可用
                .then(
                    CommandManager.literal("chat")
                        .then(CommandManager.argument("message", StringArgumentType.greedyString())
                            .executes { context ->
                                val source = context.source
                                val player = source.player ?: run {
                                    source.sendError(MessageConfig.get("command.only_player",
                                        CobblemonRanked.config.defaultLang))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                val message = StringArgumentType.getString(context, "message")
                                CrossServerSocket.sendChatMessage(player, message)
                                Command.SINGLE_SUCCESS
                            }
                        )
                )
            }
        )
    }

    // 简化消息发送
    private fun ServerCommandSource.sendSuccess(key: String, vararg params: Pair<String, Any>) {
        val lang = CobblemonRanked.config.defaultLang
        sendMessage(Text.literal(MessageConfig.get(key, lang, *params)))
    }

    private fun ServerCommandSource.sendError(key: String, vararg params: Pair<String, Any>) {
        val lang = CobblemonRanked.config.defaultLang
        sendMessage(Text.literal(MessageConfig.get(key, lang, *params))
            .styled { style -> style.withColor(0xFF5555) })
    }
}