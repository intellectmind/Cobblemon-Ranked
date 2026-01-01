**Other languages: [English](README.md)｜[中文](README_zh.md)**

[FAQ](https://github.com/intellectmind/Cobblemon-Ranked/wiki/FAQ)

---

# 📊 CobblemonRanked Ranked System Documentation

> This mod only needs to be installed on the server side.  
> After the client is installed, pressing the `X` key will open the graphical GUI by default.    
> 💡 In order to use the `enableTeamPreview` feature, this mod is also required on the client side.  

---

## 🎯 Features Overview

- Built-in multi-language support (Chinese & English), easy to extend  
- Configurable battle arenas with auto-teleport and return  
- Customizable rank titles and Elo thresholds  
- Supports three modes: **Singles**, **Doubles**, and **2v2singles**  
- Elo ranking system calculated independently per format  
- Independent reward system per format with customizable commands  
- Built-in season system with automatic rotation and data reset  
- Elo-based matchmaking queue with optional waiting-time-based relaxation  
- Disconnects are treated as losses; Elo is deducted  
- Fully GUI-driven with clickable text menus and graphical GUI  
- Cross-server matchmaking support
- After enableTeamPreview is enabled, if the number of Pokémon exceeds the set limit, the interface for selecting Pokémon to battle will be triggered. If the number does not exceed the limit, the interface will not be triggered.  
For example, if singlesPickCountNumber is set to 3:  
Player 1 has 5 Pokémon, which will trigger the selection interface, requiring them to choose 3 for battle.  
Player 2 has only 2 Pokémon, so the selection interface will not be triggered. Instead, after Player 1 completes their selection, the battle will begin directly.  
---

## 🌐 Cross-Server Matchmaking(No longer maintained)

> Available from v1.2.0+ — Supports connecting from **any server or single-player world** (requires **official Minecraft account**)  
> Cross server matching demonstration and configuration: [Youtube](https://youtu.be/V8zmSMrxSuU)  

### ✅ How to Use

1. Enable `enableCrossServer` in the config  
2. Modify `cloudServerId`, which should not be duplicated with other servers (the default `server` may already be used by someone)  
3. Enter `/rank reload` to reload the configuration or restart the server.  
4. Enter `/rank cross start` to connect to the cloud server  

### ⚠️ Current Limitations

- Only **singles** mode is supported  
- Certain items and skill effects may not take effect  
- Players from the same server **won’t be matched together**  

### 🌐 Cross-Server Commands

| Command | Description | Permission |
|--------|-------------|------------|
| `/rank cross start` | Connect to the cloud server | OP |
| `/rank cross stop` | Disconnect from the cloud | OP |
| `/rank cross chat` | Chat with your opponent | All |
| `/rank cross join singles` | Enter singles matchmaking | All |
| `/rank cross leave` | Leave matchmaking queue | All |
| `/rank cross battle move [1-4]` | Use move in battle | All |
| `/rank cross battle switch [1-6]` | Switch Pokémon | All |
| `/rank cross battle forfeit` | Surrender the battle | All |

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
| `/rank gui_info_format <player> <format>` | GUI view of another player's seasonal stats |
| `/rank queue join [format]` | Join a ranked queue |
| `/rank queue leave` | Leave all matchmaking queues |
| `/rank status` | Show your current queue status |
| `/rank info <format> <season>` | Show your stats for the given format and season |
| `/rank info <player> <format> [season]` | View another player's ranking for a specific format and season |
| `/rank top` | View leaderboard for default format and current season |
| `/rank top <format> [season] [page] [count]` | Paginated leaderboard for given format and season |
| `/rank season` | View current season info (start/end time, participation, etc.) |
| `/rank pokemon_usage <season> <page>` | view the usage statistics of Pokémon |

---

## 🛡️ Admin Commands (Requires OP)

| Command | Description |
|--------|-------------|
| `/rank gui_reward` | Opens the reward format selection GUI |
| `/rank gui_reset` | Paginated list of online players to reset rankings |
| `/rank reset <player> <format>` | Reset a player's data for the current season and format |
| `/rank reward <player> <format> <rank>` | Grant a reward to a player for a specific rank |
| `/rank season end` | Force-end the current season |
| `/rank reload` | Reload config files (language, rank settings, etc.) |
| `/rank setseasonname <seasonId> <name>` | Set Season Name |

---

## Placeholder API

%Cobblemon_ranked: elo% - ELO score  
%Cobblemon_ranked: rank_title% - Rank Title  
%Cobblemon_ranked: win_rate% - win rate  
%Cobblemon_ranked: wins% - wins  
%Cobblemon_ranked: losses% - negative field  
%Cobblemon_ranked: total games% - total number of sessions  
%Cobblemon_ranked: stress% - current winning streak  
%Cobblemon_ranked: best_stream% - Best Winning streak  
%Cobblemon_ranked: fle_count% - Number of escapes  
%Cobblemon_ranked: rank% - ranking  
%Cobblemon_ranked: season_name% - season name  
%Cobblemon_ranked: season_id% - Season ID  
%Cobblemon_ranked: season_days_1eft% - remaining days of the season  
%Cobblemon_ranked: season_time_1eft% - remaining time of season  
%Cobblemon_ranked: Next_rank_ elo% - ELO required for the next rank  
%Cobblemon_ranked: Next_rank_name% - Next rank name  
%Cobblemon_ranked: queue_stus% - queue status  

Support dynamic mode parameters (singles/doubles/2v2 singles)  
For example:  
%cobblemon_ranked:elo_singles%  
%cobblemon_ranked:rank_title_doubles%  
%cobblemon_ranked:win_rate_2v2singles%  

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
  "allowDuplicateItems": false,            // Allow duplicate held items in a team
  "enableTeamPreview": true,               // Enable Team Preview and Selection (Bring 6 Pick 3/4) 
  "teamSelectionTime": 90,                 // Time allowed for team selection in seconds
  "singlesPickCount": 3,                   // Number of Pokemon to select for Singles (usually 3) 
  "doublesPickCount": 4,                   // Number of Pokemon to select for Doubles (usually 4) 
  "maxEloDiff": 200,                       // Max Elo gap for matchmaking
  "maxQueueTime": 300,                     // Max wait time (seconds) before relaxing Elo rules
  "maxEloMultiplier": 3.0,                 // Max multiplier for Elo diff relaxation
  "seasonDuration": 30,                    // Season duration (days)
  "initialElo": 1000,                      // Elo at the beginning of a season
  "eloKFactor": 32,                        // Elo K-factor (affects Elo change magnitude)
  "minElo": 0,                             // Minimum Elo floor
  "loserProtectionRate": 1.0,              // Loser protection rate (0.0 to 1.0). Loser will lose at most (winner_gain * loserProtectionRate). 1.0 = no protection
  "bannedPokemon": ["Mewtwo", "Arceus"],   // Banned Pokémon (e.g., legendaries)
  "bannedHeldItems": ["cobblemon:leftovers"], // Banned held items for Pokémon
  "bannedCarriedItems": ["cobblemon:leftovers"], // Banned items in player's inventory
  "bannedMoves": ["leechseed"],            // Banned moves for Pokémon
  "bannedNatures": ["cobblemon:naughty"],  // Banned personalities for Pokémon
  "bannedAbilities": [],                   // Banned abilities for Pokémon
  "bannedGenders": ["MALE"],               // Banned gender for Pokémon
  "bannedShiny": false,                    // Banned shiny Pokémon from participating in battles
  "banUsageBelow": 0.0,					   // Ban Pokémon with usage rate below this threshold (0 to disable, 0.1 = 10%)
  "banUsageAbove": 0.0,					   // Ban Pokémon with usage rate above this threshold (0 to disable, 0.1 = 10%) 
  "banTopUsed": 0,						   // Ban top N most used Pokémon (0 to disable)
  "onlyBaseFormWithEvolution": false,	   // Only allow base form Pokémon that can evolve
  "allowedFormats": ["singles", "doubles", "2v2singles"], // Supported battle formats
  "maxLevel": 0,                           // Max Pokémon level (0 = no limit)
  "allowDuplicateSpecies": false,          // Whether duplicate Pokémon species are allowed
  "enableCustomLevel": false,              // Enable forced modification of Pokémon levels
  "customBattleLevel": 50,                 // Forcefully modify the level of Pokémon
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
  "victoryRewards": [                      // Victory rewards configuration (executed after each win)
    "give {player} minecraft:experience_bottle 5",
    "give {player} minecraft:emerald 1"
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
    },
    "2v2singles": {
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
  },
  "rankRequirements": {              // Minimum winning rate requirement for each rank reward（0.0 ~ 1.0）
    "Bronze": 0.0,
    "Silver": 0.3,
    "Gold": 0.3,
    "Platinum": 0.3,
    "Diamond": 0.3,
    "Master": 0.3
  },
  "enableCrossServer": true,       // Enable cross-server matchmaking
  "cloudServerId": "server",       // Cloud server ID for this server(Cannot be repeated with others)
  "cloudToken": "",                // Cloud server auth token(Leave blank for the public cloud server)
  "cloudApiUrl": "http://139.196.103.55:8000",  // Cloud API address(Either IP or domain name is acceptable)
  "cloudWebSocketUrl": "ws://139.196.103.55:8000/ws/" // Cloud WebSocket Address(Either IP or domain name is acceptable)
}
```
</details>

<details>
<summary>Click to expand `database.json`</summary>

```json
{
	// Database type: 'sqlite' or 'mysql'
	"databaseType": "sqlite",
	// SQLite database file path (relative to config folder)
	"sqliteFile": "ranked.db",
	// MySQL configuration
	"mysql": {
		// MySQL host address
		"host": "localhost",
		// MySQL port
		"port": 3306,
		// MySQL database name
		"database": "cobblemon_ranked",
		// MySQL username
		"username": "root",
		// MySQL password
		"password": "",
		// MySQL connection pool size
		"poolSize": 10,
		// MySQL connection timeout (ms)
		"connectionTimeout": 5000,
		// Additional MySQL connection parameters
		"parameters": "useSSL=false&serverTimezone=UTC&characterEncoding=utf8"
	}
}
