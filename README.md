**Other languages: [English](README.md)｜[中文](README_zh.md)**

---

# 📊 CobblemonRanked Ranked System Documentation

💡 *This mod only needs to be installed on the server side.*

---

## 🎯 Features Overview

### ✅ Completed Features

- Built-in multi-language support (Chinese & English), easy to extend
- Configurable battle arenas with auto-teleport and return
- Customizable rank titles and Elo thresholds
- Supports Singles (1v1) and Doubles (2v2) formats
- Elo ranking system calculated independently per format
- Independent reward system per format with customizable commands
- Built-in season system with automatic rotation and data reset
- Elo-based matchmaking queue with optional waiting-time-based relaxation
- Disconnects are treated as losses; Elo is deducted, opponent's Elo remains unchanged
- Fully GUI-driven with clickable menu entries

### 🔧 Planned Features

- [ ] 2v2 gauntlet (multi-battle) mode
- [ ] Client-side visual GUI
- [ ] Cross-server matchmaking support
- [ ] Flee penalty: double loss, no Elo gain for the opponent

---

## 📌 Command Overview

> All commands start with `/rank`

---

## 🎮 Player Commands

| Command | Description |
|--------|-------------|
| `/rank gui` | Opens the main menu GUI |
| `/rank gui_top` | Opens the leaderboard format selection GUI |
| `/rank gui_info` | View your detailed Elo stats |
| `/rank gui_info_players` | Paginated list of online players to inspect their rankings |
| `/rank gui_myinfo` | Quick access to your own ranking |
| `/rank gui_queue` | Opens the matchmaking menu |
| `/rank gui_reward_format <format>` | Shows unclaimed rewards for a specific format |
| `/rank gui_info_format <player> <format>` | GUI view of another player's seasonal stats |
| `/rank queue join [format]` | Join a ranked queue (default or specify format: `singles`, `doubles`) |
| `/rank queue leave` | Leave all matchmaking queues |
| `/rank status` | Show your current queue status |
| `/rank info <format> <season>` | Show your stats for the given format and season |
| `/rank info <player> <format> [season]` | View another player's ranking for a specific format and season |
| `/rank top` | View leaderboard for default format and current season |
| `/rank top <format> [season] [page] [count]` | Paginated leaderboard for given format and season |
| `/rank season` | View current season info (start/end time, participation, etc.) |

---

## 🛡️ Admin Commands (Requires OP)

| Command | Description |
|--------|-------------|
| `/rank gui_reward` | Opens the reward format selection GUI |
| `/rank gui_reset` | Paginated list of online players to reset rankings |
| `/rank reset <player> <format>` | Reset a player's data for the current season and format |
| `/rank reward <player> <format> <rank>` | Grant a reward to a player for a specific rank (auto-suggestion supported) |
| `/rank season end` | Force-end the current season |
| `/rank reload` | Reload config files (language, rank settings, etc.) |

---

## ⚙️ Configuration File Reference (`cobblemon_ranked.json`)

<details>
<summary>Click to expand full config reference (with inline comments)</summary>

```json
{
  "defaultLang": "en",                     // Default language: 'en' or 'zh'
  "defaultFormat": "singles",              // Default battle format
  "minTeamSize": 1,                        // Minimum Pokémon per team
  "maxTeamSize": 6,                        // Maximum Pokémon per team
  "maxEloDiff": 200,                       // Max Elo gap for matchmaking
  "maxQueueTime": 300,                     // Max wait time (seconds) before relaxing Elo rules
  "maxEloMultiplier": 3.0,                 // Max multiplier for Elo diff relaxation
  "seasonDuration": 30,                    // Season duration (days)
  "initialElo": 1000,                      // Elo at the beginning of a season
  "eloKFactor": 32,                        // Elo K-factor (affects Elo change magnitude)
  "minElo": 0,                             // Minimum Elo floor
  "bannedPokemon": ["Mewtwo", "Arceus"],  // Banned Pokémon (e.g., legendaries)
  "allowedFormats": ["singles", "doubles"], // Supported battle formats
  "maxLevel": 0,                           // Max Pokémon level (0 = no limit)
  "allowDuplicateSpecies": false,         // Whether duplicate Pokémon species are allowed
  "battleArenas": [                        // List of arenas (teleport locations for battles)
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
  "rankRewards": {                         // Format-specific rank rewards (command-based)
    "singles": {
      "Bronze": ["give {player} minecraft:apple 5"],
      "Silver": ["give {player} minecraft:golden_apple 3"],
      "Gold": ["give {player} minecraft:diamond 2", "give {player} minecraft:emerald 5"],
      "Platinum": ["give {player} minecraft:diamond_block 1", "effect give {player} minecraft:strength 3600 1"],
      "Diamond": ["give {player} minecraft:netherite_ingot 1", "give {player} minecraft:elytra 1"],
      "Master": ["give {player} minecraft:netherite_block 2", "give {player} minecraft:totem_of_undying 1", "effect give {player} minecraft:resistance 7200 2"]
    },
    "doubles": {
      "Bronze": ["give {player} minecraft:bread 5"],
      "Silver": ["give {player} minecraft:gold_nugget 10"],
      "Gold": ["give {player} minecraft:emerald 1"],
      "Platinum": ["give {player} minecraft:golden_apple 1"],
      "Diamond": ["give {player} minecraft:totem_of_undying 1"],
      "Master": ["give {player} minecraft:netherite_ingot 2"]
    }
  },
  "rankTitles": {                          // Elo thresholds → rank names
    "3500": "Master",
    "3000": "Diamond",
    "2500": "Platinum",
    "2000": "Gold",
    "1500": "Silver",
    "0": "Bronze"
  }
}
