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
    private val path: Path = Paths.get("config/cobblemon-ranked/messages.json")
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
                    "zh" to "§c无效的战斗模式: {format}",
                    "en" to "§cInvalid battle format: {format}"
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
                "queue.battle_start_fail" to mapOf(
                    "zh" to "§c创建战斗失败: {reason}",
                    "en" to "§cFailed to start battle: {reason}"
                ),
                "queue.battle_start" to mapOf(
                    "zh" to "§a战斗开始！对战: §e{opponent}",
                    "en" to "§aBattle started! Opponent: §e{opponent}"
                ),

                // DuoMatchmakingQueue (双人匹配)
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
                "duo.waiting_for_teammate" to mapOf(
                    "zh" to "§a等待队友加入双人排位... (使用 /rank duo invite <玩家名>)",
                    "en" to "§aWaiting for your teammate to join 2v2... (Use /rank duo invite <player>)"
                ),
                "duo.form_fail" to mapOf(
                    "zh" to "§c队伍数据异常，无法组队",
                    "en" to "§cTeam data error. Failed to form a team."
                ),
                "duo.queue_success" to mapOf(
                    "zh" to "§a已与 {partner} 组成排位队伍，等待匹配...",
                    "en" to "§aYou and {partner} have formed a ranked team. Waiting for match..."
                ),
                "duo.battle_start_fail" to mapOf(
                    "zh" to "§c创建对战失败: {reason}",
                    "en" to "§cFailed to start battle: {reason}"
                ),

                // SeasonManager
                "season.start.title" to mapOf(
                    "zh" to "§6新赛季开始!",
                    "en" to "§6New Season Started!"
                ),
                "season.start.subtitle" to mapOf(
                    "zh" to "§f赛季 #{season} ({start} - {end})",
                    "en" to "§fSeason #{season} ({start} - {end})"
                ),

                // RewardManager
                "reward.broadcast" to mapOf(
                    "zh" to "§6[段位奖励] §b{player} §f首次晋升到 §e{rank} §f，已发放奖励！",
                    "en" to "§6[Rank Reward] §b{player} §fhas reached §e{rank} §fand received a reward!"
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
                "queue.left_all" to mapOf(
                    "zh" to "§c你已退出所有匹配队列",
                    "en" to "§cYou have left all matchmaking queues"
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
                    "zh" to "§6{player} 的 {format} 数据（赛季 #{season})\\n§f段位: §e{title} §8(ELO: {elo})\\n§f全球排名: §b{rank}\\n§f战绩: §a{wins}§7/§c{losses} §8(胜率: {rate}%)\\n§f连胜: §6{streak} §8(最高: {best})\\n§f断线次数: §c{flee}",
                    "en" to "§6{player}'s {format} Stats (Season #{season})\\n§fRank: §e{title} §8(ELO: {elo})\\n§fGlobal Rank: §b{rank}\\n§fRecord: §a{wins}§7/§c{losses} §8(Win Rate: {rate}%)\\n§fStreak: §6{streak} §8(Best: {best})\\n§fDisconnection: §c{flee}"
                ),"leaderboard.empty" to mapOf(
                    "zh" to "§7赛季 #{season} [{format}] 暂无排位数据",
                    "en" to "§7No ranking data for season #{season} [{format}]"
                ),
                "leaderboard.header" to mapOf(
                    "zh" to "§6===== [{format} - 赛季 #{season}] 排行榜 ({page}/{total}) =====",
                    "en" to "§6===== [{format} - Season #{season}] Leaderboard ({page}/{total}) ====="
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
                    "zh" to "§6当前赛季: #{season}\\n§f开始时间: §7{start}\\n§f结束时间: §7{end}\\n§f赛季时长: §e{duration}天\\n§f剩余时间: §e{remaining}\\n§f参与玩家: §a{players} 人",
                    "en" to "§6Current Season: #{season}\\n§fStart: §7{start}\\n§fEnd: §7{end}\\n§fDuration: §e{duration} days\\n§fRemaining: §e{remaining}\\n§fParticipants: §a{players}"
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
                "gui.op.reward" to mapOf("zh" to "§c[获取奖励-测试用]", "en" to "§c[Gain Rewards - Test]"),
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
                "gui.queue_title" to mapOf(
                    "zh" to "§6§l▶ 加入匹配 - 请选择模式",
                    "en" to "§6§l▶ Join Match - Select Mode"
                ),
                "gui.queue.1v1" to mapOf("zh" to "§a[加入 单打]", "en" to "§a[Join singles]"),
                "gui.queue.2v2" to mapOf("zh" to "§b[加入 双打]", "en" to "§b[Join doubles]"),
                "gui.queue.leave" to mapOf("zh" to "§c[离开匹配]", "en" to "§c[Leave Match]"),
                "gui.reward.format_title" to mapOf(
                    "zh" to "§6§l▶ 选择奖励格式",
                    "en" to "§6§l▶ Select Reward Format"
                ),
                "gui.reward.format_button" to mapOf(
                    "zh" to "§c[发放 {format} 奖励]",
                    "en" to "§c[Grant {format} Rewards]"
                ),
                "gui.reward.no_data" to mapOf(
                    "zh" to "§c未找到你的段位数据，无法发放奖励",
                    "en" to "§cNo rank data found. Unable to issue rewards."
                ),
                "gui.reward.title" to mapOf(
                    "zh" to "§6§l▶ 可领取的段位奖励 - {format}",
                    "en" to "§6§l▶ Available Rewards - {format}"
                ),
                "gui.reward.claim" to mapOf(
                    "zh" to "§a[发放 {rank} 段位奖励]",
                    "en" to "§a[Claim {rank} Reward]"
                ),
                "gui.reward.all_claimed" to mapOf(
                    "zh" to "§7你已领取所有 {format} 模式的段位奖励。",
                    "en" to "§7You have claimed all rewards for {format} mode."
                ),
                "gui.reset.title" to mapOf(
                    "zh" to "§6§l▶ 重置玩家排位数据（第 {page} 页 / 共 {total} 页）",
                    "en" to "§6§l▶ Reset Player Rank Data (Page {page} / {total})"
                ),
                "gui.reset.1v1" to mapOf("zh" to "§a[重置 单打]", "en" to "§a[Reset singles]"),
                "gui.reset.2v2" to mapOf("zh" to "§c[重置 双打]", "en" to "§c[Reset doubles]"),
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
                "gui.info_target.title" to mapOf(
                    "zh" to "§6§l▶ {player} 的段位 - {format} - 选择赛季",
                    "en" to "§6§l▶ {player}'s Rank - {format} - Select Season"
                ),
                "gui.info_target.season" to mapOf(
                    "zh" to "§a[赛季 #{season}]",
                    "en" to "§a[Season #{season}]"
                ),
                "gui.myinfo.1v1" to mapOf(
                    "zh" to "§b[单打]",
                    "en" to "§b[singles]"
                ),
                "gui.myinfo.2v2" to mapOf(
                    "zh" to "§c[双打]",
                    "en" to "§c[doubles]"
                ),

                // BattleHandler
                "battle.disconnect.loser" to mapOf(
                    "zh" to "§c你断线导致失败，当前ELO: {elo}",
                    "en" to "§cYou disconnected and lost. Current ELO: {elo}"
                ),
                "battle.disconnect.winner" to mapOf(
                    "zh" to "§a对手断线，你的分数未变化。当前ELO: {elo}",
                    "en" to "§aOpponent disconnected. Your ELO remains unchanged. Current ELO: {elo}"
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

                // DuoSpectatorManager
                "duo.bossbar.title" to mapOf(
                    "zh" to "§e2v2 车轮战对决: ",
                    "en" to "§e2v2 Showdown: "
                ),
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