import asyncio
import random
from typing import Callable
import time
import uuid

# 属性克制表 (攻击方属性 -> 防御方属性 -> 效果倍率)
TYPE_CHART = {
    "normal": {
        "rock": 0.5,
        "ghost": 0,
        "steel": 0.5,
    },
    "fire": {
        "fire": 0.5,
        "water": 0.5,
        "grass": 2,
        "ice": 2,
        "bug": 2,
        "rock": 0.5,
        "dragon": 0.5,
        "steel": 2
    },
    "water": {
        "fire": 2,
        "water": 0.5,
        "grass": 0.5,
        "ground": 2,
        "rock": 2,
        "dragon": 0.5
    },
    "electric": {
        "water": 2,
        "electric": 0.5,
        "grass": 0.5,
        "ground": 0,
        "flying": 2,
        "dragon": 0.5
    },
    "grass": {
        "fire": 0.5,
        "water": 2,
        "grass": 0.5,
        "poison": 0.5,
        "ground": 2,
        "flying": 0.5,
        "bug": 0.5,
        "rock": 2,
        "dragon": 0.5,
        "steel": 0.5
    },
    "ice": {
        "fire": 0.5,
        "water": 0.5,
        "grass": 2,
        "ice": 0.5,
        "ground": 2,
        "flying": 2,
        "dragon": 2,
        "steel": 0.5
    },
    "fighting": {
        "normal": 2,
        "ice": 2,
        "poison": 0.5,
        "flying": 0.5,
        "psychic": 0.5,
        "bug": 0.5,
        "rock": 2,
        "ghost": 0,
        "dark": 2,
        "steel": 2,
        "fairy": 0.5
    },
    "poison": {
        "grass": 2,
        "poison": 0.5,
        "ground": 0.5,
        "rock": 0.5,
        "ghost": 0.5,
        "steel": 0,
        "fairy": 2
    },
    "ground": {
        "fire": 2,
        "electric": 2,
        "grass": 0.5,
        "poison": 2,
        "flying": 0,
        "bug": 0.5,
        "rock": 2,
        "steel": 2
    },
    "flying": {
        "electric": 0.5,
        "grass": 2,
        "fighting": 2,
        "bug": 2,
        "rock": 0.5,
        "steel": 0.5
    },
    "psychic": {
        "fighting": 2,
        "poison": 2,
        "psychic": 0.5,
        "dark": 0,
        "steel": 0.5
    },
    "bug": {
        "fire": 0.5,
        "grass": 2,
        "fighting": 0.5,
        "poison": 0.5,
        "flying": 0.5,
        "psychic": 2,
        "ghost": 0.5,
        "dark": 2,
        "steel": 0.5,
        "fairy": 0.5
    },
    "rock": {
        "fire": 2,
        "ice": 2,
        "fighting": 0.5,
        "ground": 0.5,
        "flying": 2,
        "bug": 2,
        "steel": 0.5
    },
    "ghost": {
        "normal": 0,
        "psychic": 2,
        "ghost": 2,
        "dark": 0.5
    },
    "dragon": {
        "dragon": 2,
        "steel": 0.5,
        "fairy": 0
    },
    "dark": {
        "fighting": 0.5,
        "psychic": 2,
        "ghost": 2,
        "dark": 0.5,
        "fairy": 0.5
    },
    "steel": {
        "fire": 0.5,
        "water": 0.5,
        "electric": 0.5,
        "ice": 2,
        "rock": 2,
        "steel": 0.5,
        "fairy": 2
    },
    "fairy": {
        "fire": 0.5,
        "fighting": 2,
        "poison": 0.5,
        "dragon": 2,
        "dark": 2,
        "steel": 0.5
    },
}

# 状态效果
STATUS_EFFECTS = {
    "par": {"name": "麻痹", "damage": 0, "chance": 0.25, "stat_mod": {"speed": 0.5}, "can_attack_chance": 0.75},
    "brn": {"name": "灼伤", "damage": 0.0625, "stat_mod": {"atk": 0.5}},
    "psn": {"name": "中毒", "damage": 0.125, "turn_damage_increase": False},
    "badpsn": {"name": "剧毒", "damage": lambda turn: min(0.0625 * turn, 0.5), "turn_damage_increase": True},
    "slp": {"name": "睡眠", "turns": (1, 3), "chance_to_wake": 0.33, "can_attack": False},
    "frz": {"name": "冰冻", "chance_to_thaw": 0.20, "thaw_on_fire_hit": True},
    "cfs": {"name": "混乱", "turns": (1, 4), "chance_to_hurt": 0.33, "damage": 0.25},
    "fln": {"name": "畏缩", "turns": 1},
    "att": {"name": "着迷", "turns": (1, 3), "chance_to_fail": 0.5},
    "trap": {"name": "束缚", "damage": 0.125},
    "curse": {"name": "诅咒", "damage": 0.25}
}

# 要害段数与概率映射表
CRITICAL_HIT_RATES = {
    0: 1 / 16,
    1: 1 / 8,
    2: 1 / 4,
    3: 1 / 3,
    4: 1 / 2
}

# 特性效果定义
ABILITY_EFFECTS = {
    "overgrow": {
        "name": "茂盛",
        "trigger": "hp_low",
        "effect": {
            "type": "move_power_boost",
            "move_types": ["grass"],
            "multiplier": 1.5
        }
    },
    "blaze": {
        "name": "猛火",
        "trigger": "hp_low",
        "effect": {
            "type": "move_power_boost",
            "move_types": ["fire"],
            "multiplier": 1.5
        }
    },
    "torrent": {
        "name": "激流",
        "trigger": "hp_low",
        "effect": {
            "type": "move_power_boost",
            "move_types": ["water"],
            "multiplier": 1.5
        }
    },
    "swiftswim": {
        "name": "湿滑",
        "trigger": "weather_rain",
        "effect": {
            "type": "stat_boost",
            "stat": "spe",
            "multiplier": 2
        }
    },
    "chlorophyll": {
        "name": "叶绿素",
        "trigger": "weather_sunny",
        "effect": {
            "type": "stat_boost",
            "stat": "spe",
            "multiplier": 2
        }
    },
    "hugepower": {
        "name": "大力士",
        "trigger": "passive",
        "effect": {
            "type": "stat_boost",
            "stat": "atk",
            "multiplier": 2
        }
    },
    "guts": {
        "name": "根性",
        "trigger": "statused",
        "effect": {
            "type": "stat_boost",
            "stat": "atk",
            "multiplier": 1.5
        }
    },
    "marvelscale": {
        "name": "神秘鳞片",
        "trigger": "statused",
        "effect": {
            "type": "stat_boost",
            "stat": "def",
            "multiplier": 1.5
        }
    },
    "plus": {
        "name": "正极",
        "trigger": "partner_minus",
        "effect": {
            "type": "stat_boost",
            "stat": "spa",
            "multiplier": 1.5
        }
    },
    "minus": {
        "name": "负极",
        "trigger": "partner_plus",
        "effect": {
            "type": "stat_boost",
            "stat": "spa",
            "multiplier": 1.5
        }
    },
    "owntempo": {
        "name": "我行我素",
        "trigger": "passive",
        "effect": {
            "type": "stat_boost",
            "stat": "atk",
            "multiplier": 1.5
        }
    },
    "solarpower": {
        "name": "太阳力量",
        "trigger": "weather_sunny",
        "effect": {
            "type": "stat_boost",
            "stat": "spa",
            "multiplier": 1.5
        }
    },
    "quickfeet": {
        "name": "早足",
        "trigger": "statused",
        "effect": {
            "type": "stat_boost",
            "stat": "spe",
            "multiplier": 1.5
        }
    },
    "slowstart": {
        "name": "缓慢启动",
        "trigger": "switch_in",
        "effect": {
            "type": "stat_mod",
            "stats": ["atk", "spa", "spe"],
            "multiplier": 0.5,
            "turns": 5
        }
    },
    "flowergift": {
        "name": "开花",
        "trigger": "weather_sunny",
        "effect": {
            "type": "stat_boost",
            "stats": ["atk", "spd"],
            "multiplier": 1.5
        }
    },
    "levitate": {
        "name": "飘浮",
        "trigger": "passive",
        "effect": {
            "type": "immunity",
            "types": ["ground"]
        }
    },
    "immunity": {
        "name": "免疫",
        "trigger": "passive",
        "effect": {
            "type": "status_immunity",
            "statuses": ["psn", "badpsn"]
        }
    },
    "voltabsorb": {
        "name": "蓄电",
        "trigger": "damage_taken",
        "effect": {
            "type": "heal_on_damage",
            "move_type": "electric",
            "multiplier": 0.25
        }
    },
    "drought": {
        "name": "干旱",
        "trigger": "switch_in",
        "effect": {
            "type": "weather_change",
            "weather": "sunny"
        }
    },
    "sandstream": {
        "name": "扬沙",
        "trigger": "switch_in",
        "effect": {
            "type": "weather_change",
            "weather": "sandstorm"
        }
    }
}

class BattleInstance:
    def __init__(self, battle_id: str, player1: dict, player2: dict, mode: str, log_callback: Callable):
        self.battle_id = battle_id
        self.mode = mode
        self.turn = 0
        self.weather = None
        self.terrain = None
        self.ended = False
        self.winner = None
        self.log_callback = log_callback
        self.start_time = time.time()
        self.state_update_callback = None  # 状态更新回调
        self.action_choices = {player1["player_id"]: False, player2["player_id"]: False}  # 追踪玩家行动选择状态
        self.turn_timeout = asyncio.get_event_loop().call_later(
            180,  # 3分钟超时
            self.handle_turn_timeout
        )  # 回合超时任务
        self.turn_start_time = time.time()  # 回合开始时间

        # 初始化玩家状态
        self.players = {
            player1["player_id"]: {
                "id": player1["player_id"],
                "name": player1["player_name"],
                "team": [self._init_pokemon(p) for p in player1["pokemons"]],
                "active": 0,
                "action": None,
                "switch_pending": False
            },
            player2["player_id"]: {
                "id": player2["player_id"],
                "name": player2["player_name"],
                "team": [self._init_pokemon(p) for p in player2["pokemons"]],
                "active": 0,
                "action": None,
                "switch_pending": False
            }
        }

        # 使用事件记录对战开始
        self._log_event("battle_start", {
            "player1": player1['player_name'],
            "player2": player2['player_name'],
            "pokemon1": self.get_active(player1['player_id'])['name'],
            "pokemon2": self.get_active(player2['player_id'])['name']
        })

        # +++ 检查宝可梦数据是否超过500 +++
        self._check_pokemon_stats()
        if self.ended:  # 如果已因数据超限结束战斗，则直接返回
            return

    def _check_pokemon_stats(self):
        """检查宝可梦数据是否超过500以及等级，超过则判负"""
        MAX_STAT = 500  # 最大允许值
        MAX_LEVEL = 100  # 最大等级

        for player_id, player_data in self.players.items():
            for pokemon in player_data["team"]:
                # 检查等级是否超过100
                if pokemon["level"] > MAX_LEVEL:
                    # 该玩家因宝可梦数据超限判负
                    opponent_id = next(pid for pid in self.players if pid != player_id)
                    self.ended = True
                    self.winner = self.players[opponent_id]["id"]

                    # 记录事件
                    self._log_event("cheat_detected", {
                        "player": player_data['name'],
                        "pokemon": pokemon['name'],
                        "stat": "level",
                        "value": pokemon['level']
                    })

                    # 设置结果待处理
                    self.result_pending = True
                    self.result = {
                        "battle_id": self.battle_id,
                        "winner": opponent_id,
                        "loser": player_id,
                        "winner_name": self.players[opponent_id]['name'],
                        "loser_name": player_data['name']
                    }
                    return  # 发现一个超限即结束检查

                # 检查六项基础数据
                stats_to_check = [
                    ("hp", pokemon["hp"]),
                    ("atk", pokemon["atk"]),
                    ("def", pokemon["def"]),
                    ("spa", pokemon["spa"]),
                    ("spd", pokemon["spd"]),
                    ("spe", pokemon["spe"]),
                ]

                for stat_name, value in stats_to_check:
                    if value > MAX_STAT:
                        # 该玩家因宝可梦数据超限判负
                        opponent_id = next(pid for pid in self.players if pid != player_id)
                        self.ended = True
                        self.winner = self.players[opponent_id]["id"]

                        # 记录事件
                        self._log_event("cheat_detected", {
                            "player": player_data['name'],
                            "pokemon": pokemon['name'],
                            "stat": stat_name,
                            "value": value
                        })

                        # 设置结果待处理
                        self.result_pending = True
                        self.result = {
                            "battle_id": self.battle_id,
                            "winner": opponent_id,
                            "loser": player_id,
                            "winner_name": self.players[opponent_id]['name'],
                            "loser_name": player_data['name']
                        }
                        return  # 发现一个超限即结束检查

    def execute_switch(self, player_id: str, action: dict):
        """执行更换宝可梦"""
        player_data = self.players[player_id]
        slot = action["slot"] - 1
        player_data["switch_pending"] = True
        player_data["switch_slot"] = slot
        player_data["action"] = {"type": "switch"}  # 标记行动已选择
        self.action_choices[player_id] = True

    def _log_event(self, event_type: str, data: dict = None):
        """记录对战事件"""
        if self.log_callback:
            message = {"event_type": event_type}
            if data:
                message.update(data)
            self.log_callback(message)

    def _init_pokemon(self, pokemon: dict) -> dict:
        """初始化宝可梦战斗状态"""
        name = pokemon.get("name", "未知宝可梦")
        types = pokemon.get("types", ["normal"])

        return {
            "id": pokemon.get("uuid", str(uuid.uuid4())),
            "name": name,
            "name_key": pokemon.get("name_key", "未知宝可梦"),
            "types": types,
            "level": pokemon.get("level", 50),
            "hp": pokemon.get("hp", 100),
            "max_hp": pokemon.get("max_hp", 100),
            "atk": pokemon.get("attack", 100),
            "def": pokemon.get("defense", 100),
            "spa": pokemon.get("special_attack", 100),
            "spd": pokemon.get("special_defense", 100),
            "spe": pokemon.get("speed", 100),
            "ability": pokemon.get("ability", ""),
            "ability_activated": False,
            "item": pokemon.get("item", ""),
            "moves": self._format_moves(pokemon.get("moves", [])),
            "status": None,
            "volatile": {
                "ability_effects": {},
                "confusion": None  # 混乱状态存储
            },
            "stat_mod": {
                "atk": 0,
                "def": 0,
                "spa": 0,
                "spd": 0,
                "spe": 0,
                "accuracy": 0,
                "evasion": 0
            },
            "last_move": None,
            "critical_hit_stage": 0,
            "slow_start_turns": 0,
            "status_turns": 0,  # 状态持续回合计数器
            "toxic_counter": 0  # 剧毒专用计数器
        }

    def _format_moves(self, moves: list) -> list:
        """格式化招式数据以适应战斗引擎"""
        formatted = []
        for move in moves:
            formatted.append({
                "name": move.get("name", "未知招式"),
                "type": move.get("type", "normal"),
                "category": move.get("category", "physical"),
                "power": move.get("power", 0),
                "accuracy": move.get("accuracy", 100),
                "priority": move.get("priority", 0),
                "current_pp": move.get("current_pp", move.get("max_pp", 10)),
                "max_pp": move.get("max_pp", 10),
                "critical_hit_stage": move.get("critical_hit_stage", 0),
                "secondary": move.get("secondary", {})
            })
        return formatted

    def _trigger_ability(self, pokemon: dict, trigger: str, context: dict = None):
        """触发宝可梦特性"""
        if not pokemon["ability"]:
            return

        ability_data = ABILITY_EFFECTS.get(pokemon["ability"])
        if not ability_data:
            return

        # 检查触发条件
        if ability_data["trigger"] != trigger:
            return

        # 应用特性效果
        effect = ability_data["effect"]
        pokemon["ability_activated"] = True

        # 记录特性触发日志
        self._log_event("ability_triggered", {
            "pokemon": pokemon['name'],
            "ability": ability_data['name']
        })

        # 根据特性类型应用效果
        if effect["type"] == "stat_boost":
            # 处理能力提升类特性
            stats = effect.get("stats", [effect["stat"]])
            for stat in stats:
                # 创建或更新特性效果
                key = f"{stat}_boost"
                pokemon["volatile"]["ability_effects"][key] = {
                    "multiplier": effect["multiplier"],
                    "trigger": trigger
                }

        elif effect["type"] == "stat_mod":
            # 处理能力变化类特性（如缓慢启动）
            stats = effect["stats"]
            turns = effect.get("turns", 0)
            for stat in stats:
                key = f"{stat}_mod"
                pokemon["volatile"]["ability_effects"][key] = {
                    "multiplier": effect["multiplier"],
                    "turns": turns,
                    "trigger": trigger
                }

                # 如果是缓慢启动特性，初始化回合计数器
                if pokemon["ability"] == "slowstart":
                    pokemon["slow_start_turns"] = turns

        elif effect["type"] == "move_power_boost":
            # 处理招式威力提升特性
            pokemon["volatile"]["ability_effects"]["move_power_boost"] = {
                "multiplier": effect["multiplier"],
                "move_types": effect["move_types"],
                "trigger": trigger
            }

        elif effect["type"] == "immunity":
            # 处理免疫类特性
            immunities = effect.get("immunities", [])
            if "immunities" not in pokemon["volatile"]["ability_effects"]:
                pokemon["volatile"]["ability_effects"]["immunities"] = []
            pokemon["volatile"]["ability_effects"]["immunities"].extend(immunities)

        elif effect["type"] == "status_immunity":
            # 添加状态免疫
            status_immunities = pokemon["volatile"].setdefault("status_immunities", [])
            status_immunities.extend(effect["statuses"])

        elif effect["type"] == "weather_change":
            # 改变天气
            self.weather = effect["weather"]
            self._log_event("weather_changed", {"weather": self.weather})

        elif effect["type"] == "heal_on_damage":
            # 受到特定属性攻击时恢复HP
            pokemon["volatile"]["ability_effects"]["heal_on_damage"] = {
                "move_type": effect["move_type"],
                "multiplier": effect["multiplier"]
            }

    def _get_player_name_by_pokemon(self, pokemon: dict) -> str:
        """通过宝可梦获取玩家名称"""
        for player_id, player_data in self.players.items():
            if pokemon in player_data["team"]:
                return player_data["name"]
        return "未知训练家"

    def _get_player_by_id(self, player_id: str) -> dict:
        """通过ID获取玩家数据"""
        return self.players.get(player_id)

    def get_active(self, player_id: str) -> dict:
        """获取玩家当前出战的宝可梦"""
        player = self.players[player_id]
        return player["team"][player["active"]]

    def set_action(self, player_id: str, action: dict):
        """设置玩家行动"""
        self.players[player_id]["action"] = action
        self.action_choices[player_id] = True

        # 通知对手玩家已选择行动
        self._log_event("opponent_action_taken", {
            "player": self.players[player_id]['name']
        })

    def can_proceed(self) -> bool:
        """检查是否双方都已选择行动"""
        return all(p["action"] is not None for p in self.players.values())

    def process_turn(self):
        """处理一个回合"""
        if self.ended:
            return

        # 取消当前回合的超时任务
        if self.turn_timeout:
            self.turn_timeout.cancel()
            self.turn_timeout = None

        self.turn += 1
        self._log_event("turn_start", {"turn": self.turn})

        # 0. 检查状态伤害
        for player_id, player_data in self.players.items():
            pokemon = self.get_active(player_id)
            self._apply_status_damage(pokemon, player_data["name"])

        # 1. 检查胜负条件
        if self._check_win_conditions():
            return

        # 2. 确定行动顺序
        actions = self._determine_action_order()

        # 3. 执行行动
        for action in actions:
            if self.ended:
                break

            player_id = action["player_id"]
            action_data = self.players[player_id]["action"]

            # 检查是否是更换操作
            if self.players[player_id]["switch_pending"]:
                # 执行实际更换
                slot = self.players[player_id]["switch_slot"]
                self._perform_switch(player_id, slot)
                self.players[player_id]["switch_pending"] = False
                continue  # 更换后跳过其他行动

            # 检查状态是否阻止行动
            pokemon = self.get_active(player_id)
            prevented = False

            if pokemon["status"] == "slp":
                self._handle_sleep_status(pokemon)
                prevented = True
            elif pokemon["status"] == "frz":
                self._handle_frozen_status(pokemon)
                prevented = True
            elif pokemon["status"] == "par":
                prevented = self._handle_paralysis_status(pokemon)

            # +++ 混乱状态检查 +++
            if not prevented and "confusion" in pokemon["volatile"]:
                # 混乱状态存在，检查是否自伤
                if self._check_confusion(pokemon):
                    # 混乱自伤后跳过行动
                    prevented = True

            # 如果状态阻止行动，跳过行动执行
            if prevented:
                continue

            if action_data["type"] == "move":
                self._execute_move(player_id, action_data)
            elif action_data["type"] == "switch":
                self._execute_switch(player_id, action_data)
            elif action_data["type"] == "item":
                self._execute_item(player_id, action_data)
            elif action_data["type"] == "forfeit":
                self.ended = True
                opponent_id = next(pid for pid in self.players if pid != player_id)
                self.winner = self.players[opponent_id]["id"]
                self._log_event("battle_ended", {"winner": self.winner})
                return

            # 检查行动后胜负条件
            if self._check_win_conditions():
                break

        # 4. 回合结束状态处理
        if not self.ended:
            for player_id in self.players:
                pokemon = self.get_active(player_id)
                self._end_of_turn_effects(pokemon)

        # 5. 重置行动
        for player in self.players.values():
            player["action"] = None
            player["switch_pending"] = False

        # +++ 重置行动选择状态 +++
        for player_id in self.action_choices:
            self.action_choices[player_id] = False

        # 6. 触发状态更新
        if self.state_update_callback:
            self.state_update_callback()

        # +++ 新回合超时设置 +++
        if not self.ended:
            self.turn_start_time = time.time()
            self.turn_timeout = asyncio.get_event_loop().call_later(
                180,  # 3分钟超时
                self.handle_turn_timeout
            )

    def handle_turn_timeout(self):
        if self.ended:
            return

        # 记录超时日志
        self._log_event("turn_timeout")

        # 查找未选择行动的玩家
        for player_id, has_chosen in self.action_choices.items():
            if not has_chosen:
                player_data = self._get_player_by_id(player_id)
                # 设置该玩家的行动为弃权
                self.set_action(player_id, {"type": "forfeit"})
                self._log_event("timeout_forfeit", {
                    "player": player_data['name']
                })

                # +++ 战斗结束通知 +++
                self.ended = True
                opponent_id = next(pid for pid in self.players if pid != player_id)
                self.winner = self.players[opponent_id]["id"]

                # 获取玩家数据
                winner_data = self.players[opponent_id]
                loser_data = self.players[player_id]

                # 记录战斗结果（需要传递给上层处理）
                self.result = {
                    "battle_id": self.battle_id,
                    "winner": winner_data["id"],
                    "loser": loser_data["id"],
                    "winner_name": winner_data["name"],
                    "loser_name": loser_data["name"]
                }

                # 标记需要处理结果
                self.result_pending = True
                break  # 只处理一个超时玩家

    def _apply_status_damage(self, pokemon: dict, player_name: str):
        """应用异常状态伤害"""
        if not pokemon["status"]:
            return

        status = pokemon["status"]
        status_data = STATUS_EFFECTS.get(status)

        if not status_data:
            return

        # 睡眠和冰冻不造成伤害
        if status in ["slp", "frz"]:
            return

        damage_percent = status_data.get("damage", 0)

        # 剧毒伤害随回合增加
        if status == "badpsn":
            if "toxic_counter" not in pokemon:
                pokemon["toxic_counter"] = 1
            else:
                pokemon["toxic_counter"] += 1

            if callable(damage_percent):
                damage_percent = damage_percent(pokemon["toxic_counter"])

        # 计算并应用伤害
        if damage_percent > 0:
            damage = int(pokemon["max_hp"] * damage_percent)
            pokemon["hp"] = max(0, pokemon["hp"] - damage)

            self._log_event("status_damage", {
                "pokemon": pokemon['name'],
                "status": status,
                "damage": damage
            })

            # 检查是否濒死
            if pokemon["hp"] == 0:
                self._log_event("pokemon_fainted", {
                    "player": player_name,
                    "pokemon": pokemon['name']
                })

    def _handle_sleep_status(self, pokemon: dict):
        """处理睡眠状态"""
        if pokemon["status"] != "slp":
            return

        # 减少睡眠回合数
        if "sleep_turns" not in pokemon:
            # 初始化睡眠回合数
            min_turns, max_turns = STATUS_EFFECTS["slp"]["turns"]
            pokemon["sleep_turns"] = random.randint(min_turns, max_turns)
        else:
            pokemon["sleep_turns"] -= 1

        # 检查是否醒来
        if pokemon["sleep_turns"] <= 0 or random.random() < STATUS_EFFECTS["slp"]["chance_to_wake"]:
            pokemon["status"] = None
            self._log_event("status_ended", {
                "pokemon": pokemon['name'],
                "status": "slp"
            })
        else:
            self._log_event("status_prevents_move", {
                "pokemon": pokemon['name'],
                "status": "slp"
            })

    def _handle_frozen_status(self, pokemon: dict):
        """处理冰冻状态"""
        if pokemon["status"] != "frz":
            return

        # 检查是否解冻
        if random.random() < STATUS_EFFECTS["frz"]["chance_to_thaw"]:
            pokemon["status"] = None
            self._log_event("status_ended", {
                "pokemon": pokemon['name'],
                "status": "frz"
            })
        else:
            self._log_event("status_prevents_move", {
                "pokemon": pokemon['name'],
                "status": "frz"
            })

    def _handle_paralysis_status(self, pokemon: dict):
        """处理麻痹状态"""
        if pokemon["status"] != "par":
            return

        # 检查是否因麻痹无法行动
        if random.random() < STATUS_EFFECTS["par"]["chance"]:
            self._log_event("status_prevents_move", {
                "pokemon": pokemon['name'],
                "status": "par"
            })
            return True  # 无法行动

        return False  # 可以行动

    def _check_confusion(self, pokemon: dict) -> bool:
        """检查混乱状态是否触发自伤"""
        if "confusion" not in pokemon["volatile"]:
            return False

        confusion_data = pokemon["volatile"]["confusion"]

        # +++ 添加安全检查 +++
        if not confusion_data or "turns" not in confusion_data:
            # 如果混乱数据无效，清除混乱状态
            del pokemon["volatile"]["confusion"]
            self._log_event("confusion_data_error", {
                "pokemon": pokemon['name']
            })
            return False

        # 减少混乱回合计数
        confusion_data["turns"] -= 1
        if confusion_data["turns"] <= 0:
            del pokemon["volatile"]["confusion"]
            self._log_event("status_ended", {
                "pokemon": pokemon['name'],
                "status": "cfs"
            })
            return False

        # 检查是否自伤
        if random.random() < confusion_data["chance_to_hurt"]:
            damage = int(pokemon["max_hp"] * confusion_data["damage"])
            pokemon["hp"] = max(0, pokemon["hp"] - damage)

            self._log_event("confusion_damage", {
                "pokemon": pokemon['name'],
                "damage": damage
            })
            return True

        return False

    def _check_win_conditions(self) -> bool:
        for player_id, player_data in self.players.items():
            active_pokemon = self.get_active(player_id)

            # 当前宝可梦濒死
            if active_pokemon["hp"] <= 0:
                # 检查是否有可用宝可梦 (关键校验)
                available_pokemon = [p for p in player_data["team"]
                                     if p["hp"] > 0 and p != active_pokemon]  # 排除当前宝可梦

                if available_pokemon:
                    # 自动切换到下一只可用宝可梦
                    team = player_data["team"]
                    current_index = player_data["active"]

                    # 尝试选择下一个索引的宝可梦
                    next_index = (current_index + 1) % len(team)
                    while next_index != current_index:
                        if team[next_index]["hp"] > 0:  # 血量检查
                            # 执行切换 - 更新玩家状态
                            player_data["active"] = next_index
                            player_data["switch_pending"] = True
                            player_data["switch_slot"] = next_index

                            self._log_event("auto_switch", {
                                "player": player_data['name'],
                                "from": active_pokemon['name'],
                                "to": team[next_index]['name']
                            })
                            return False  # 切换成功，战斗继续
                        next_index = (next_index + 1) % len(team)

                    # 理论上不会执行到这里，因为available_pokemon非空
                else:
                    # 没有可用宝可梦，战斗结束
                    self.ended = True
                    opponent_id = next(pid for pid in self.players if pid != player_id)
                    self.winner = self.players[opponent_id]["id"]
                    self._log_event("battle_ended", {"winner": self.winner})
                    return True
        return False

    def _determine_action_order(self) -> list:
        """确定行动顺序"""
        actions = []

        for player_id, player_data in self.players.items():
            action = player_data["action"]
            if not action:
                continue

            pokemon = self.get_active(player_id)
            priority = 0
            speed = self._calculate_stat(pokemon, "spe", ignore_ability=False)

            if action["type"] == "move":
                # 获取招式槽位
                move_slot = action.get("slot", 1) - 1

                # 检查槽位是否有效
                if move_slot < 0 or move_slot >= len(pokemon["moves"]):
                    continue

                # 获取实际招式对象
                move = pokemon["moves"][move_slot]

                # 获取招式优先度
                priority = move.get("priority", 0)
            # +++ 添加对非招式行动的处理 +++
            else:  # switch, item, forfeit 等行动
                priority = 0  # 这些行动默认优先级为0

            actions.append({
                "player_id": player_id,
                "priority": priority,
                "speed": speed,
                "action": action
            })

        # 排序规则: 优先度 > 速度 > 随机
        actions.sort(key=lambda a: (-a["priority"], -a["speed"]))

        # 速度相同随机化
        if len(actions) >= 2:
            for i in range(len(actions) - 1):
                if (actions[i]["priority"] == actions[i + 1]["priority"] and
                        actions[i]["speed"] == actions[i + 1]["speed"]):
                    if random.choice([True, False]):
                        actions[i], actions[i + 1] = actions[i + 1], actions[i]

        return actions

    def _execute_move(self, player_id: str, action: dict):
        """执行招式"""
        try:
            player_data = self.players[player_id]
            pokemon = self.get_active(player_id)
            opponent_id = next(pid for pid in self.players if pid != player_id)
            opponent_data = self.players[opponent_id]
            opponent_pokemon = self.get_active(opponent_id)

            # 获取实际招式对象
            move_slot = action.get("slot", 1) - 1
            if move_slot < 0 or move_slot >= len(pokemon["moves"]):
                self._log_event("invalid_move_slot", {
                    "player": player_data['name'],
                    "slot": move_slot + 1
                })
                return

            move = pokemon["moves"][move_slot]
            move_name = move["name"]

            if move["current_pp"] <= 0:
                self._log_event("move_unusable", {
                    "pokemon": pokemon['name'],
                    "move": move_name
                })
                return

            if move["current_pp"] > 0:
                move["current_pp"] -= 1
            else:
                self._log_event("move_unusable", {
                    "pokemon": pokemon['name'],
                    "move": move_name
                })
                return

            self._log_event("move_used", {
                "player": player_data['name'],
                "pokemon": pokemon['name'],
                "move": move_name
            })

            # 检查招式是否命中
            if not self._check_move_hit(pokemon, opponent_pokemon, move):
                self._log_event("move_missed")
                return

            # 计算伤害
            if move["category"] != "status":
                damage, is_critical, effectiveness = self._calculate_damage(pokemon, opponent_pokemon, move)

                # 应用伤害
                opponent_pokemon["hp"] = max(0, opponent_pokemon["hp"] - damage)

                self._log_event("damage_dealt", {
                    "target_player": opponent_data['name'],
                    "target_pokemon": opponent_pokemon['name'],
                    "damage": damage,
                    "effectiveness": effectiveness
                })

                # 要害事件
                if is_critical:
                    self._log_event("critical_hit")

                # 效果事件
                if effectiveness == 0:
                    self._log_event("effectiveness", {"effectiveness": 0})
                elif effectiveness <= 0.5:
                    self._log_event("effectiveness", {"effectiveness": 0.5})
                elif effectiveness < 1:
                    self._log_event("effectiveness", {"effectiveness": 0.75})
                elif effectiveness == 2:
                    self._log_event("effectiveness", {"effectiveness": 2})
                elif effectiveness > 2:
                    self._log_event("effectiveness", {"effectiveness": 3})
                elif effectiveness > 1:
                    self._log_event("effectiveness", {"effectiveness": 1.5})

                # 检查对手是否濒死
                if opponent_pokemon["hp"] == 0:
                    self._log_event("pokemon_fainted", {
                        "player": opponent_data['name'],
                        "pokemon": opponent_pokemon['name']
                    })

            # 应用招式附加效果
            if move.get("secondary") and not self.ended:
                self._apply_secondary_effects(pokemon, opponent_pokemon, move)

            # 如果是接触类招式，触发接触特性
            if move.get("makes_contact", False):
                context = {"attacker": pokemon, "target": opponent_pokemon}
                self._trigger_ability(opponent_pokemon, "contact", context)

            # 记录最后使用的招式
            pokemon["last_move"] = move

        except Exception as e:
            self._log_event("move_execution_error", {
                "error": str(e),
                "player": player_data['name'] if 'player_data' in locals() else 'unknown'
            })

    def _check_move_hit(self, attacker: dict, defender: dict, move: dict) -> bool:
        """检查招式是否命中"""
        accuracy = move.get("accuracy", 100)

        # 确保准确度不为None
        if accuracy is None:
            accuracy = 100

        # 考虑能力变化
        accuracy_mod = attacker["stat_mod"]["accuracy"] - defender["stat_mod"]["evasion"]
        accuracy_mod = max(-6, min(6, accuracy_mod))
        accuracy_multiplier = [0.33, 0.36, 0.43, 0.5, 0.6, 0.75, 1, 1.33, 1.67, 2, 2.33, 2.67, 3][accuracy_mod + 6]

        effective_accuracy = accuracy * accuracy_multiplier
        effective_accuracy = min(100, max(1, effective_accuracy))

        rand_num = random.randint(1, 100)
        return rand_num <= effective_accuracy

    def _calculate_damage(self, attacker: dict, defender: dict, move: dict) -> (int, bool, float):
        """计算招式伤害"""
        # 检查特性免疫
        immunities = defender["volatile"].get("ability_effects", {}).get("immunities", [])
        if move["type"] in immunities:
            self._log_event("ability_immunity", {
                "pokemon": defender['name'],
                "move_type": move["type"]
            })
            return 0, False, 0.0

        level = attacker["level"]
        power = move["power"]

        # 应用特性对威力的影响
        move_power_boost = attacker["volatile"].get("ability_effects", {}).get("move_power_boost")
        if move_power_boost and move["type"] in move_power_boost["move_types"]:
            power = int(power * move_power_boost["multiplier"])

        # 判断使用物攻还是特攻
        if move["category"] == "physical":
            attack_stat = self._calculate_stat(attacker, "atk")
            defense_stat = self._calculate_stat(defender, "def")
        else:  # special
            attack_stat = self._calculate_stat(attacker, "spa")
            defense_stat = self._calculate_stat(defender, "spd")

        # 基础伤害计算
        base_damage = ((2 * level / 5 + 2) * power * attack_stat / defense_stat) / 50 + 2

        # 属性一致加成 (1.5x)
        stab = 1.5 if move["type"] in attacker["types"] else 1.0

        # 属性克制
        effectiveness = 1.0
        for defender_type in defender["types"]:
            type_effect = TYPE_CHART.get(move["type"], {}).get(defender_type, 1.0)
            effectiveness *= type_effect

        # 检查要害一击
        is_critical = self._check_critical_hit(attacker, move)
        critical_multiplier = 1.5 if is_critical else 1.0

        # 随机因子 (0.85 - 1.0)
        random_factor = random.uniform(0.85, 1.0)

        # 最终伤害
        damage = int(base_damage * stab * effectiveness * critical_multiplier * random_factor)

        # 确保至少1点伤害（除非免疫）
        return max(1, damage) if effectiveness > 0 else 0, is_critical, effectiveness

    def _apply_secondary_effects(self, attacker: dict, defender: dict, move: dict):
        """应用招式附加效果"""
        secondary = move.get("secondary")
        if not secondary:
            return

        chance = secondary.get("chance", 100)

        # 检查效果是否触发
        rand_num = random.randint(0, 100)
        if rand_num > chance:
            return

        effect = secondary["effect"]

        # 能力变化
        if effect.startswith("stat"):
            parts = effect.split("_")
            target = parts[1]
            stat = parts[2]
            change = int(parts[3])

            if target == "self":
                self._modify_stat(attacker, stat, change)
            else:  # opponent
                self._modify_stat(defender, stat, change)

        # 状态附加
        elif effect.startswith("status"):
            status = effect.split("_")[1]
            self._apply_status(defender, status)

    def _check_critical_hit(self, attacker: dict, move: dict) -> bool:
        """检查是否发生要害一击"""
        # 总要害段数 = 招式基础段数 + 宝可梦的额外段数
        total_crit_stage = move["critical_hit_stage"] + attacker["critical_hit_stage"]

        # 限制在0-4之间
        total_crit_stage = max(0, min(4, total_crit_stage))

        # 获取要害概率
        crit_chance = CRITICAL_HIT_RATES[total_crit_stage]

        # 检查是否发生要害
        return random.random() < crit_chance

    def _calculate_stat(self, pokemon: dict, stat_name: str, ignore_ability: bool = False) -> float:
        """计算能力值 (考虑能力变化和特性)"""
        base_stat = pokemon[stat_name]

        # 应用特性加成 (除非特别要求忽略)
        if not ignore_ability:
            # 处理能力提升类特性
            ability_boost = pokemon["volatile"].get("ability_effects", {}).get(f"{stat_name}_boost")
            if ability_boost:
                base_stat *= ability_boost["multiplier"]

            # 处理能力变化类特性 (如缓慢启动)
            ability_mod = pokemon["volatile"].get("ability_effects", {}).get(f"{stat_name}_mod")
            if ability_mod:
                base_stat *= ability_mod["multiplier"]

        # 处理能力变化段数
        mod = pokemon["stat_mod"][stat_name]
        mod = max(-6, min(6, mod))

        # 能力变化乘数表
        mod_multipliers = [0.25, 0.28, 0.33, 0.40, 0.50, 0.66, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0]
        multiplier = mod_multipliers[mod + 6]

        return base_stat * multiplier

    def _apply_secondary_effects(self, attacker: dict, defender: dict, move: dict):
        """应用招式附加效果"""
        secondary = move["secondary"]
        chance = secondary.get("chance", 100)

        # 检查效果是否触发
        rand_num = random.randint(0, 100)
        if rand_num > chance:
            return

        effect = secondary["effect"]

        # 能力变化
        if effect.startswith("stat"):
            parts = effect.split("_")
            target = parts[1]
            stat = parts[2]
            change = int(parts[3])

            if target == "self":
                self._modify_stat(attacker, stat, change)
            else:  # opponent
                self._modify_stat(defender, stat, change)

        # 状态附加
        elif effect.startswith("status"):
            status = effect.split("_")[1]
            self._apply_status(defender, status)

    def _modify_stat(self, pokemon: dict, stat: str, change: int):
        """修改能力等级"""
        current = pokemon["stat_mod"][stat]
        new_level = max(-6, min(6, current + change))
        pokemon["stat_mod"][stat] = new_level

        self._log_event("stat_change", {
            "pokemon": pokemon['name'],
            "stat": stat,
            "direction": "提升" if change > 0 else "降低"
        })

    def _apply_status(self, pokemon: dict, status: str):
        """附加异常状态"""
        # 检查状态免疫特性
        status_immunities = pokemon["volatile"].get("ability_effects", {}).get("status_immunities", [])
        if status in status_immunities:
            self._log_event("ability_status_immunity", {
                "pokemon": pokemon['name'],
                "status": status
            })
            return

        # 宝可梦已有状态
        if pokemon["status"]:
            return

        # 检查状态免疫
        immune = False
        if status == "par":  # 麻痹
            immune = "electric" in pokemon["types"]
        elif status == "psn" or status == "badpsn":  # 中毒
            immune = "poison" in pokemon["types"] or "steel" in pokemon["types"]
        elif status == "brn":  # 灼伤
            immune = "fire" in pokemon["types"]
        elif status == "frz":  # 冰冻
            immune = "ice" in pokemon["types"]

        if immune:
            self._log_event("status_immune", {
                "pokemon": pokemon['name'],
                "status": status
            })
            return

        pokemon["status"] = status
        self._log_event("status_applied", {
            "pokemon": pokemon['name'],
            "status": status
        })

        # 触发相关特性
        if status != "none":
            self._trigger_ability(pokemon, "statused")

        # 如果是混乱状态，初始化混乱状态数据
        if status == "cfs":
            min_turns, max_turns = STATUS_EFFECTS["cfs"]["turns"]
            # +++ 确保正确初始化混乱状态 +++
            pokemon["volatile"]["confusion"] = {
                "turns": random.randint(min_turns, max_turns),
                "chance_to_hurt": STATUS_EFFECTS["cfs"]["chance_to_hurt"],
                "damage": STATUS_EFFECTS["cfs"]["damage"]
            }

    # 自动更换 - 立即执行
    def _execute_switch(self, player_id: str, action: dict):
        self._perform_switch(player_id, action.get("pokemon_index", 1) - 1)

    def _perform_switch(self, player_id: str, slot: int):
        """实际执行宝可梦更换"""
        player_data = self.players[player_id]

        # 检查是否有效更换
        if slot < 0 or slot >= len(player_data["team"]):
            return

        new_pokemon = player_data["team"][slot]

        # 检查宝可梦是否可用
        if new_pokemon["hp"] <= 0:
            return

        # 执行更换
        old_pokemon = self.get_active(player_id)
        player_data["active"] = slot

        self._log_event("switch_out", {
            "player": player_data['name'],
            "pokemon": old_pokemon['name']
        })
        self._log_event("switch_in", {
            "player": player_data['name'],
            "pokemon": new_pokemon['name']
        })

        # 触发出场特性
        self._trigger_ability(new_pokemon, "switch_in")

        # 重置能力变化
        for stat in new_pokemon["stat_mod"]:
            new_pokemon["stat_mod"][stat] = 0

    def _execute_item(self, player_id: str, action: dict):
        """使用道具"""
        player_data = self.players[player_id]
        pokemon = self.get_active(player_id)
        item = action["item"]

        self._log_event("item_used", {
            "player": player_data['name'],
            "item": item['name'],
            "pokemon": pokemon['name']
        })

        # 恢复类道具
        if item["category"] == "heal":
            heal_amount = item.get("amount", 0)
            if "%" in str(heal_amount):
                percent = float(heal_amount.strip("%")) / 100
                heal_amount = int(pokemon["max_hp"] * percent)

            pokemon["hp"] = min(pokemon["max_hp"], pokemon["hp"] + heal_amount)
            self._log_event("hp_restored", {
                "pokemon": pokemon['name'],
                "amount": heal_amount
            })

        # 状态恢复
        elif item["category"] == "status_heal":
            if pokemon["status"]:
                pokemon["status"] = None
                self._log_event("status_healed", {
                    "pokemon": pokemon['name']
                })
            else:
                self._log_event("item_no_effect", {
                    "item": item['name']
                })

    def _end_of_turn_effects(self, pokemon: dict):
        """回合结束时的效果处理"""
        # 检查低HP特性触发
        if pokemon["hp"] / pokemon["max_hp"] <= 1 / 3 and not pokemon["ability_activated"]:
            self._trigger_ability(pokemon, "hp_low")
            pokemon["ability_activated"] = True

        # 状态伤害
        self._apply_status_damage(pokemon, self._get_player_name_by_pokemon(pokemon))

        # 处理缓慢启动特性
        if "slow_start_turns" in pokemon and pokemon["slow_start_turns"] > 0:
            pokemon["slow_start_turns"] -= 1
            if pokemon["slow_start_turns"] == 0:
                # 移除缓慢启动效果
                for stat in ["atk", "spa", "spe"]:
                    key = f"{stat}_mod"
                    if key in pokemon["volatile"]["ability_effects"]:
                        del pokemon["volatile"]["ability_effects"][key]
                self._log_event("slow_start_ended", {
                    "pokemon": pokemon['name']
                })

        # 回合状态结束
        for status in list(pokemon["volatile"].keys()):
            if status == "confusion" and pokemon["volatile"][status] is not None:
                pokemon["volatile"][status]["turns"] -= 1
                if pokemon["volatile"][status]["turns"] <= 0:
                    del pokemon["volatile"][status]
                    self._log_event("status_ended", {
                        "pokemon": pokemon['name'],
                        "status": "confusion"
                    })