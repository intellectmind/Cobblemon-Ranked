**其他语言版本: [English](README.md)｜[中文](README_zh.md)**

[常见问题解答（FAQ）](https://github.com/intellectmind/Cobblemon-Ranked/wiki/FAQ)

---

# 📊 CobblemonRanked 排位系统说明文档

> 本模组仅需安装在服务端，无需客户端参与  
> 客户端安装后，默认按`X`键可打开图形GUI  
> 💡 如果启用了`enableTeamPreview`，客户端也必须安装此mod  

---

## 🎯 功能总览

- 支持中英文切换，支持扩展更多语言
- 多个战斗场地配置，自动传送和归位
- 自定义段位名称与 Elo 阈值，灵活配置
- 支持单打、双打、2v2单打3个模式
- 独立 Elo 排名系统，按模式分别计算
- 独立的段位奖励系统，支持自定义指令
- 内置赛季机制，自动轮换与数据重置
- 匹配队列支持 Elo 限制与等待放宽机制
- 掉线按失败处理，扣分
- 提供可点击的文字操作界面和客户端图形GUI
- 跨服务器匹配支持
- `enableTeamPreview`启用后，超过数量的玩家会触发选择出战宝可梦界面，未超过设置数量的则不触发。  
  如`singlesPickCountNumber`设置为3，  
  玩家1有5只宝可梦，则会触发选择界面，要求选择3只出战。  
  玩家2只有2只，则不会触发选择界面，等待玩家1选择完成后直接开始战斗。  

## 🌐 跨服匹配

> 从v1.2.0+开始支持，可以从**任何服务器或单人世界**连接（需要**正版Minecraft帐户**）  
> 如果你遇到异常匹配对局，可以在[Discord](https://discord.gg/guTkeS8wEE)上向我们报告  
> 云服务器支持保证至少到2026-01-01  
> 跨服匹配演示及配置：[Bilibili](https://www.bilibili.com/video/BV1ztuwz5ECa)  

### ✅ 如何使用

1. 在配置中启用`enableCrossServer`  
2. 修改`cloudServerId`，不应与其他服务器重复（默认的`server`可能已被某人使用）  
3. 输入`/rank reload`重新加载配置或者重新启动服务器  
4. 输入`/rank cross start`连接云服  

> 🌍 公共服务器：  
> 网址：[http://139.196.103.55](http://139.196.103.55)  
> 密钥：`cobblemonranked`  

### ⚠️ 当前限制

- 仅支持**单打**模式
- 某些道具和技能效果可能无法生效
- 来自同一服务器**的玩家将不会匹配在一起**

### 🌐 跨服务器命令

|命令|描述|权限|
|--------|-------------|------------|
|`/rank cross start`|连接到云服务器|OP|
|`/rank cross stop`|与云断开连接|OP|
|`/rank cross chat` |与对手聊天|全部|
|`/rank cross join singles`|进入单打配对|全部|
|`/rank cross leave`|离开配对队列|全部|
|`/rank cross battle move [1-4]`|使用技能|全部|
|`/rank cross battle switch [1-6]	`|切换宝可梦|全部|
|`/rank cross battle forfeit`|放弃战斗|全部|

---

## 📌 指令总览

> 所有命令均以 `/rank` 开头

---

## 🎮 玩家指令

| 指令 | 功能描述 |
|------|----------|
| `/rank gui` | 打开主菜单 GUI |
| `/rank gui_top` | 打开排行榜选择界面 |
| `/rank gui_info` | 查看自己的 Elo 对战信息 |
| `/rank gui_info_players` | 查看在线玩家，支持点击查看他人战绩 |
| `/rank gui_myinfo` | 快速查看自己的战绩 |
| `/rank gui_queue` | 打开匹配队列加入界面 |
| `/rank gui_info_format <player> <format>` | 查看指定玩家指定模式的赛季战绩 |
| `/rank queue join [format]` | 加入匹配队列 |
| `/rank queue leave` | 退出所有匹配队列 |
| `/rank status` | 查看当前匹配状态 |
| `/rank info <format> <season>` | 查看自己在指定模式与赛季下的 Elo 数据 |
| `/rank info <player> <format> [season]` | 查看其他玩家的 Elo 数据 |
| `/rank top` | 查看当前赛季默认模式排行榜 |
| `/rank top <format> [season] [page] [count]` | 分页查看指定模式与赛季的排行榜 |
| `/rank season` | 查看当前赛季信息 |

---

## 🛡️ 管理员指令（需 OP 权限）

| 指令 | 功能描述 |
|------|----------|
| `/rank gui_reward` | 打开段位奖励选择界面 |
| `/rank gui_reset` | 分页查看在线玩家并重置其战绩 |
| `/rank reset <player> <format>` | 重置指定玩家某模式的当前赛季 Elo 数据 |
| `/rank reward <player> <format> <rank>` | 发放指定段位奖励 |
| `/rank season end` | 结束当前赛季 |
| `/rank reload` | 重新加载配置文件与语言包 |
| `/rank setseasonname <seasonId> <name>` | 设置赛季名称 |

---

## Placeholder API

%cobblemon_ranked:elo%                     - ELO分数  
%cobblemon_ranked:rank_title%              - 段位称号  
%cobblemon_ranked:win_rate%                - 胜率  
%cobblemon_ranked:wins%                    - 胜场  
%cobblemon_ranked:losses%                  - 负场  
%cobblemon_ranked:total_games%             - 总场次  
%cobblemon_ranked:streak%                  - 当前连胜  
%cobblemon_ranked:best_streak%             - 最佳连胜  
%cobblemon_ranked:flee_count%              - 逃跑次数  
%cobblemon_ranked:rank%                    - 排名  
%cobblemon_ranked:season_name%             - 赛季名称  
%cobblemon_ranked:season_id%               - 赛季ID  
%cobblemon_ranked:season_days_left%        - 赛季剩余天数  
%cobblemon_ranked:season_time_left%        - 赛季剩余时间  
%cobblemon_ranked:next_rank_elo%           - 下一段位所需ELO  
%cobblemon_ranked:next_rank_name%          - 下一段位名称  
%cobblemon_ranked:queue_status%            - 排队状态  

支持动态模式参数(singles/doubles/2v2singles)  
例如：  
%cobblemon_ranked:elo_singles%  
%cobblemon_ranked:rank_title_doubles%  
%cobblemon_ranked:win_rate_2v2singles%  

---

## ⚙️ 配置文件说明（`cobblemon_ranked.json`）

<details>
<summary>点击展开查看完整配置（含注释）</summary>

```json
{
  "defaultLang": "zh", // 默认语言：zh 或 en
  "defaultFormat": "singles", // 默认对战模式
  "minTeamSize": 1, // 最少携带宝可梦数量
  "maxTeamSize": 6, // 最多携带宝可梦数量
  "allowDuplicateItems": false, // 是否允许队伍中携带重复道具 (道具条款)
  "enableTeamPreview": true, // 是否启用队伍预览和选出阶段 (6选3/4)
  "teamSelectionTime": 90, // 队伍选择阶段的时间限制(秒)
  "singlesPickCount": 3, // 单打模式选出的宝可梦数量 (通常为3)
  "doublesPickCount": 4, // 双打模式选出的宝可梦数量 (通常为4)
  "maxEloDiff": 200, // 最大 Elo 匹配差值
  "maxQueueTime": 300, // 最大排队等待时间（秒）
  "maxEloMultiplier": 3.0, // Elo 放宽倍率上限
  "seasonDuration": 30, // 赛季持续天数
  "initialElo": 1000, // 初始 Elo 值
  "eloKFactor": 32, // Elo K 系数
  "minElo": 0, // Elo 最低值限制
  "bannedPokemon": ["Mewtwo", "Arceus"], // 禁用宝可梦列表
  "bannedHeldItems": ["cobblemon:leftovers"], // 禁止宝可梦携带的道具
  "bannedCarriedItems": ["cobblemon:leftovers"], // 禁止玩家背包携带的物品
  "bannedMoves": ["leechseed"], // 禁止宝可梦使用的技能
  "bannedNatures": ["cobblemon:naughty"], // 禁止宝可梦使用的性格
  "bannedAbilities": [], // 禁止宝可梦使用的特性
  "bannedGenders": ["MALE"], // 禁止宝可梦使用的性别
  "bannedShiny": false, // 是否禁止闪光宝可梦参战
  "banUsageBelow": 0.0,					   // 禁止使用率低于此阈值的宝可梦（0为关闭，0.1表示10%）
  "banUsageAbove": 0.0,					   // 禁止使用率高于此阈值的宝可梦（0为关闭，0.1表示10%）
  "banTopUsed": 0,						   // 禁止使用排行前N的宝可梦（0为关闭）
  "onlyBaseFormWithEvolution": false,	   // 是否只允许使用能够进化的最初形态
  "allowedFormats": ["singles", "doubles", "2v2singles"], // 支持的对战模式
  "maxLevel": 0, // 宝可梦最大等级（0 表示不限制）
  "allowDuplicateSpecies": false, // 是否允许重复宝可梦
  "enableCustomLevel": false, // 是否启用强制修改宝可梦等级
  "customBattleLevel": 50, // 设置强制修改宝可梦的等级
  "battleArenas": [ // 战斗场地配置
    {
      "world": "minecraft:overworld",
      "playerPositions": [
        { "x": 0.0, "y": 70.0, "z": 0.0 },
        { "x": 10.0, "y": 70.0, "z": 0.0 }
      ]
    },
    {
      "world": "minecraft:overworld",
      "playerPositions": [
        { "x": 100.0, "y": 65.0, "z": 100.0 },
        { "x": 110.0, "y": 65.0, "z": 100.0 }
      ]
    }
  ],
	"victoryRewards": [ // 每场比赛获胜的奖励配置
		"give {player} minecraft:experience_bottle 5",
		"give {player} minecraft:emerald 1"
	],
  "rankRewards": { // 段位奖励（按模式分别配置）
    "singles": {
      "青铜": ["give {player} minecraft:apple 5"],
      "白银": ["give {player} minecraft:golden_apple 3"],
      "黄金": ["give {player} minecraft:diamond 2", "give {player} minecraft:emerald 5"],
      "白金": ["give {player} minecraft:diamond_block 1", "effect give {player} minecraft:strength 3600 1"],
      "钻石": ["give {player} minecraft:netherite_ingot 1", "give {player} minecraft:elytra 1"],
      "大师": ["give {player} minecraft:netherite_block 2", "give {player} minecraft:totem_of_undying 1", "effect give {player} minecraft:resistance 7200 2"]
    },
    "doubles": {
      "青铜": ["give {player} minecraft:bread 5"],
      "白银": ["give {player} minecraft:gold_nugget 10"],
      "黄金": ["give {player} minecraft:emerald 1"],
      "白金": ["give {player} minecraft:golden_apple 1"],
      "钻石": ["give {player} minecraft:totem_of_undying 1"],
      "大师": ["give {player} minecraft:netherite_ingot 2"]
    },
    "2v2singles": {
      "青铜": ["give {player} minecraft:bread 5"],
      "白银": ["give {player} minecraft:gold_nugget 10"],
      "黄金": ["give {player} minecraft:emerald 1"],
      "白金": ["give {player} minecraft:golden_apple 1"],
      "钻石": ["give {player} minecraft:totem_of_undying 1"],
      "大师": ["give {player} minecraft:netherite_ingot 2"]
    }
  }
  },
  "rankTitles": { // Elo 段位划分
    "3500": "大师",
    "3000": "钻石",
    "2500": "白金",
    "2000": "黄金",
    "1500": "白银",
    "0": "青铜"
  },
  "rankRequirements": { // 每个段位奖励领取的最小胜率要求（0.0 ~ 1.0）
    "青铜": 0.0,
    "白银": 0.3,
    "黄金": 0.3,
    "白金": 0.3,
    "钻石": 0.3,
    "大师": 0.3
  },
  "enableCrossServer": true,       // 是否启用跨服匹配
  "cloudServerId": "server",       // 本服的云端标识(不可与他人重复)
  "cloudToken": "",                // 云端验证用密钥(公开云服留空即可)
  "cloudApiUrl": "http://139.196.103.55:8000",  // 云端 API 地址(ip或者域名都可以)
  "cloudWebSocketUrl": "ws://139.196.103.55:8000/ws/" // 云端 WebSocket 地址(ip或者域名都可以)
}
```
</details>

<details>
<summary>Click to expand `database.json`</summary>

```json
{
	// 数据库类型：'sqlite' 或 'mysql'
	"databaseType": "sqlite",
	// SQLite 数据库文件路径
	"sqliteFile": "ranked.db",
	// MySQL 配置
	"mysql": {
		// MySQL 主机地址
		"host": "localhost",
		// MySQL 端口
		"port": 3306,
		// MySQL 数据库名
		"database": "cobblemon_ranked",
		// MySQL 用户名
		"username": "root",
		// MySQL 密码
		"password": "",
		// MySQL 连接池大小
		"poolSize": 10,
		// MySQL 连接超时时间（毫秒）
		"connectionTimeout": 5000,
		// MySQL 额外连接参数
		"parameters": "useSSL=false&serverTimezone=UTC&characterEncoding=utf8"
	}
}
