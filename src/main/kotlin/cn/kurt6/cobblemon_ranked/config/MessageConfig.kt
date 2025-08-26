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
                "battle.VictoryRewards" to mapOf(
                    "zh" to "§a已发放获胜奖励！",
                    "en" to "§aVictory rewards have been granted!"
                ),
                "pokemon_usage.header" to mapOf(
                    "zh" to "§e===== 赛季 §6{season} {name} §e宝可梦使用统计 (第 §6{page}§e/§6{total}§e 页) =====",
                    "en" to "§e===== Season §6{season} {name} §ePokemon Usage Statistics (Page §6{page}§e/§6{total}§e) ====="
                ),
                "pokemon_usage.entry" to mapOf(
                    "zh" to "§e#{rank} §a{species} §f- 使用次数: §b{count} §f(使用率: §b{rate}%)",
                    "en" to "§e#{rank} §a{species} §f- Usage: §b{count} §f(Usage Rate: §b{rate}%)"
                ),
                "pokemon_usage.empty" to mapOf(
                    "zh" to "§c赛季 {season} {name} 没有宝可梦使用记录",
                    "en" to "§cSeason {season} {name} has no pokemon usage records"
                ),
                "pokemon_usage.statistics" to mapOf(
                    "zh" to "§e[宝可梦统计]",
                    "en" to "§e[Pokemon Statistics]"
                ),
                // MatchmakingQueue (单人匹配)
                "queue.global_join" to mapOf(
                    "zh" to "§a玩家 {player} 加入了 {format} 模式的匹配队列！",
                    "en" to "§aPlayer {player} has joined the matching queue of {format} mode!"
                ),
                "queue.opponent_disconnected" to mapOf(
                    "zh" to "§c你的对手已断开连接，已重新加入匹配队列",
                    "en" to "§cYour opponent has disconnected and has rejoined the matching queue"
                ),
                "queue.cooldown" to mapOf(
                    "zh" to "§c你刚刚匹配失败，请等待 {seconds} 秒后再尝试加入",
                    "en" to "§cYou recently failed matchmaking. Please wait {seconds} seconds before trying again"
                ),
                "queue.invalid_format" to mapOf(
                    "zh" to "§c禁用的战斗模式: {format}",
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
                "queue.join_success_customLevel" to mapOf(
                    "zh" to "§c服务器已启用强制修改宝可梦等级功能，等待时获取的宝可梦经验将无效",
                    "en" to "§cThe server has enabled the forced modification of Pokémon level function. The Pokémon experience obtained while waiting will be invalid"
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
                "queue.customBattleLevel" to mapOf(
                    "zh" to "§a已启用自定义等级，强制修改宝可梦等级为: {level}",
                    "en" to "§aCustom level enabled, forcing modification of Pokémon level to: {level}"
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
                "leaderboard.prev_page" to mapOf(
                    "zh" to "§e« 上一页",
                    "en" to "§e« Previous Page"
                ),
                "leaderboard.next_page" to mapOf(
                    "zh" to "§e下一页 »",
                    "en" to "§eNext Page »"
                ),
                "gui.op.cross_start" to mapOf(
                    "zh" to "§c[连接云服]",
                    "en" to "§c[Connect to Cloud]"
                ),
                "gui.op.cross_stop" to mapOf(
                    "zh" to "§c[断开云服]",
                    "en" to "§c[Disconnect from Cloud]"
                ),
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
                "gui.cross_join_singles" to mapOf("zh" to "§a[加入跨服单打匹配]", "en" to "§a[Join Cross Server Singles]"),
                "gui.cross_leave" to mapOf("zh" to "§a[离开跨服匹配]", "en" to "§a[Leave Cross Server]"),
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
                "customBattleLevel.restore" to mapOf(
                    "zh" to "§a已恢复宝可梦等级",
                    "en" to "§aRecovered Pokémon level"
                ),
                "battle.team.banned_nature" to mapOf(
                    "zh" to "§c队伍中有宝可梦使用了被禁止的性格: {names}",
                    "en" to "§cYour team contains Pokémon with banned natures: {names}"
                ),
                "battle.team.banned_gender" to mapOf(
                    "zh" to "§c队伍中有宝可梦使用了被禁止的性别: {names}",
                    "en" to "§cYour team contains Pokémon with banned genders: {names}"
                ),
                "battle.team.banned_ability" to mapOf(
                    "zh" to "§c队伍中有宝可梦使用了被禁止的特性: {names}",
                    "en" to "§cYour team contains Pokémon with banned abilities: {names}"
                ),
                "battle.team.banned_moves" to mapOf(
                    "zh" to "§c队伍中有宝可梦使用了被禁止的招式: {names}",
                    "en" to "§cYour team contains banned moves: {names}"
                ),
                "battle.team.banned_shiny" to mapOf(
                    "zh" to "§c队伍中有宝可梦使用了被禁止的闪光个体: {names}",
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
                    "zh" to "§c你不能在排位战中逃跑！",
                    "en" to "§cYou cannot flee in a ranked battle."
                ),
                "battle.team.banned_held_items" to mapOf(
                "zh" to "§c队伍中有宝可梦携带了被禁止的物品: {names}",
                "en" to "§cYour team contains Pokémon with banned held items: {names}"
                ),
                "battle.team.banned_moves" to mapOf(
                    "zh" to "§c队伍中有宝可梦使用了被禁止的招式: {names}",
                    "en" to "§cYour team contains banned moves: {names}"
                ),
                "battle.team.banned_abilities" to mapOf(
                    "zh" to "§c队伍中有宝可梦使用了被禁止的特性: {names}",
                    "en" to "§cYour team contains Pokémon with banned abilities: {names}"
                ),
                "battle.team.banned_natures" to mapOf(
                    "zh" to "§c队伍中有宝可梦使用了被禁止的性格: {names}",
                    "en" to "§cYour team contains banned natures: {names}"
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
                    "zh" to "§7未知",
                    "en" to "§7Unknown"
                ),
                "cross.unknown_opponent" to mapOf(
                    "zh" to "§7未知对手",
                    "en" to "§7Unknown opponent"
                ),
                "cross.error.unknown" to mapOf(
                    "zh" to "§c未知错误",
                    "en" to "§cUnknown error"
                ),

                // 状态/方向
                "cross.status.normal" to mapOf(
                    "zh" to "§a正常",
                    "en" to "§aNormal"
                ),
                "cross.status.par" to mapOf(
                    "zh" to "§e麻痹",
                    "en" to "§eParalyzed"
                ),
                "cross.status.brn" to mapOf(
                    "zh" to "§6灼伤",
                    "en" to "§6Burned"
                ),
                "cross.status.psn" to mapOf(
                    "zh" to "§d中毒",
                    "en" to "§dPoisoned"
                ),
                "cross.status.badpsn" to mapOf(
                    "zh" to "§5剧毒",
                    "en" to "§5Badly Poisoned"
                ),
                "cross.status.slp" to mapOf(
                    "zh" to "§9睡眠",
                    "en" to "§9Asleep"
                ),
                "cross.status.frz" to mapOf(
                    "zh" to "§b冰冻",
                    "en" to "§bFrozen"
                ),
                "cross.direction.up" to mapOf(
                    "zh" to "§a提升",
                    "en" to "§arose"
                ),
                "cross.direction.down" to mapOf(
                    "zh" to "§c下降",
                    "en" to "§cfell"
                ),

                // 日志
                "cross.log.heartbeat_start" to mapOf(
                    "zh" to "§8开始心跳",
                    "en" to "§8Heartbeat started"
                ),
                "cross.log.heartbeat_failed" to mapOf(
                    "zh" to "§c发送心跳失败: {error}",
                    "en" to "§cFailed to send heartbeat: {error}"
                ),
                "cross.log.connected" to mapOf(
                    "zh" to "§a连接成功",
                    "en" to "§aConnected successfully"
                ),
                "cross.log.message_received" to mapOf(
                    "zh" to "§7收到服务器消息: {message}...",
                    "en" to "§7Received server message: {message}..."
                ),
                "cross.log.unknown_message_type" to mapOf(
                    "zh" to "§e未知消息类型: {type}",
                    "en" to "§eUnknown message type: {type}"
                ),
                "cross.log.parse_failed" to mapOf(
                    "zh" to "§c解析失败: {error} | 原始消息: {raw}",
                    "en" to "§cParse failed: {error} | Raw message: {raw}"
                ),
                "cross.log.closing" to mapOf(
                    "zh" to "§e连接正在关闭: code={code}, reason={reason}",
                    "en" to "§eConnection closing: code={code}, reason={reason}"
                ),
                "cross.log.connection_failed" to mapOf(
                    "zh" to "§cWebSocket连接失败: {error}",
                    "en" to "§cWebSocket connection failed: {error}"
                ),
                "cross.log.connection_closed" to mapOf(
                    "zh" to "§eWebSocket连接已关闭: {code} - {reason}",
                    "en" to "§eWebSocket connection closed: {code} - {reason}"
                ),
                "cross.log.reconnect_attempt" to mapOf(
                    "zh" to "§6尝试重连 (尝试次数: {attempts})",
                    "en" to "§6Attempting reconnect (attempt: {attempts})"
                ),
                "cross.log.reconnect_stop" to mapOf(
                    "zh" to "§6已达到最大重连次数次)，停止重连",
                    "en" to "§6Reached maximum reconnect attempts attempts), stopping"
                ),
                "cross.log.request_battle_state" to mapOf(
                    "zh" to "§7请求战斗状态更新: {battleId}",
                    "en" to "§7Requesting battle state update: {battleId}"
                ),
                "cross.log.send_battle_command" to mapOf(
                    "zh" to "§7发送战斗指令: battle_id = {battleId}, player_id = {playerId}, command = {command}",
                    "en" to "§7Sending battle command: battle_id = {battleId}, player_id = {playerId}, command = {command}"
                ),
                "cross.log.disconnected" to mapOf(
                    "zh" to "§e连接已断开",
                    "en" to "§eDisconnected"
                ),
                "cross.log.missing_opponent_team" to mapOf(
                    "zh" to "§c匹配成功消息中缺少对手队伍信息",
                    "en" to "§cMissing opponent team information in match found message"
                ),
                "cross.log.player_not_found" to mapOf(
                    "zh" to "§c未找到玩家 {playerId}，无法发送 Elo 更新消息",
                    "en" to "§cPlayer {playerId} not found, cannot send Elo update"
                ),
                "cross.log.auto_forfeit" to mapOf(
                    "zh" to "§e玩家 {player} 断线，自动发送投降指令: battle_id = {battleId}",
                    "en" to "§ePlayer {player} disconnected, auto-sending forfeit: battle_id = {battleId}"
                ),

                // 队列
                "cross.queue.already_in_queue" to mapOf(
                    "zh" to "§e[跨服匹配]你已在匹配队列中",
                    "en" to "§e[CrossServer]You are already in the queue"
                ),
                "cross.queue.join_success" to mapOf(
                    "zh" to "§a[跨服匹配]你已成功加入 {mode} 匹配队列",
                    "en" to "§a[CrossServer]You have joined the {mode} queue"
                ),
                "cross.queue.join_failed" to mapOf(
                    "zh" to "§c[跨服匹配]加入匹配失败：{error}",
                    "en" to "§c[CrossServer]Failed to join queue: {error}"
                ),
                "cross.queue.leave_success" to mapOf(
                    "zh" to "§a[跨服匹配]你已成功离开匹配队列",
                    "en" to "§a[CrossServer]You have left the queue"
                ),
                "cross.queue.leave_failed" to mapOf(
                    "zh" to "§c[跨服匹配]离开匹配失败：{error}",
                    "en" to "§c[CrossServer]Failed to leave queue: {error}"
                ),
                "cross.queue.join_failed.authenticated_only" to mapOf(
                    "zh" to "§c[跨服匹配]仅限正版玩家参与匹配",
                    "en" to "§c[CrossServer]Only authentic players can join the queue"
                ),
                "cross.queue.join_failed.battles_exceeds" to mapOf(
                    "zh" to "§c[跨服匹配]当前战斗数量超出了限制，请稍后再试。",
                    "en" to "§c[CrossServer]Current battle count exceeds the limit, please try again later."
                ),

                // 对战
                "cross.battle.match_found" to mapOf(
                    "zh" to "§6===== §a匹配成功! §6=====",
                    "en" to "§6===== §aMATCH FOUND! §6====="
                ),
                "cross.battle.opponent" to mapOf(
                    "zh" to "§c对手: {name}",
                    "en" to "§cOpponent: {name}"
                ),
                "cross.battle.opponent_team" to mapOf(
                    "zh" to "§c对手队伍:",
                    "en" to "§cOpponent Team: "
                ),
                "cross.lead" to mapOf(
                    "zh" to "§c首发: ",
                    "en" to "§cLead: "
                ),
                "cross.battle.your_team" to mapOf(
                    "zh" to "§a你的首发:",
                    "en" to "§aYour lead:"
                ),
                "cross.battle.pokemon_info" to mapOf(
                    "zh" to "§7宝可梦: {name} | §cHP: {hp}/{maxHp}",
                    "en" to "§7Pokemon: {name} | §cHP: {hp}/{maxHp}"
                ),
                "cross.battle.current_moves" to mapOf(
                    "zh" to "§b当前宝可梦技能:",
                    "en" to "§bCurrent moves:"
                ),
                "cross.battle.move_pp" to mapOf(
                    "zh" to "§e{index}§r: {name} §7(PP: {currentPP}/{maxPP})",
                    "en" to "§e{index}§r: {name} §7(PP: {currentPP}/{maxPP})"
                ),
                "cross.battle.click_hint" to mapOf(
                    "zh" to "§7点击§e[数字]§7可直接使用技能，悬浮查看描述",
                    "en" to "§7Click §e[number]§7 to use move, Suspended View Description"
                ),
                "cross.move.type" to mapOf(
                    "zh" to "类型: {type}",
                    "en" to "Type: {type}"
                ),
                "cross.move.power" to mapOf(
                    "zh" to "威力: {power}",
                    "en" to "Power: {power}"
                ),
                "cross.move.accuracy" to mapOf(
                    "zh" to "命中: {accuracy}%",
                    "en" to "Accuracy: {accuracy}%"
                ),
                "cross.move.accuracy.sure_hit" to mapOf(
                    "zh" to "命中: 必中",
                    "en" to "Accuracy: Sure Hit"
                ),
                "cross.move.category" to mapOf(
                    "zh" to "类别: {category}",
                    "en" to "Category: {category}"
                ),
                "cross.battle.move_info" to mapOf(
                    "zh" to "§7{index}. {name} (§d{pp}/{pp}§7)",
                    "en" to "§7{index}. {name} (§d{pp}/{pp}§7)"
                ),
                "cross.battle.switch_options" to mapOf(
                    "zh" to "§b更换宝可梦:",
                    "en" to "§bSwitch Pokemon:"
                ),
                "command.battle.invalid_switch_slot" to mapOf(
                    "zh" to "§c无效的宝可梦槽位，请输入1-6之间的数字",
                    "en" to "§cInvalid Pokemon slot, please enter a number between 1-6"
                ),
                "cross.battle.forfeit_command" to mapOf(
                    "zh" to "§c投降: /rank cross battle forfeit",
                    "en" to "§cForfeit: /rank cross battle forfeit"
                ),
                "cross.battle.chat" to mapOf(
                    "zh" to "§7与对手聊天: /rank cross chat <message>",
                    "en" to "§7Chat with opponent: /rank cross chat <message>"
                ),
                "cross.battle.turn_start" to mapOf(
                    "zh" to "§e[回合 {turn}] §a开始！请输入你的指令,3分钟内未选将弃权",
                    "en" to "§e[Turn {turn}] §aBegin! Please enter your command.Abstain if not selected within 3 minutes."
                ),
                "cross.battle.start" to mapOf(
                    "zh" to "§6===== §c战斗开始! §6=====",
                    "en" to "§6===== §cBATTLE START! §6====="
                ),
                "cross.battle.players" to mapOf(
                    "zh" to "§a对战双方: {player1} §fvs §c{player2}",
                    "en" to "§aPlayers: {player1} §fvs §c{player2}"
                ),
                "cross.battle.lead" to mapOf(
                    "zh" to "§f{player} §7派出了 §e{pokemon}",
                    "en" to "§f{player} §7sent out §e{pokemon}"
                ),
                "cross.battle.move_used" to mapOf(
                    "zh" to "§f{playerName} §7的 §e{pokemon} §7使用了 §b{move}§7!",
                    "en" to "§f{playerName}§7's §e{pokemon} §7used §b{move}§7!"
                ),
                "cross.battle.move_missed" to mapOf(
                    "zh" to "§7但是没有命中!",
                    "en" to "§7But it missed!"
                ),
                "cross.battle.damage_dealt" to mapOf(
                    "zh" to "§7对 §c{targetPlayer} §7的 §e{targetPokemon} §c造成了 {damage} 伤害!",
                    "en" to "§7Dealt §c{damage} damage §7to §c{targetPlayer}§7's §e{targetPokemon}§7!"
                ),
                "cross.battle.critical_hit" to mapOf(
                    "zh" to "§c击中要害!",
                    "en" to "§cA critical hit!"
                ),
                "cross.battle.effectiveness.none" to mapOf(
                    "zh" to "§8没有效果!",
                    "en" to "§8It doesn't affect..."
                ),
                "cross.battle.effectiveness.very_bad" to mapOf(
                    "zh" to "§7效果非常差...",
                    "en" to "§7It's not very effective..."
                ),
                "cross.battle.effectiveness.bad" to mapOf(
                    "zh" to "§7效果不太好...",
                    "en" to "§7It's not very effective..."
                ),
                "cross.battle.effectiveness.good" to mapOf(
                    "zh" to "§a效果不错!",
                    "en" to "§aIt's effective!"
                ),
                "cross.battle.effectiveness.super" to mapOf(
                    "zh" to "§6效果拔群!",
                    "en" to "§6It's super effective!"
                ),
                "cross.battle.effectiveness.very_super" to mapOf(
                    "zh" to "§c效果非常拔群!",
                    "en" to "§cIt's extremely effective!"
                ),
                "cross.battle.status_applied" to mapOf(
                    "zh" to "§e{pokemon} §7陷入了{status} §7状态!",
                    "en" to "§e{pokemon} §7was inflicted with {status}§7!"
                ),
                "cross.battle.status_damage" to mapOf(
                    "zh" to "§e{pokemon} §7因{status} §c损失了 {damage} HP!",
                    "en" to "§e{pokemon} §7lost §c{damage} HP §7due to {status}!"
                ),
                "cross.battle.pokemon_fainted" to mapOf(
                    "zh" to "§8{playerName} §7的 §e{pokemon} §8倒下了!",
                    "en" to "§8{playerName}§7's §e{pokemon} §8fainted!"
                ),
                "cross.battle.switch_out" to mapOf(
                    "zh" to "§f{playerName} §7收回了 §e{pokemon}§7!",
                    "en" to "§f{playerName} §7withdrew §e{pokemon}§7!"
                ),
                "cross.battle.switch_in" to mapOf(
                    "zh" to "§f{playerName} §7派出了 §e{pokemon}§7!",
                    "en" to "§f{playerName} §7sent out §e{pokemon}§7!"
                ),
                "cross.battle.stat_change" to mapOf(
                    "zh" to "§e{pokemon} §7的 §6{stat} {direction}§7了!",
                    "en" to "§e{pokemon}§7's §6{stat} {direction}§7!"
                ),
                "cross.battle.ability_triggered" to mapOf(
                    "zh" to "§e{pokemon} §7触发了特性 §d[{ability}]§7!",
                    "en" to "§e{pokemon}§7's ability §d[{ability}] §7triggered!"
                ),
                "cross.battle.move_unusable" to mapOf(
                    "zh" to "§e{pokemon} §7无法使用 §b{move} §7(PP 不足)",
                    "en" to "§e{pokemon} §7can't use §b{move} §7(no PP left)"
                ),
                "cross.battle.ended" to mapOf(
                    "zh" to "§6===== §e战斗结束! §6=====",
                    "en" to "§6===== §eBATTLE ENDED! §6====="
                ),
                "cross.battle.forfeit_self" to mapOf(
                    "zh" to "§c你已投降认输",
                    "en" to "§cYou forfeited the match"
                ),
                "cross.battle.win" to mapOf(
                    "zh" to "§a§l恭喜你赢得了对战!",
                    "en" to "§a§lCongratulations! You won the battle!"
                ),
                "cross.battle.lose" to mapOf(
                    "zh" to "§c很遗憾，你输掉了对战",
                    "en" to "§cUnfortunately, you lost the battle"
                ),
                "cross.battle.slow_start_ended" to mapOf(
                    "zh" to "§e{pokemon} §a摆脱了缓慢启动的影响!",
                    "en" to "§e{pokemon} §aended Slow Start!"
                ),
                "cross.battle.your_pokemon" to mapOf(
                    "zh" to "§a你的宝可梦 {name}",
                    "en" to "§aYour Pokemon {name}"
                ),
                "cross.battle.opponent_pokemon" to mapOf(
                    "zh" to "§c对手的宝可梦 {name}",
                    "en" to "§cOpponent's Pokemon {name}"
                ),
                "cross.battle.state_title" to mapOf(
                    "zh" to "§6===== §e战斗状态 [回合 {turn}] §6=====",
                    "en" to "§6===== §eBATTLE STATE [Turn {turn}] §6====="
                ),
                "cross.battle.hp" to mapOf(
                    "zh" to "§cHP: {current}/{max}",
                    "en" to "§cHP: {current}/{max}"
                ),
                "cross.battle.status" to mapOf(
                    "zh" to "§d状态: {status}",
                    "en" to "§dStatus: {status}"
                ),
                "cross.battle.current_pp" to mapOf(
                    "zh" to "§b当前技能:(§7点击§e[数字]§7可直接使用，悬浮查看描述)",
                    "en" to "§bCurrent moves: (§7Click §e[number]§7 to use, Suspended View Description)"
                ),
                "cross.battle.hp_percent" to mapOf(
                    "zh" to "§cHP: {percent}%",
                    "en" to "§cHP: {percent}%"
                ),
                "cross.battle.no_active" to mapOf(
                    "zh" to "§e你当前没有进行中的对战",
                    "en" to "§eYou don't have an active battle"
                ),
                "cross.battle.command_sent" to mapOf(
                    "zh" to "§a指令已发送: {command}",
                    "en" to "§aCommand sent: {command}"
                ),
                "cross.battle.opponent_action_taken" to mapOf(
                    "zh" to "§7玩家 {playerName} 已选择行动",
                    "en" to "§7Player {playerName} has chosen an action"
                ),

                // ELO系统
                "cross.elo.update" to mapOf(
                    "zh" to "§6你的 Elo 分数更新: §f{oldRating} §7→ §e{newRating} §a({change})",
                    "en" to "§6Your Elo rating updated: §f{oldRating} §7→ §e{newRating} §a({change})"
                ),

                // 聊天
                "cross.chat.message" to mapOf(
                    "zh" to "§b[跨服匹配消息 {opponentName}] §f{message}",
                    "en" to "§b[Cross-Battle {opponentName}] §f{message}"
                ),

                // CrossCommand 消息
                "cross.queue.not_connected" to mapOf(
                    "zh" to "§c跨服连接尚未建立，请稍后再试或联系管理员",
                    "en" to "§cThe cross-server connection is not established. Please try again later or contact an administrator"
                ),
                "cross.queue.connection_restored" to mapOf(
                    "zh" to "§a跨服连接已恢复",
                    "en" to "§aCross-server connection restored"
                ),
                "cross.queue.connection_lost" to mapOf(
                    "zh" to "§c云服务器连接已断开，您已从队列中移除",
                    "en" to "§cConnection to cloud server lost. You have been removed from the queue"
                ),
                "cross.battle.no_moves_available" to mapOf(
                    "zh" to "§c没有可用技能",
                    "en" to "§cNo skills available"
                ),
                "command.only_player" to mapOf(
                    "zh" to "§c[跨服匹配] 命令只能由玩家执行",
                    "en" to "§c[CrossServer] Only players can use this command"
                ),
                "command.join.authenticated_only" to mapOf(
                    "zh" to "§c[跨服匹配] 非正版账号玩家不可加入匹配",
                    "en" to "§c[CrossServer] Only official accounts can join matchmaking"
                ),
                "command.join.empty_team" to mapOf(
                    "zh" to "§c[跨服匹配] 队伍为空，无法加入匹配",
                    "en" to "§c[CrossServer] Your team is empty, cannot join queue"
                ),
                "command.join.success" to mapOf(
                    "zh" to "§a[跨服匹配]你已成功加入 {mode} 匹配队列",
                    "en" to "§a[CrossServer]Joined {mode} matchmaking queue"
                ),
                "command.join.fail" to mapOf(
                    "zh" to "§c[跨服匹配]加入匹配失败：{error}",
                    "en" to "§c[CrossServer]Failed to join queue: {error}"
                ),
                "command.leave.success" to mapOf(
                    "zh" to "§a[跨服匹配]你已成功离开匹配队列",
                    "en" to "§a[CrossServer]Left matchmaking queue"
                ),
                "command.leave.fail" to mapOf(
                    "zh" to "§c[跨服匹配]离开匹配失败：{error}",
                    "en" to "§c[CrossServer]Failed to leave queue: {error}"
                ),
                "command.connect.start" to mapOf(
                    "zh" to "§e[跨服匹配] 正在尝试连接云服务...",
                    "en" to "§e[CrossServer] Connecting to cloud service..."
                ),
                "command.connect.stop" to mapOf(
                    "zh" to "§e[跨服匹配] 云服务连接已断开",
                    "en" to "§e[CrossServer] Cloud connection stopped"
                ),
                "command.battle.no_active" to mapOf(
                    "zh" to "§e[跨服匹配] 你当前没有进行中的对战",
                    "en" to "§e[CrossServer] You don't have an active battle"
                ),
                "command.battle.invalid_move_slot" to mapOf(
                    "zh" to "§c招式槽位无效，槽位必须在 1 到 4 之间",
                    "en" to "§cInvalid move slot (must be 1-4)"
                ),
                "command.battle.sent" to mapOf(
                    "zh" to "§a指令已发送: {command}",
                    "en" to "§aCommand sent: {command}"
                ),
                "command.chat.sent" to mapOf(
                    "zh" to "§a[跨服匹配]聊天消息已发送",
                    "en" to "§a[CrossServer]Chat message sent"
                ),
                "cross.battle.already_chosen" to mapOf(
                    "zh" to "§c你已在本回合选择过行动，请等待下一回合",
                    "en" to "§cYou have already chosen an action in this turn, please wait for the next turn"
                ),
                "cross.log.localize_string_failed" to mapOf(
                    "zh" to "§c本地化字符串失败: 键={key}, 语言={lang}, 错误={error}",
                    "en" to "§cFailed to localize string: key={key}, lang={lang}, error={error}"
                ),
                "command.join.duplicate_pokemon" to mapOf(
                    "zh" to "§c无法加入匹配队列：队伍中包含重复宝可梦 - {species}",
                    "en" to "§cUnable to join the match queue: Your team contains duplicate Pokémon - {species}"
                ),
                "command.not_in_queue" to mapOf(
                    "zh" to "§c当前不在跨服匹配队列中",
                    "en" to "§cCurrently not in the cross server matching queue"
                ),
                "command.battle.in_queue_or_battle" to mapOf(
                    "zh" to "§c已在跨服队列或战斗中",
                    "en" to "§cAlready in cross server queue or battle"
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