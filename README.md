**Read this in other languages: [English](README.md)，[中文](README_zh.md)。**

---

# 📊 CobblemonRanked Ranked System Documentation

## 🎯 Feature Overview

- ✅ Built-in support for Chinese and English messages (multi-language expandable)
- ✅ Multiple battle arena locations (players auto-teleported in/out)
- ✅ Customizable rank names and Elo score thresholds (flexible additions/removals)
- ✅ Supports 1v1 and 2v2 (round-robin) formats
- ✅ Elo rating system (independent per format)
- ✅ Reward system per format with customizable commands
- ✅ Automated season system (rotation and reward resets)
- ✅ Matchmaking queue with Elo difference control and dynamic range expansion
- ✅ Double Elo penalty for quitting/disconnection; opponent Elo unchanged
- ✅ Custom team system for 2v2
- ✅ Clickable GUI interfaces for convenience

---

## 📌 Command Overview

| Command | Description |
|--------|-------------|
| `/rank gui` | Open the main GUI menu |
| `/rank gui_top` | Open leaderboard selection |
| `/rank gui_info` | View your detailed battle info |
| `/rank gui_info_players` | View other players' battle info |
| `/rank gui_queue` | Matchmaking shortcut GUI |
| `/rank gui_invite <page>` | Invite others for 2v2 (paginated) |
| `/rank gui_reward` | Admin GUI for reward distribution |
| `/rank gui_reward_format <format>` | Distribute rewards by format |
| `/rank gui_info_format <player> <format>` | View specific player info in format |
| `/rank gui_myinfo` | View your full battle history |
| `/rank gui_reset` | Admin GUI to reset player data |
| `/rank reset <player> <format>` | Reset a player's Elo in format |
| `/rank reload` | Reload config file without restart |
| `/rank queue join [format]` | Join matchmaking (default to config mode) |
| `/rank queue leave` | Leave all queues |
| `/rank info <format> <season>` | View your season stats |
| `/rank info <player> <format> [season]` | View other player's stats |
| `/rank top [format] [season] [page] [amount]` | Paginated leaderboard |
| `/rank season` | View current season |
| `/rank season end` | End current season (admin only) |
| `/rank reward <player> <format> <rank>` | Give rank reward (admin only) |
| `/rank duo invite <player>` | Send 2v2 team invite |
| `/rank duo leave` | Leave or disband 2v2 team |
| `/rank duo accept` | Accept 2v2 invite |
| `/rank duo status` | View current 2v2 queue/team status |

---

## ⚙️ Configuration File (`cobblemon_ranked.json`)

<details>
<summary>Click to expand full JSON with comments</summary>

```json
{
  // Default language: 'en' (English), or 'zh' (Chinese)
  "defaultLang": "en",

  // Default battle format used when not specified
  "defaultFormat": "1v1",

  // Minimum number of Pokémon allowed in a team
  "minTeamSize": 1,

  // Maximum number of Pokémon allowed in a team
  "maxTeamSize": 6,

  // Max allowed Elo difference for matchmaking
  "maxEloDiff": 200,

  // Max time (seconds) before Elo diff expands
  "maxQueueTime": 300,

  // Max Elo range multiplier (scales with wait time)
  "maxEloMultiplier": 3.0,

  // Days per season before it resets
  "seasonDuration": 30,

  // Starting Elo for every new season
  "initialElo": 1000,

  // K-factor for Elo calculations (affects how much Elo changes)
  "eloKFactor": 32,

  // Lowest possible Elo score (floor)
  "minElo": 0,

  // List of banned Pokémon names
  "bannedPokemon": ["Mewtwo", "Arceus"],

  // Allowed match formats
  "allowedFormats": ["1v1", "2v2"],

  // Max Pokémon level allowed (0 = no limit)
  "maxLevel": 0,

  // Allow duplicate species in a team (e.g., two Pikachus)
  "allowDuplicateSpecies": false,

  // List of arena coordinates to teleport to after matching
  "battleArenas": [
    {
      "world": "minecraft:overworld",
      "playerPositions": [
        { "x": 0.0, "y": 70.0, "z": 0.0 },
        { "x": 10.0, "y": 70.0, "z": 0.0 },
        { "x": 0.0, "y": 70.0, "z": 10.0 },
        { "x": 10.0, "y": 70.0, "z": 10.0 }
      ]
    },
    {
      "world": "minecraft:overworld",
      "playerPositions": [
        { "x": 100.0, "y": 65.0, "z": 100.0 },
        { "x": 110.0, "y": 65.0, "z": 100.0 },
        { "x": 100.0, "y": 65.0, "z": 110.0 },
        { "x": 110.0, "y": 65.0, "z": 110.0 }
      ]
    }
  ],

  // Rank rewards per format (custom commands using {player})
  "rankRewards": {
    "1v1": {
      "Bronze": ["give {player} minecraft:apple 5"],
      "Silver": ["give {player} minecraft:golden_apple 3"],
      "Gold": [
        "give {player} minecraft:diamond 2",
        "give {player} minecraft:emerald 5"
      ],
      "Platinum": [
        "give {player} minecraft:diamond_block 1",
        "effect give {player} minecraft:strength 3600 1"
      ],
      "Diamond": [
        "give {player} minecraft:netherite_ingot 1",
        "give {player} minecraft:elytra 1"
      ],
      "Master": [
        "give {player} minecraft:netherite_block 2",
        "give {player} minecraft:totem_of_undying 1",
        "effect give {player} minecraft:resistance 7200 2"
      ]
    },
    "2v2": {
      "Bronze": ["give {player} minecraft:bread 5"],
      "Silver": ["give {player} minecraft:gold_nugget 10"],
      "Gold": ["give {player} minecraft:emerald 1"],
      "Platinum": ["give {player} minecraft:golden_apple 1"],
      "Diamond": ["give {player} minecraft:totem_of_undying 1"],
      "Master": ["give {player} minecraft:netherite_ingot 2"]
    }
  },

  // Elo thresholds for each rank (descending order)
  "rankTitles": {
    "3500": "Master",
    "3000": "Diamond",
    "2500": "Platinum",
    "2000": "Gold",
    "1500": "Silver",
    "0": "Bronze"
  }
}
