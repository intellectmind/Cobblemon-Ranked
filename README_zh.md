**其他语言版本: [English](README.md)｜[中文](README_zh.md)**

---

# 📊 CobblemonRanked 排位系统说明文档

💡 *本模组仅需安装在服务端，无需客户端参与。*

从v1.0.3开始，客户端安装后，默认按Z键可打开简易GUI

---

## 🎯 功能总览

### ✅ 已实现功能

- 支持中英文切换，支持扩展更多语言
- 多个战斗场地配置，自动传送和归位
- 自定义段位名称与 Elo 阈值，灵活配置
- 支持单打、双打、2v2单打3个模式
- 独立 Elo 排名系统，按模式分别计算
- 独立的段位奖励系统，支持自定义指令
- 内置赛季机制，自动轮换与数据重置
- 匹配队列支持 Elo 限制与等待放宽机制
- 掉线按失败处理，扣分
- 提供可点击的 GUI 操作界面

### 🔧 计划中功能

- [ ] 客户端可视化 GUI 界面
- [ ] 跨服务器匹配支持

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
  "maxEloDiff": 200, // 最大 Elo 匹配差值
  "maxQueueTime": 300, // 最大排队等待时间（秒）
  "maxEloMultiplier": 3.0, // Elo 放宽倍率上限
  "seasonDuration": 30, // 赛季持续天数
  "initialElo": 1000, // 初始 Elo 值
  "eloKFactor": 32, // Elo K 系数
  "minElo": 0, // Elo 最低值限制
  "bannedPokemon": ["Mewtwo", "Arceus"], // 禁用宝可梦列表
  "bannedHeldItems": ["cobblemon:leftovers"], // 禁止宝可梦携带的道具
  "allowedFormats": ["singles", "doubles", "2v2singles"], // 支持的对战模式
  "maxLevel": 0, // 宝可梦最大等级（0 表示不限制）
  "allowDuplicateSpecies": false, // 是否允许重复宝可梦
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
  }
}
