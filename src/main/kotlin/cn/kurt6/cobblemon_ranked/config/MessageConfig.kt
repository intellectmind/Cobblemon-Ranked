package cn.kurt6.cobblemon_ranked.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import cn.kurt6.cobblemon_ranked.CobblemonRanked
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object MessageConfig {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val path: Path = Paths.get("config/cobblemon_ranked/messages.json")
    private val messages: Map<String, Map<String, String>> = loadOrCreate()

    private fun loadOrCreate(): Map<String, Map<String, String>> {
        if (!Files.exists(path)) {
            Files.createDirectories(path.parent)
            val defaultMessages = mapOf(
                // MatchmakingQueue (单人匹配)
                "queue.cooldown" to mapOf(
                    "zh" to "§c你刚刚匹配失败，请等待 {seconds} 秒后再尝试加入。",
                    "en" to "§cYou recently failed matchmaking. Please wait {seconds} seconds before trying again."
                ),
                "queue.invalid_format" to mapOf(
                    "zh" to "§禁用的战斗模式: {format}",
                    "en" to "§cBanned battle format: {format}"
                ),
                "queue.ban_format" to mapOf(
                    "zh" to "§c模式已被禁用: {format}",
                    "en" to "§cFormat is banned: {format}"
                ),
                "queue.already_in_battle" to mapOf(
                    "zh" to "§c你正在进行战斗，无法加入匹配队列",
                    "en" to "§cYou are already in a battle and cannot join the queue"
                ),
                "queue.join_success_singles" to mapOf(
                    "zh" to "§a已加入 单打 匹配队列...",
                    "en" to "§aJoined the singles matchmaking queue..."
                ),
                "queue.join_success_doubles" to mapOf(
                    "zh" to "§a已加入 双打 匹配队列...",
                    "en" to "§aJoined the doubles matchmaking queue..."
                ),
                "queue.join_success_unknown" to mapOf(
                    "zh" to "§a已加入 匹配队列（未知模式）...",
                    "en" to "§aJoined the matchmaking queue (unknown mode)..."
                ),
                "queue.empty_team" to mapOf(
                    "zh" to "§c你的队伍为空，请先准备至少一只宝可梦再点击加入匹配。",
                    "en" to "§cYour team is empty. Please prepare at least one Pokémon before joining matchmaking."
                ),
                "queue.error" to mapOf(
                    "zh" to "§c无法加入队列: {error}",
                    "en" to "§cFailed to join queue: {error}"
                ),
                "queue.leave" to mapOf(
                    "zh" to "§c已离开匹配队列",
                    "en" to "§cYou have left the matchmaking queue"
                ),
                "queue.clear" to mapOf(
                    "zh" to "§c服务器关闭，已清除匹配队列",
                    "en" to "§cServer shutting down, matchmaking queue cleared"
                ),
                "queue.team_load_fail" to mapOf(
                    "zh" to "§c无法加载宝可梦队伍，战斗无法开始",
                    "en" to "§cFailed to load Pokémon team. Battle cannot start"
                ),
                "queue.no_arena" to mapOf(
                    "zh" to "§c没有可用战斗场地（至少2个玩家位置）",
                    "en" to "§cNo available battle arenas (at least 2 player positions required)"
                ),
                "duo.already_in_queue" to mapOf(
                    "zh" to "§e你已经在匹配队列中。",
                    "en" to "§eYou are already in the matchmaking queue."
                ),
                "queue.invalid_world" to mapOf(
                    "zh" to "§c世界 ID 无效: {world}",
                    "en" to "§cInvalid world ID: {world}"
                ),
                "queue.world_load_fail" to mapOf(
                    "zh" to "§c无法加载世界: {world}",
                    "en" to "§cFailed to load world: {world}"
                ),
                "queue.match_success" to mapOf(
                    "zh" to "§e匹配成功！§7将在 §c5秒 §7后开始战斗...",
                    "en" to "§eMatch found! §7Battle starts in §c5 seconds§7..."
                ),
                "queue.cancel_team_changed" to mapOf(
                    "zh" to "§c战斗取消：队伍发生变动",
                    "en" to "§cBattle cancelled: team changed"
                ),
                "duo.disqualified" to mapOf(
                    "zh" to "§c在战斗中更换了非法队伍，判负处理。",
                    "en" to "§cDisqualified from battle: team changed"
                ),
                "queue.battle_start_fail" to mapOf(
                    "zh" to "§c创建战斗失败: {reason}",
                    "en" to "§cFailed to start battle: {reason}"
                ),
                "queue.battle_start" to mapOf(
                    "zh" to "§a战斗开始！对战: §e{opponent}",
                    "en" to "§aBattle started! Opponent: §e{opponent}"
                ),

                // DuoMatchmakingQueue (双人匹配)
                "duo.waiting_for_match" to mapOf(
                    "zh" to "§a已加入 2v2单打 匹配队列...",
                    "en" to "§aJoined the 2v2singles matchmaking queue..."
                ),
                "duo.match.announce" to mapOf(
                    "zh" to "§a配对成功！§f {t1p1} & {t1p2} §7VS§f {t2p1} & {t2p2}",
                    "en" to "§aMatch Found!§f {t1p1} & {t1p2} §7VS§f {t2p1} & {t2p2}"
                ),
                "duo.round.announce" to mapOf(
                    "zh" to "§e本轮对战：§f{p1} §7VS§f {p2}",
                    "en" to "§eThis round: §f{p1} §7VS§f {p2}"
                ),
                "duo.cooldown" to mapOf(
                    "zh" to "§c你刚刚匹配失败，请等待 {seconds} 秒后再加入队列",
                    "en" to "§cYou recently failed matchmaking. Please wait {seconds} seconds before trying again."
                ),
                "duo.in_battle" to mapOf(
                    "zh" to "§c你正在进行战斗，无法加入匹配队列",
                    "en" to "§cYou are currently in a battle and cannot join the queue."
                ),
                "duo.invalid_team_selection" to mapOf(
                    "zh" to "§c只能选择当前出战队伍（Party）中的宝可梦！",
                    "en" to "§cYou can only select Pokémon from your current party!"
                ),
                "duo.invalid_team" to mapOf(
                    "zh" to "§c队伍不符合对战规则，无法加入匹配",
                    "en" to "§cYour team does not meet the battle requirements."
                ),

                // SeasonManager
                "season.start.title" to mapOf(
                    "zh" to "§6新赛季开始!",
                    "en" to "§6New Season Started!"
                ),
                "season.start.subtitle" to mapOf(
                    "zh" to "§f赛季 #{season} {name} ({start} - {end})",
                    "en" to "§fSeason #{season} {name}  ({start} - {end})"
                ),

                // RewardManager
                "reward.not_eligible" to mapOf(
                    "zh" to "§c胜率未达要求（{rate}%），无法领取 {rank} 段位奖励。",
                    "en" to "§cYou must have at least {rate}% win rate to claim the {rank} reward."
                ),
                "reward.broadcast" to mapOf(
                    "zh" to "§6[Cobblemon Rank] §b{player} §f首次晋升到 §e{rank} §f，已发放奖励！",
                    "en" to "§6[Cobblemon Rank] §b{player} §fhas reached §e{rank} §fand received a reward!"
                ),
                "reward.not_configured" to mapOf(
                    "zh" to "§c该段位没有配置奖励！",
                    "en" to "§cNo rewards configured for this rank!"
                ),
                "reward.granted" to mapOf(
                    "zh" to "§a已为您发放 {rank} 段位奖励！",
                    "en" to "§a{rank} rank reward has been granted to you!"
                ),

                // RankCommands
                "setSeasonName.error" to mapOf(
                    "zh" to "§c找不到编号为 {seasonId} 的赛季记录。",
                    "en" to "§cCould not find season record with ID {seasonId}."
                ),
                "setSeasonName.success" to mapOf(
                    "zh" to "§a已将第 {seasonId} 赛季名称设置为：§f{name}",
                    "en" to "§aSeason name has been set to §f{name}"
                ),
                "status.2v2.singles" to mapOf(
                    "zh" to "§a你当前在 §e2v2单打 §a匹配队列中。",
                    "en" to "§aYou are currently in the §e2v2singles §amatchmaking queue."
                ),
                "command.hint" to mapOf(
                    "zh" to "§e点击执行命令: {command}",
                    "en" to "§eClick to run command: {command}"
                ),
                "config.reloaded" to mapOf(
                    "zh" to "§a配置已重载并已应用",
                    "en" to "§aConfiguration reloaded and applied"
                ),
                "season.ended" to mapOf(
                    "zh" to "§a已手动结束当前赛季并开始新赛季",
                    "en" to "§aSeason has been manually ended and a new one started"
                ),
                "format.invalid" to mapOf(
                    "zh" to "§c无效的战斗模式: {format}",
                    "en" to "§cInvalid battle format: {format}"
                ),
                "player.not_found" to mapOf(
                    "zh" to "§c未找到玩家 {player}",
                    "en" to "§cPlayer {player} not found"
                ),
                "status.1v1" to mapOf(
                    "zh" to "§a你当前在 §e单打 §a匹配队列中。",
                    "en" to "§aYou are currently in the §esingles §amatchmaking queue."
                ),
                "status.none" to mapOf(
                    "zh" to "§7你当前未在任何匹配队列中。",
                    "en" to "§7You are not in any matchmaking queue."
                ),
                "status.2v2.solo" to mapOf(
                    "zh" to "§a你当前在 §e双打 §a匹配队列中。",
                    "en" to "§aYou are currently in the §edoubles §amatchmaking queue."
                ),
                "reward.invalid_rank" to mapOf(
                    "zh" to "§c无效的段位: {rank}",
                    "en" to "§cInvalid rank: {rank}"
                ),
                "reward.valid_ranks" to mapOf(
                    "zh" to "§c可用段位: {ranks}",
                    "en" to "§cAvailable ranks: {ranks}"
                ),
                "reward.granted_to" to mapOf(
                    "zh" to "§a已为 {player} 发放 {format}:{rank} 段位奖励",
                    "en" to "§aGranted {format}:{rank} reward to {player}"
                ),
                "permission.denied" to mapOf(
                    "zh" to "§c你没有权限执行此命令",
                    "en" to "§cYou do not have permission to run this command"
                ),
                "rank.none" to mapOf(
                    "zh" to "§7该玩家在 {format} 模式下，赛季 #{season} 无排位数据",
                    "en" to "§7The player has no ranking data in {format} for season #{season}"
                ),
                "rank.unranked" to mapOf(
                    "zh" to "未上榜",
                    "en" to "Unranked"
                ),
                "rank.summary" to mapOf(
                    "zh" to "§6{player} 的 {format} 数据（赛季 #{season} {name})\\n§f段位: §e{title} §8(ELO: {elo})\\n§f全球排名: §b{rank}\\n§f战绩: §a{wins}§7/§c{losses} §8(胜率: {rate}%)\\n§f连胜: §6{streak} §8(最高: {best})\\n§f断线次数: §c{flee}",
                    "en" to "§6{player}'s {format} Stats (Season #{season} {name})\\n§fRank: §e{title} §8(ELO: {elo})\\n§fGlobal Rank: §b{rank}\\n§fRecord: §a{wins}§7/§c{losses} §8(Win Rate: {rate}%)\\n§fStreak: §6{streak} §8(Best: {best})\\n§fDisconnection: §c{flee}"
                ),"leaderboard.empty" to mapOf(
                    "zh" to "§7赛季 #{season} {name} [{format}] 暂无排位数据",
                    "en" to "§7No ranking data for season #{season} {name} [{format}]"
                ),
                "leaderboard.header" to mapOf(
                    "zh" to "§6===== [{format} - 赛季 #{season} {name}] 排行榜 ({page}/{total}) =====",
                    "en" to "§6===== [{format} - Season #{season} {name}] Leaderboard ({page}/{total}) ====="
                ),
                "leaderboard.entry" to mapOf(
                    "zh" to "§e{rank}. §f{name} §7- §6ELO: {elo} §7(战绩: §a{wins}§7/§c{losses}§7) §8断线: {flee}",
                    "en" to "§e{rank}. §f{name} §7- §6ELO: {elo} §7(W/L: §a{wins}§7/§c{losses}§7) §8Disconnection: {flee}"
                ),
                "leaderboard.more_hint" to mapOf(
                    "zh" to "§e使用 /rank top {format} {season} <页码> [数量] 查看更多",
                    "en" to "§eUse /rank top {format} {season} <page> [count] to view more"
                ),
                "season.info" to mapOf(
                    "zh" to "§6当前赛季: #{season} {name}\\n§f开始时间: §7{start}\\n§f结束时间: §7{end}\\n§f赛季时长: §e{duration}天\\n§f剩余时间: §e{remaining}\\n§f参与玩家: §a{players} 人",
                    "en" to "§6Current Season: #{season} {name}\\n§fStart: §7{start}\\n§fEnd: §7{end}\\n§fDuration: §e{duration} days\\n§fRemaining: §e{remaining}\\n§fParticipants: §a{players}"
                ),
                "rank.reset.success" to mapOf(
                    "zh" to "§a已清除 {player} 在 {format} 模式下的排位数据。",
                    "en" to "§aCleared {player}'s rank data in {format} mode."
                ),
                "rank.reset.fail" to mapOf(
                    "zh" to "§c未找到该玩家在 {format} 模式下的排位数据。",
                    "en" to "§cNo ranking data found for the player in {format} mode."
                ),
                "gui.main_title" to mapOf(
                    "zh" to "§6§l▶ Cobblemon Rank 系统 - 主菜单",
                    "en" to "§6§l▶ Cobblemon Rank System - Main Menu"
                ),
                "gui.hover.run_command" to mapOf(
                    "zh" to "§7点击运行命令: §f{command}",
                    "en" to "§7Click to run: §f{command}"
                ),
                "gui.my_info" to mapOf("zh" to "§a[我的信息]", "en" to "§a[My Info]"),
                "gui.season_info" to mapOf("zh" to "§a[赛季信息]", "en" to "§a[Season Info]"),
                "gui.rank_info" to mapOf("zh" to "§a[查看段位]", "en" to "§a[Rank Info]"),
                "gui.leaderboard" to mapOf("zh" to "§a[排行榜]", "en" to "§a[Leaderboard]"),
                "gui.queue_join" to mapOf("zh" to "§b[加入匹配]", "en" to "§b[Join Queue]"),
                "gui.status" to mapOf("zh" to "§b[匹配状态]", "en" to "§b[Match State]"),
                "gui.queue_leave" to mapOf("zh" to "§c[退出匹配]", "en" to "§c[Leave Queue]"),
                "gui.op.title" to mapOf("zh" to "§6§l管理员功能：", "en" to "§6§lAdmin Functions:"),
                "gui.op.reward" to mapOf("zh" to "§c[获取奖励]", "en" to "§c[Gain Rewards]"),
                "gui.op.season_end" to mapOf("zh" to "§c[结束赛季]", "en" to "§c[End Season]"),
                "gui.op.reload" to mapOf("zh" to "§c[重载配置]", "en" to "§c[Reload Config]"),
                "gui.op.reset" to mapOf("zh" to "§c[重置玩家]", "en" to "§c[Reset Player]"),
                "gui.top_title" to mapOf(
                    "zh" to "§6§l▶ 排行榜 - 选择赛季",
                    "en" to "§6§l▶ Leaderboard - Select Season"
                ),
                "gui.top.1v1" to mapOf(
                    "zh" to "§a[单打 赛季 #{season}]",
                    "en" to "§a[singles Season #{season}]"
                ),
                "gui.top.2v2singles" to mapOf(
                    "zh" to "§a[2v2单打 赛季 #{season}]",
                    "en" to "§a[2v2singles Season #{season}]"
                ),
                "gui.top.2v2" to mapOf(
                    "zh" to "§b[双打 赛季 #{season}]",
                    "en" to "§b[doubles Season #{season}]"
                ),
                "gui.info_title" to mapOf(
                    "zh" to "§6§l▶ 查看段位 - 选择赛季",
                    "en" to "§6§l▶ View Rank - Select Season"
                ),
                "gui.info.1v1" to mapOf(
                    "zh" to "§a[单打 赛季 #{season}]",
                    "en" to "§a[singles Season #{season}]"
                ),
                "gui.info.2v2" to mapOf(
                    "zh" to "§b[双打 赛季 #{season}]",
                    "en" to "§b[doubles Season #{season}]"
                ),
                "gui.info.2v2singles" to mapOf(
                    "zh" to "§b[2v2单打 赛季 #{season}]",
                    "en" to "§b[2v2singles Season #{season}]"
                ),
                "gui.queue_title" to mapOf(
                    "zh" to "§6§l▶ 加入匹配 - 请选择模式",
                    "en" to "§6§l▶ Join Match - Select Mode"
                ),
                "gui.queue.1v1" to mapOf("zh" to "§a[加入 单打]", "en" to "§a[Join singles]"),
                "gui.queue.2v2" to mapOf("zh" to "§b[加入 双打]", "en" to "§b[Join doubles]"),
                "gui.queue.2v2singles" to mapOf("zh" to "§a[加入 2v2单打]", "en" to "§a[Join 2v2singles]"),
                "gui.queue.leave" to mapOf("zh" to "§c[离开匹配]", "en" to "§c[Leave Match]"),
                "gui.reward.top" to mapOf(
                    "zh" to "直接发放可重复领取的奖励-用于测试",
                    "en" to "Directly distribute reusable rewards - for testing purposes"
                ),
                "gui.reward.title" to mapOf(
                    "zh" to "§6§l▶ 可领取的段位奖励 - {format}",
                    "en" to "§6§l▶ Available Rewards - {format}"
                ),
                "gui.reward.claim" to mapOf(
                    "zh" to "§a[发放 {rank} 段位奖励]",
                    "en" to "§a[Claim {rank} Reward]"
                ),
                "gui.reset.title" to mapOf(
                    "zh" to "§6§l▶ 重置玩家排位数据（第 {page} 页 / 共 {total} 页）",
                    "en" to "§6§l▶ Reset Player Rank Data (Page {page} / {total})"
                ),
                "gui.reset.1v1" to mapOf("zh" to "§a[重置 单打]", "en" to "§a[Reset singles]"),
                "gui.reset.2v2" to mapOf("zh" to "§c[重置 双打]", "en" to "§c[Reset doubles]"),
                "gui.reset.2v2singles" to mapOf("zh" to "§c[重置 2v2单打]", "en" to "§c[Reset 2v2singles]"),
                "gui.reset.tip" to mapOf(
                    "zh" to "§7点击重置按钮清除指定玩家在该模式的段位数据。",
                    "en" to "§7Click the reset button to clear a player's rank data in that mode."
                ),
                "gui.info_player.title" to mapOf(
                    "zh" to "§6§l▶ 查看段位 - 玩家选择（第 {page} 页 / 共 {total} 页）",
                    "en" to "§6§l▶ View Rank - Select Player (Page {page} / {total})"
                ),
                "gui.info_player.1v1" to mapOf("zh" to "§a[单打]", "en" to "§a[singles]"),
                "gui.info_player.2v2" to mapOf("zh" to "§b[双打]", "en" to "§b[doubles]"),
                "gui.info_player.2v2singles" to mapOf("zh" to "§a[2v2单打]", "en" to "§a[2v2singles]"),
                "gui.info_target.title" to mapOf(
                    "zh" to "§6§l▶ {player} 的段位 - {format} - 选择赛季",
                    "en" to "§6§l▶ {player}'s Rank - {format} - Select Season"
                ),
                "gui.info_target.season" to mapOf(
                    "zh" to "§a[赛季 #{season} {name}]",
                    "en" to "§a[Season #{season} {name}]"
                ),
                "gui.myinfo.1v1" to mapOf(
                    "zh" to "§b[查看单打]",
                    "en" to "§b[View Singles]"
                ),
                "gui.myinfo.2v2" to mapOf(
                    "zh" to "§a[查看双打]",
                    "en" to "§a[View Doubles]"
                ),
                "gui.myinfo.2v2singles" to mapOf(
                    "zh" to "§b[查看2v2单打]",
                    "en" to "§b[View 2v2singles]"
                ),

                // BattleHandler
                "battle.team.banned_nature" to mapOf(
                    "zh" to "队伍中有宝可梦使用了被禁止的性格: {names}",
                    "en" to "Your team contains Pokémon with banned natures: {names}"
                ),
                "battle.team.banned_gender" to mapOf(
                    "zh" to "队伍中有宝可梦使用了被禁止的特性: {names}",
                    "en" to "Your team contains Pokémon with banned genders: {names}"
                ),
                "battle.team.banned_moves" to mapOf(
                    "zh" to "队伍中有宝可梦使用了被禁止的招式: {names}",
                    "en" to "Your team contains banned moves: {names}"
                ),
                "battle.team.banned_shiny" to mapOf(
                    "zh" to "队伍中有宝可梦使用了被禁止的闪光个体: {names}",
                    "en" to "§cYour team contains shiny Pokémon: {names}"
                ),
                "battle.player.banned_items" to mapOf(
                    "zh" to "§c你的背包中含有被禁止的物品: {items}",
                    "en" to "§cYour inventory contains banned items: {items}"
                ),
                "battle.disconnect.broadcast" to mapOf(
                    "zh" to "§c玩家 {player} 断线，所在队伍判负。",
                    "en" to "§cPlayer {player} disconnected and lost. Their team was eliminated."
                ),
                "battle.flee.forbidden" to mapOf(
                    "zh" to "你不能在排位战中逃跑！",
                    "en" to "You cannot flee in a ranked battle."
                ),
                "battle.team.banned_held_items" to mapOf(
                "zh" to "队伍中有宝可梦携带了被禁止的物品: {names}",
                "en" to "Your team contains Pokémon with banned held items: {names}"
                ),
                "battle.team.banned_moves" to mapOf(
                    "zh" to "队伍中有宝可梦使用了被禁止的招式: {names}",
                    "en" to "Your team contains banned moves: {names}"
                ),
                "battle.team.banned_abilities" to mapOf(
                    "zh" to "队伍中有宝可梦使用了被禁止的特性: {names}",
                    "en" to "Your team contains Pokémon with banned abilities: {names}"
                ),
                "battle.team.banned_natures" to mapOf(
                    "zh" to "队伍中有宝可梦使用了被禁止的性格: {names}",
                    "en" to "Your team contains banned natures: {names}"
                ),
                "battle.disconnect.loser" to mapOf(
                    "zh" to "§c你断线导致失败。",
                    "en" to "§cYou disconnected and lost."
                ),
                "battle.disconnect.winner" to mapOf(
                    "zh" to "§a对手断线，你获得了胜利。",
                    "en" to "§aOpponent disconnected. You win."
                ),
                "battle.invalid_team_selection" to mapOf(
                    "zh" to "§c只能使用当前出战队伍中的宝可梦！",
                    "en" to "§cYou can only use Pokémon from your current battle team!"
                ),
                "battle.team.too_small" to mapOf(
                    "zh" to "§c队伍至少需要{min}只宝可梦",
                    "en" to "§cTeam must have at least {min} Pokémon"
                ),
                "battle.team.too_large" to mapOf(
                    "zh" to "§c队伍最多只能有{max}只宝可梦",
                    "en" to "§cTeam can have at most {max} Pokémon"
                ),
                "battle.team.banned_pokemon" to mapOf(
                    "zh" to "§c队伍包含禁用宝可梦: {names}",
                    "en" to "§cTeam contains banned Pokémon: {names}"
                ),
                "battle.team.overleveled" to mapOf(
                    "zh" to "§c宝可梦等级超过{max}级: {names}",
                    "en" to "§cPokémon exceed level {max}: {names}"
                ),
                "battle.team.duplicates" to mapOf(
                    "zh" to "§c队伍包含重复宝可梦: {names}",
                    "en" to "§cTeam contains duplicate species: {names}"
                ),
                "battle.status.egg" to mapOf("zh" to "蛋", "en" to "Egg"),
                "battle.status.fainted" to mapOf("zh" to "濒死", "en" to "Fainted"),
                "battle.status.unknown" to mapOf("zh" to "未知状态", "en" to "Unknown"),
                "battle.team.invalid" to mapOf(
                    "zh" to "§c队伍包含无效宝可梦: {entries}",
                    "en" to "§cTeam contains invalid Pokémon: {entries}"
                ),
                "battle.flee.loser" to mapOf(
                    "zh" to "§c你在战斗中逃跑，ELO 双倍扣分！当前ELO: §e{elo}",
                    "en" to "§cYou fled from battle. Double ELO penalty! Current ELO: §e{elo}"
                ),
                "battle.flee.winner" to mapOf(
                    "zh" to "§a对手逃跑，你的分数未变化。当前ELO: §e{elo}",
                    "en" to "§aOpponent fled. Your ELO remains unchanged. Current ELO: §e{elo}"
                ),
                "battle.teleport.back" to mapOf(
                    "zh" to "§a战斗结束，你已被传送回原来的位置。",
                    "en" to "§aBattle ended. You have been teleported back."
                ),
                "battle.result.header" to mapOf(
                    "zh" to "§6===== 对战结果 =====",
                    "en" to "§6===== Battle Result ====="
                ),
                "battle.result.rank" to mapOf(
                    "zh" to "§f当前段位: §b{rank}",
                    "en" to "§fCurrent Rank: §b{rank}"
                ),
                "battle.result.change" to mapOf(
                    "zh" to "§fELO变化: {change}",
                    "en" to "§fELO Change: {change}"
                ),
                "battle.result.elo" to mapOf(
                    "zh" to "§f当前ELO: §e{elo}",
                    "en" to "§fCurrent ELO: §e{elo}"
                ),
                "battle.result.record" to mapOf(
                    "zh" to "§f战绩: §a{wins}§f胜 §c{losses}§f败",
                    "en" to "§fRecord: §a{wins} Wins §c{losses} Losses"
                ),
                "reward.granted" to mapOf(
                    "zh" to "§a已为您发放 {rank} 段位奖励！",
                    "en" to "§a{rank} reward has been granted!"
                ),
                "duo.next_round.ready" to mapOf(
                    "zh" to "§e轮到你上场迎战 §c{opponent}！",
                    "en" to "§eYour turn to face §c{opponent}!"
                ),
                "duo.next_round.win_continue" to mapOf(
                    "zh" to "§a你击败了对手，继续战斗！",
                    "en" to "§aYou defeated your opponent. Continue battling!"
                ),
                "duo.next_round.start.title" to mapOf(
                    "zh" to "§a下一轮战斗开始！",
                    "en" to "§aNext Round Begins!"
                ),
                "duo.next_round.start.subtitle" to mapOf(
                    "zh" to "§f你的对手是 §e{opponent}",
                    "en" to "§fYour opponent is §e{opponent}"
                ),
                "duo.next_round.alert.title" to mapOf(
                    "zh" to "§c队友落败！",
                    "en" to "§cYour Teammate Was Defeated!"
                ),
                "duo.next_round.alert.subtitle" to mapOf(
                    "zh" to "§f你将对战 §e{opponent}",
                    "en" to "§fYou will battle §e{opponent}"
                ),
                "duo.rematch.failed" to mapOf(
                    "zh" to "§c重新对战失败: {error}",
                    "en" to "§cFailed to start rematch: {error}"
                ),
                "duo.end.victory.title" to mapOf(
                    "zh" to "§a你们赢得了胜利！",
                    "en" to "§aYou Won the Battle!"
                ),
                "duo.end.victory.subtitle" to mapOf(
                    "zh" to "§f击败了 {loser}",
                    "en" to "§fDefeated {loser}"
                ),
                "duo.end.defeat.title" to mapOf(
                    "zh" to "§c你们被击败了",
                    "en" to "§cYou Were Defeated"
                ),
                "duo.end.defeat.subtitle" to mapOf(
                    "zh" to "§f胜者是 {winner}",
                    "en" to "§fThe winners are {winner}"
                ),
                "duo.end.rank_display" to mapOf(
                    "zh" to "§7[双打] 当前段位: §e{rank} §8(ELO: {elo})",
                    "en" to "§7[doubles] Current Rank: §e{rank} §8(ELO: {elo})"
                ),
                "duo.rule" to mapOf(
                    "zh" to "§e[提示] 本次为 2v2 轮战模式：每队每次出战一人，胜者留场，败者轮换，直到全员战败！",
                    "en" to "§e[Tip] This is a 2v2 round-robin mode: each team will send one Pokémon per round, and the winner will stay in the battle, while the loser will be replaced. The battle will end when all Pokémon are defeated."
                ),

                // ServerNetworking
                "rank.not_found" to mapOf(
                    "zh" to "§c未找到您的战绩数据。",
                    "en" to "§cYour ranked data could not be found."
                ),
                "season.not_found" to mapOf(
                    "zh" to "§c未找到赛季信息。",
                    "en" to "§cNo season information found."
                ),
                "leaderboard.header" to mapOf(
                    "zh" to "§6第{page}页（模式：{format}）\n",
                    "en" to "§6Page {page} (Format: {format})\n"
                ),
                "leaderboard.entry2" to mapOf(
                    "zh" to "§e{rank}. §f{name} §7- §6ELO: {elo} §7(战绩: §a{wins}§7/§c{losses}§7) §7断线: {flees}\n",
                    "en" to "§e{rank}. §f{name} §7- §6ELO: {elo} §7(Record: §a{wins}§7/§c{losses}§7) §7Flees: {flees}\n"
                ),
                "leaderboard.empty" to mapOf(
                    "zh" to "§7暂无更多数据。",
                    "en" to "§7No more data available."
                ),
                "season.info2" to mapOf(
                    "zh" to "§6当前赛季: #{season} {name}\n§f开始时间: §7{start}\n§f结束时间: §7{end}\n§f赛季时长: §e{duration}天\n§f剩余时间: §e{remaining}\n§f参与玩家: §a{players} 人",
                    "en" to "§6Current Season: #{season} {name}\n§fStart: §7{start}\n§fEnd: §7{end}\n§fDuration: §e{duration} days\n§fRemaining: §e{remaining}\n§fParticipants: §a{players}"
                ),

                // CrossServerSocket 消息
                // 通用
                "cross.cross_server_disabled" to mapOf(
                    "zh" to "§c跨服匹配未启用",
                    "en" to "§cCross-server matching is disabled"
                ),
                "cross.unknown" to mapOf(
                    "zh" to "未知",
                    "en" to "Unknown"
                ),
                "cross.unknown_opponent" to mapOf(
                    "zh" to "未知对手",
                    "en" to "Unknown opponent"
                ),
                "cross.unknown_pokemon" to mapOf(
                    "zh" to "未知宝可梦",
                    "en" to "Unknown Pokemon"
                ),
                "cross.unknown_move" to mapOf(
                    "zh" to "未知技能",
                    "en" to "Unknown move"
                ),
                "cross.error.unknown" to mapOf(
                    "zh" to "未知错误",
                    "en" to "Unknown error"
                ),

                // 状态/方向
                "cross.status.normal" to mapOf(
                    "zh" to "正常",
                    "en" to "Normal"
                ),
                "cross.status.par" to mapOf(
                    "zh" to "麻痹",
                    "en" to "Paralyzed"
                ),
                "cross.status.brn" to mapOf(
                    "zh" to "灼伤",
                    "en" to "Burned"
                ),
                "cross.status.psn" to mapOf(
                    "zh" to "中毒",
                    "en" to "Poisoned"
                ),
                "cross.status.badpsn" to mapOf(
                    "zh" to "剧毒",
                    "en" to "Badly Poisoned"
                ),
                "cross.status.slp" to mapOf(
                    "zh" to "睡眠",
                    "en" to "Asleep"
                ),
                "cross.status.frz" to mapOf(
                    "zh" to "冰冻",
                    "en" to "Frozen"
                ),
                "cross.direction.up" to mapOf(
                    "zh" to "提升",
                    "en" to "rose"
                ),
                "cross.direction.down" to mapOf(
                    "zh" to "下降",
                    "en" to "fell"
                ),

                // 日志
                "cross.log.heartbeat_start" to mapOf(
                    "zh" to "开始心跳",
                    "en" to "Heartbeat started"
                ),
                "cross.log.heartbeat_failed" to mapOf(
                    "zh" to "发送心跳失败: {error}",
                    "en" to "Failed to send heartbeat: {error}"
                ),
                "cross.log.connected" to mapOf(
                    "zh" to "连接成功",
                    "en" to "Connected successfully"
                ),
                "cross.log.message_received" to mapOf(
                    "zh" to "收到服务器消息: {message}...",
                    "en" to "Received server message: {message}..."
                ),
                "cross.log.unknown_message_type" to mapOf(
                    "zh" to "未知消息类型: {type}",
                    "en" to "Unknown message type: {type}"
                ),
                "cross.log.parse_failed" to mapOf(
                    "zh" to "解析失败: {error} | 原始消息: {raw}",
                    "en" to "Parse failed: {error} | Raw message: {raw}"
                ),
                "cross.log.closing" to mapOf(
                    "zh" to "连接正在关闭: code={code}, reason={reason}",
                    "en" to "Connection closing: code={code}, reason={reason}"
                ),
                "cross.log.connection_failed" to mapOf(
                    "zh" to "WebSocket连接失败: {error}",
                    "en" to "WebSocket connection failed: {error}"
                ),
                "cross.log.connection_closed" to mapOf(
                    "zh" to "WebSocket连接已关闭: {code} - {reason}",
                    "en" to "WebSocket connection closed: {code} - {reason}"
                ),
                "cross.log.reconnect_attempt" to mapOf(
                    "zh" to "尝试重连 (尝试次数: {attempts})",
                    "en" to "Attempting reconnect (attempt: {attempts})"
                ),
                "cross.log.request_battle_state" to mapOf(
                    "zh" to "请求战斗状态更新: {battleId}",
                    "en" to "Requesting battle state update: {battleId}"
                ),
                "cross.log.send_battle_command" to mapOf(
                    "zh" to "发送战斗指令: battle_id = {battleId}, player_id = {playerId}, command = {command}",
                    "en" to "Sending battle command: battle_id = {battleId}, player_id = {playerId}, command = {command}"
                ),
                "cross.log.disconnected" to mapOf(
                    "zh" to "连接已断开",
                    "en" to "Disconnected"
                ),
                "cross.log.missing_opponent_team" to mapOf(
                    "zh" to "匹配成功消息中缺少对手队伍信息",
                    "en" to "Missing opponent team information in match found message"
                ),
                "cross.log.player_not_found" to mapOf(
                    "zh" to "未找到玩家 {playerId}，无法发送 Elo 更新消息",
                    "en" to "Player {playerId} not found, cannot send Elo update"
                ),
                "cross.log.auto_forfeit" to mapOf(
                    "zh" to "玩家 {player} 断线，自动发送投降指令: battle_id = {battleId}",
                    "en" to "Player {player} disconnected, auto-sending forfeit: battle_id = {battleId}"
                ),

                // 队列
                "cross.queue.already_in_queue" to mapOf(
                    "zh" to "你已在匹配队列中",
                    "en" to "You are already in the queue"
                ),
                "cross.queue.join_success" to mapOf(
                    "zh" to "你已成功加入 {mode} 匹配队列",
                    "en" to "You have joined the {mode} queue"
                ),
                "cross.queue.join_failed" to mapOf(
                    "zh" to "加入匹配失败：{error}",
                    "en" to "Failed to join queue: {error}"
                ),
                "cross.queue.leave_success" to mapOf(
                    "zh" to "你已成功离开匹配队列",
                    "en" to "You have left the queue"
                ),
                "cross.queue.leave_failed" to mapOf(
                    "zh" to "离开匹配失败：{error}",
                    "en" to "Failed to leave queue: {error}"
                ),

                // 对战
                "cross.battle.match_found" to mapOf(
                    "zh" to "===== 匹配成功! =====",
                    "en" to "===== MATCH FOUND! ====="
                ),
                "cross.battle.opponent" to mapOf(
                    "zh" to "对手: {name}",
                    "en" to "Opponent: {name}"
                ),
                "cross.battle.opponent_lead" to mapOf(
                    "zh" to "对手首发: {pokemon}",
                    "en" to "Opponent lead: {pokemon}"
                ),
                "cross.battle.your_team" to mapOf(
                    "zh" to "你的队伍:",
                    "en" to "Your team:"
                ),
                "cross.battle.pokemon_info" to mapOf(
                    "zh" to "宝可梦: {name} | HP: {hp}/{maxHp}",
                    "en" to "Pokemon: {name} | HP: {hp}/{maxHp}"
                ),
                "cross.battle.current_moves" to mapOf(
                    "zh" to "当前宝可梦技能:",
                    "en" to "Current moves:"
                ),
                "cross.battle.move_info" to mapOf(
                    "zh" to "{index}. {name} ({pp}/{pp})",
                    "en" to "{index}. {name} ({pp}/{pp})"
                ),
                "cross.battle.move_command" to mapOf(
                    "zh" to "使用技能: /rank cross battle move <技能名称或编号>",
                    "en" to "Use move: /rank cross battle move <move name or number>"
                ),
                "cross.battle.switch_command" to mapOf(
                    "zh" to "更换宝可梦: /rank cross battle switch <1-6>",
                    "en" to "Switch Pokemon: /rank cross battle switch <1-6>"
                ),
                "cross.battle.forfeit_command" to mapOf(
                    "zh" to "投降: /rank cross battle forfeit",
                    "en" to "Forfeit: /rank cross battle forfeit"
                ),
                "cross.battle.turn_start" to mapOf(
                    "zh" to "[回合 {turn}] 开始！请输入你的指令",
                    "en" to "[Turn {turn}] Begin! Please enter your command"
                ),
                "cross.battle.start" to mapOf(
                    "zh" to "===== 战斗开始! =====",
                    "en" to "===== BATTLE START! ====="
                ),
                "cross.battle.players" to mapOf(
                    "zh" to "对战双方: {player1} vs {player2}",
                    "en" to "Players: {player1} vs {player2}"
                ),
                "cross.battle.lead" to mapOf(
                    "zh" to "{player} 派出了 {pokemon}",
                    "en" to "{player} sent out {pokemon}"
                ),
                "cross.battle.move_used" to mapOf(
                    "zh" to "{playerName} 的 {pokemon} 使用了 {move}!",
                    "en" to "{playerName}'s {pokemon} used {move}!"
                ),
                "cross.battle.move_missed" to mapOf(
                    "zh" to "但是没有命中!",
                    "en" to "But it missed!"
                ),
                "cross.battle.damage_dealt" to mapOf(
                    "zh" to "对 {targetPlayer} 的 {targetPokemon} 造成了 {damage} 伤害!",
                    "en" to "Dealt {damage} damage to {targetPlayer}'s {targetPokemon}!"
                ),
                "cross.battle.critical_hit" to mapOf(
                    "zh" to "击中要害!",
                    "en" to "A critical hit!"
                ),
                "cross.battle.effectiveness.none" to mapOf(
                    "zh" to "没有效果!",
                    "en" to "It doesn't affect..."
                ),
                "cross.battle.effectiveness.very_bad" to mapOf(
                    "zh" to "效果非常差...",
                    "en" to "It's not very effective..."
                ),
                "cross.battle.effectiveness.bad" to mapOf(
                    "zh" to "效果不太好...",
                    "en" to "It's not very effective..."
                ),
                "cross.battle.effectiveness.good" to mapOf(
                    "zh" to "效果不错!",
                    "en" to "It's effective!"
                ),
                "cross.battle.effectiveness.super" to mapOf(
                    "zh" to "效果拔群!",
                    "en" to "It's super effective!"
                ),
                "cross.battle.effectiveness.very_super" to mapOf(
                    "zh" to "效果非常拔群!",
                    "en" to "It's extremely effective!"
                ),
                "cross.battle.status_applied" to mapOf(
                    "zh" to "{pokemon} 陷入了{status} 状态!",
                    "en" to "{pokemon} was inflicted with {status}!"
                ),
                "cross.battle.status_damage" to mapOf(
                    "zh" to "{pokemon} 因{status} 损失了 {damage} HP!",
                    "en" to "{pokemon} lost {damage} HP due to {status}!"
                ),
                "cross.battle.pokemon_fainted" to mapOf(
                    "zh" to "{playerName} 的 {pokemon} 倒下了!",
                    "en" to "{playerName}'s {pokemon} fainted!"
                ),
                "cross.battle.switch_out" to mapOf(
                    "zh" to "{playerName} 收回了 {pokemon}!",
                    "en" to "{playerName} withdrew {pokemon}!"
                ),
                "cross.battle.switch_in" to mapOf(
                    "zh" to "{playerName} 派出了 {pokemon}!",
                    "en" to "{playerName} sent out {pokemon}!"
                ),
                "cross.battle.stat_change" to mapOf(
                    "zh" to "{pokemon} 的 {stat} {direction}了!",
                    "en" to "{pokemon}'s {stat} {direction}!"
                ),
                "cross.battle.ability_triggered" to mapOf(
                    "zh" to "{pokemon} 触发了特性 [{ability}]!",
                    "en" to "{pokemon}'s ability [{ability}] triggered!"
                ),
                "cross.battle.move_unusable" to mapOf(
                    "zh" to "{pokemon} 无法使用 {move} (PP 不足)",
                    "en" to "{pokemon} can't use {move} (no PP left)"
                ),
                "cross.battle.ended" to mapOf(
                    "zh" to "===== 战斗结束! =====",
                    "en" to "===== BATTLE ENDED! ====="
                ),
                "cross.battle.forfeit_self" to mapOf(
                    "zh" to "你已投降认输",
                    "en" to "You forfeited the match"
                ),
                "cross.battle.win" to mapOf(
                    "zh" to "恭喜你赢得了对战!",
                    "en" to "Congratulations! You won the battle!"
                ),
                "cross.battle.lose" to mapOf(
                    "zh" to "很遗憾，你输掉了对战",
                    "en" to "Unfortunately, you lost the battle"
                ),
                "cross.battle.slow_start_ended" to mapOf(
                    "zh" to "{pokemon} 摆脱了缓慢启动的影响!",
                    "en" to "{pokemon} ended Slow Start!"
                ),
                "cross.battle.your_pokemon" to mapOf(
                    "zh" to "你的宝可梦{index}",
                    "en" to "Your Pokemon {index}"
                ),
                "cross.battle.opponent_pokemon" to mapOf(
                    "zh" to "对手的宝可梦{index}",
                    "en" to "Opponent's Pokemon {index}"
                ),
                "cross.battle.state_title" to mapOf(
                    "zh" to "===== 战斗状态 [回合 {turn}] =====",
                    "en" to "===== BATTLE STATE [Turn {turn}] ====="
                ),
                "cross.battle.hp" to mapOf(
                    "zh" to "HP: {current}/{max}",
                    "en" to "HP: {current}/{max}"
                ),
                "cross.battle.status" to mapOf(
                    "zh" to "状态: {status}",
                    "en" to "Status: {status}"
                ),
                "cross.battle.current_pp" to mapOf(
                    "zh" to "当前技能PP:",
                    "en" to "Current PP:"
                ),
                "cross.battle.move_pp" to mapOf(
                    "zh" to "{index}. {name}: {currentPP}/{maxPP}",
                    "en" to "{index}. {name}: {currentPP}/{maxPP}"
                ),
                "cross.battle.hp_percent" to mapOf(
                    "zh" to "HP: {percent}%",
                    "en" to "HP: {percent}%"
                ),
                "cross.battle.no_active" to mapOf(
                    "zh" to "你当前没有进行中的对战",
                    "en" to "You don't have an active battle"
                ),
                "cross.battle.command_sent" to mapOf(
                    "zh" to "指令已发送: {command}",
                    "en" to "Command sent: {command}"
                ),
                "cross.battle.opponent_action_taken" to mapOf(
                    "zh" to "对手 {playerName} 已选择行动",
                    "en" to "Opponent {playerName} has chosen an action"
                ),
                "cross.battle.timeout_move" to mapOf(
                    "zh" to "玩家 {playerName} 未及时选择行动，已默认使用 {move}",
                    "en" to "Player {playerName} did not select an action in time. Defaulting to {move}"
                ),

                // ELO系统
                "cross.elo.update" to mapOf(
                    "zh" to "你的 Elo 分数更新: {oldRating} → {newRating} ({change})",
                    "en" to "Your Elo rating updated: {oldRating} → {newRating} ({change})"
                ),

                // 聊天
                "cross.chat.message" to mapOf(
                    "zh" to "[跨服匹配消息 {opponentName}] {message}",
                    "en" to "[Cross-Battle {opponentName}] {message}"
                ),

                // CrossCommand 消息
                "command.only_player" to mapOf(
                    "zh" to "[跨服匹配] 命令只能由玩家执行",
                    "en" to "[CrossServer] Only players can use this command"
                ),
                "command.join.empty_team" to mapOf(
                    "zh" to "[跨服匹配] 队伍为空，无法加入匹配",
                    "en" to "[CrossServer] Your team is empty, cannot join queue"
                ),
                "command.join.invalid_size" to mapOf(
                    "zh" to "[跨服匹配] 只能带一只宝可梦加入匹配",
                    "en" to "[CrossServer] You can only bring one Pokemon"
                ),
                "command.join.inventory_not_empty" to mapOf(
                    "zh" to "[跨服匹配] 背包不为空，无法加入匹配",
                    "en" to "[CrossServer] Your inventory must be empty"
                ),
                "command.join.success" to mapOf(
                    "zh" to "你已成功加入 {mode} 匹配队列",
                    "en" to "Joined {mode} matchmaking queue"
                ),
                "command.join.fail" to mapOf(
                    "zh" to "加入匹配失败：{error}",
                    "en" to "Failed to join queue: {error}"
                ),
                "command.leave.success" to mapOf(
                    "zh" to "你已成功离开匹配队列",
                    "en" to "Left matchmaking queue"
                ),
                "command.leave.fail" to mapOf(
                    "zh" to "离开匹配失败：{error}",
                    "en" to "Failed to leave queue: {error}"
                ),
                "command.connect.start" to mapOf(
                    "zh" to "[跨服匹配] 正在尝试连接云服务...",
                    "en" to "[CrossServer] Connecting to cloud service..."
                ),
                "command.connect.stop" to mapOf(
                    "zh" to "[跨服匹配] 云服务连接已断开",
                    "en" to "[CrossServer] Cloud connection stopped"
                ),
                "command.battle.no_active" to mapOf(
                    "zh" to "[战斗] 你当前没有进行中的对战",
                    "en" to "[Battle] You don't have an active battle"
                ),
                "command.battle.invalid_move_slot" to mapOf(
                    "zh" to "[战斗] 招式槽位无效，槽位必须在 1 到 4 之间",
                    "en" to "[Battle] Invalid move slot (must be 1-4)"
                ),
                "command.battle.sent" to mapOf(
                    "zh" to "指令已发送: {command}",
                    "en" to "Command sent: {command}"
                ),
                "command.chat.sent" to mapOf(
                    "zh" to "聊天消息已发送",
                    "en" to "Chat message sent"
                )
            )
            val json = gson.toJson(defaultMessages)
            Files.writeString(path, json)
            return defaultMessages
        }

        val json = Files.readString(path)
        val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
        return gson.fromJson(json, type)
    }

    fun get(key: String, lang: String = CobblemonRanked.config.defaultLang, vararg args: Pair<String, Any>): String {
        val raw = messages[key]?.get(lang) ?: key
        return args.fold(raw) { acc, (k, v) -> acc.replace("{$k}", v.toString()) }
    }
}