import socket
import os
import json
import uuid
import asyncio
import sqlite3
import logging
from logging.handlers import TimedRotatingFileHandler
import uvicorn
from contextlib import contextmanager
from datetime import datetime, timedelta
from math import pow
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Query, Body, Header, HTTPException, Request
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from fastapi.responses import JSONResponse, HTMLResponse
from pydantic import BaseModel, ValidationError
from typing import List, Dict, Optional
import time

from battle_system import BattleInstance

app = FastAPI()
player_names: Dict[str, str] = {}

# 所有时间处理中使用北京时间
def beijing_time():
    return datetime.utcnow() + timedelta(hours=8)

# ===================== 配置 =====================
CONFIG_FILE = "config.json"

# 默认配置
SERVER_TOKENS = {}
MATCH_ELO_TOLERANCE = 300
API_KEY = "cobblemonranked"
MATCH_TIMEOUT = 1800
IS_TEST_VERSION = False
MAX_CONCURRENT_BATTLES = 100
PORT = 8000

# 记录已经连接的服务器 ID
connected_servers = set()

if os.path.exists(CONFIG_FILE):
    with open(CONFIG_FILE, "r") as f:
        config_data = json.load(f)
        SERVER_TOKENS = config_data.get("server_tokens", {})
        MATCH_ELO_TOLERANCE = config_data.get("elo_max_diff", 300)
        API_KEY = config_data.get("api_key", "admin123")
        MATCH_TIMEOUT = config_data.get("match_timeout", 1800)
        IS_TEST_VERSION = config_data.get("is_test_version", False)
        MAX_CONCURRENT_BATTLES = config_data.get("max_concurrent_battles", 100)
        PORT = config_data.get("port", 8000)
else:
    SERVER_TOKENS = {"server-a": "abc123", "server-b": "def456"}
    MATCH_ELO_TOLERANCE = 300
    API_KEY = "cobblemonranked"
    MATCH_TIMEOUT = 1800
    IS_TEST_VERSION = False
    MAX_CONCURRENT_BATTLES = 100
    PORT = 8000

# 配置日志
# 确保log目录存在
os.makedirs("log", exist_ok=True)

# 主日志记录器
logger = logging.getLogger("matchmaking")
logger.setLevel(logging.INFO)

# 控制台处理器
console_handler = logging.StreamHandler()

# 按天滚动的文件处理器
file_handler = TimedRotatingFileHandler(
    filename="log/matchmaking.log",  # 存储到log目录
    when="midnight",  # 每天午夜滚动
    interval=1,       # 每天一个文件
    backupCount=7     # 保留7天的日志
)
file_handler.suffix = "%Y-%m-%d"  # 日志文件后缀格式

# 设置日志格式
formatter = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s")
console_handler.setFormatter(formatter)
file_handler.setFormatter(formatter)

# 添加处理器
logger.addHandler(console_handler)
logger.addHandler(file_handler)

# 战斗结果日志记录器
battle_logger = logging.getLogger("battle_result")
battle_logger.setLevel(logging.INFO)
# 战斗日志按天滚动
battle_file_handler = TimedRotatingFileHandler(
    filename="log/battle_result.log",
    when="midnight",
    interval=1,
    backupCount=7
)
battle_file_handler.suffix = "%Y-%m-%d"
battle_file_handler.setFormatter(formatter)
battle_logger.addHandler(battle_file_handler)

# ===================== 数据库初始化 =====================
DB_FILE = "matchmaking.db"

@contextmanager
def get_db_connection():
    """数据库连接上下文管理器"""
    conn = sqlite3.connect(DB_FILE, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
    finally:
        conn.close()

def init_db():
    """初始化数据库结构"""
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS ratings (
            player_id TEXT PRIMARY KEY,
            player_name TEXT,
            rating INTEGER DEFAULT 1000,
            wins INTEGER DEFAULT 0,
            losses INTEGER DEFAULT 0,
            last_active TEXT,
            last_server TEXT
        )""")

        cursor.execute("""
        CREATE TABLE IF NOT EXISTS battles (
            battle_id TEXT PRIMARY KEY,
            timestamp TEXT,
            player1 TEXT,
            player2 TEXT,
            mode TEXT,
            winner TEXT,
            team1 TEXT,
            team2 TEXT
        )""")

        cursor.execute("""
        CREATE TABLE IF NOT EXISTS server_status (
            server_id TEXT PRIMARY KEY,
            last_ping TEXT,
            player_count INTEGER DEFAULT 0
        )""")

        cursor.execute("""
        CREATE TABLE IF NOT EXISTS status_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp TEXT NOT NULL,
            online_servers INTEGER DEFAULT 0,
            queue_singles INTEGER DEFAULT 0,
            queue_doubles INTEGER DEFAULT 0,
            active_battles INTEGER DEFAULT 0,
            online_players INTEGER DEFAULT 0
        )
        """)

        # 添加宝可梦使用统计表
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS pokemon_usage (
            pokemon_name TEXT PRIMARY KEY,
            usage_count INTEGER DEFAULT 0
        )""")

        cursor.execute("CREATE INDEX IF NOT EXISTS idx_ratings_rating ON ratings(rating)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_battles_timestamp ON battles(timestamp)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_status_history_timestamp ON status_history(timestamp)")
        conn.commit()

init_db()


def record_pokemon_usage(player1: Dict, player2: Dict):
    """记录宝可梦使用次数"""
    with get_db_connection() as conn:
        cursor = conn.cursor()

        # 创建临时表存储本次使用的宝可梦
        cursor.execute("""
            CREATE TEMP TABLE IF NOT EXISTS temp_pokemon_usage (
                pokemon_name TEXT PRIMARY KEY
            )
        """)

        # 清空临时表
        cursor.execute("DELETE FROM temp_pokemon_usage")

        # 记录玩家1的宝可梦
        for pokemon in player1["pokemons"]:
            pokemon_name = pokemon.get("name", "未知宝可梦").strip().lower()
            cursor.execute("""
                INSERT OR IGNORE INTO temp_pokemon_usage (pokemon_name)
                VALUES (?)
            """, (pokemon_name,))

        # 记录玩家2的宝可梦
        for pokemon in player2["pokemons"]:
            pokemon_name = pokemon.get("name", "未知宝可梦").strip().lower()
            cursor.execute("""
                INSERT OR IGNORE INTO temp_pokemon_usage (pokemon_name)
                VALUES (?)
            """, (pokemon_name,))

        # 更新主表
        cursor.execute("""
            INSERT OR REPLACE INTO pokemon_usage (pokemon_name, usage_count)
            SELECT 
                t.pokemon_name,
                COALESCE(pu.usage_count, 0) + 1
            FROM temp_pokemon_usage t
            LEFT JOIN pokemon_usage pu ON t.pokemon_name = pu.pokemon_name
        """)

        conn.commit()

# ===================== 状态存储 =====================
matchmaking_queue: Dict[str, List[Dict]] = {"singles": [], "doubles": []}
online_players: Dict[str, str] = {}
active_battles: Dict[str, BattleInstance] = {}
pending_matches: Dict[str, asyncio.Task] = {}

# ===================== 模型 =====================
class JoinRequest(BaseModel):
    player_id: str
    player_name: Optional[str]
    server: str
    pokemons: List[dict]
    mode: str

class BattleResult(BaseModel):
    battle_id: str
    winner: str
    loser: str

class BattleCommand(BaseModel):
    battle_id: str
    command: str
    player_id: str

class ChatMessage(BaseModel):
    battle_id: str
    message: str
    player_id: str
    channel: str = "battle"

# ===================== WebSocket 连接管理 =====================
class ConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, WebSocket] = {}
        self.last_ping: Dict[str, float] = {}
        self.server_players: Dict[str, List[str]] = {}  # 服务器到玩家列表的映射

    async def connect(self, websocket: WebSocket, server_id: str):
        await websocket.accept()
        self.active_connections[server_id] = websocket
        self.last_ping[server_id] = time.time()
        self.server_players[server_id] = []
        logger.info(f"子服 {server_id} 已连接")

    def disconnect(self, server_id: str):
        if server_id in self.active_connections:
            del self.active_connections[server_id]
        if server_id in self.last_ping:
            del self.last_ping[server_id]
        if server_id in self.server_players:
            del self.server_players[server_id]
        logger.info(f"子服 {server_id} 断开连接")

    # 更新玩家列表的方法
    def update_player_server(self, player_id: str, server_id: str):
        # 先从旧服务器移除玩家
        for srv, players in list(self.server_players.items()):
            if player_id in players:
                players.remove(player_id)

        # 添加到新服务器
        if server_id not in self.server_players:
            self.server_players[server_id] = []
        if player_id not in self.server_players[server_id]:
            self.server_players[server_id].append(player_id)

    async def send_message(self, message: Dict, server_id: str):
        if server_id in self.active_connections:
            try:
                await self.active_connections[server_id].send_text(json.dumps(message))
            except Exception as e:
                logger.error(f"向 {server_id} 发送消息失败: {e}")
                self.disconnect(server_id)

    async def broadcast(self, message: Dict):
        for server_id in list(self.active_connections.keys()):
            await self.send_message(message, server_id)

manager = ConnectionManager()

@app.websocket("/ws/{server_id}")
async def websocket_connect(websocket: WebSocket, server_id: str, token: str = Query(...), version: str = Query(...)):
    # try:
    #     server_ip = socket.gethostbyname(server_id)
    # except socket.gaierror:
    #     server_ip = server_id

    if server_id in connected_servers:
        logger.warning(f"拒绝连接: 服务器 {server_id} ({websocket.client.host})已经连接过，无法重复连接")
        await websocket.close(code=114)
        return

    expected_version = "1.2.0"

    if version != expected_version:
        logger.warning(f"拒绝连接: 非法版本号来自 {server_id} ({websocket.client.host})")
        await websocket.close(code=1008)
        return

    if IS_TEST_VERSION:
        logger.warning(f"公开版本，允许任何连接来自 {server_id} ({websocket.client.host})")
    else:
        expected_token = SERVER_TOKENS.get(server_id)
        client_ip = websocket.client.host

        if expected_token is None or token != expected_token:
            logger.warning(f"拒绝连接: 非法 Token 来自 {server_id} ({client_ip})")
            await websocket.close(code=1008)
            return

    connected_servers.add(server_id)
    await manager.connect(websocket, server_id)

    try:
        while True:
            data = await websocket.receive_text()
            try:
                message = json.loads(data)
                await handle_ws_message(message, server_id)
            except json.JSONDecodeError:
                logger.error(f"来自 {server_id} 的无效 JSON 消息: {data}")
            except ValidationError as e:
                logger.error(f"来自 {server_id} 的消息验证失败: {e}")

    except WebSocketDisconnect:
        manager.disconnect(server_id)
        connected_servers.remove(server_id)
    except Exception as e:
        logger.error(f"与 {server_id} 的 WebSocket 错误: {e}")
        manager.disconnect(server_id)
        connected_servers.remove(server_id)

# ===================== 匹配系统 =====================
def is_authenticated_player(player_id: str) -> bool:
    """
    验证玩家是否为正版
    正版玩家UUID格式：8-4-4-4-12 (32个十六进制字符)
    离线模式UUID格式：带破折号的v3 UUID
    """
    try:
        # 验证UUID格式（32字符无破折号）
        if len(player_id) == 32 and all(c in "0123456789abcdef" for c in player_id):
            return True

        # 验证在线模式UUID（带破折号）
        uuid_obj = uuid.UUID(player_id)
        return uuid_obj.version == 4  # 仅接受v4 UUID
    except ValueError:
        return False

@app.post("/join-queue")
async def join_queue(payload: JoinRequest):
    # 检查当前战斗数量是否超过限制
    if len(active_battles) >= MAX_CONCURRENT_BATTLES:
        logger.warning(f"拒绝加入队列：当前活跃战斗数 {len(active_battles)} 已达到最大限制 {MAX_CONCURRENT_BATTLES}")
        return JSONResponse(
            {"error": "服务器繁忙，当前对战数量已达上限，请稍后再试"},
            status_code=429
        )

    # +++ 验证是否为正版玩家 +++
    if not is_authenticated_player(payload.player_id):
        return JSONResponse(
            {"error": "仅限正版玩家参与匹配"},
            status_code=410
        )

    if payload.mode not in matchmaking_queue:
        return JSONResponse({"error": "无效的匹配模式"}, status_code=400)

    for mode, queue in matchmaking_queue.items():
        if any(e["player_id"] == payload.player_id for e in queue):
            return JSONResponse({"error": "你已在匹配队列中"}, status_code=400)

    if payload.player_id in pending_matches:
        pending_matches[payload.player_id].cancel()
        del pending_matches[payload.player_id]

    entry = {
        "player_id": payload.player_id,
        "player_name": payload.player_name,
        "server": payload.server,
        "pokemons": payload.pokemons,
        "join_time": time.time()
    }

    player_names[payload.player_id] = payload.player_name or payload.player_id
    online_players[payload.player_id] = payload.server

    matchmaking_queue[payload.mode].append(entry)
    online_players[payload.player_id] = payload.server

    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT player_id FROM ratings WHERE player_id = ?", (payload.player_id,))
        row = cursor.fetchone()

        player_name = payload.player_name or payload.player_id

        if row:
            cursor.execute("""
                    UPDATE ratings 
                    SET player_name = ?, last_server = ?, last_active = ?
                    WHERE player_id = ?
                """, (player_name, payload.server, beijing_time().isoformat(), payload.player_id))
        else:
            cursor.execute("""
                    INSERT INTO ratings (player_id, player_name, last_server, last_active)
                    VALUES (?, ?, ?, ?)
                """, (payload.player_id, player_name, payload.server, beijing_time().isoformat()))

        conn.commit()

    pending_matches[payload.player_id] = asyncio.create_task(
        match_timeout_handler(payload.player_id, payload.mode)
    )
    # 更新服务器玩家映射
    manager.update_player_server(payload.player_id, payload.server)

    logger.info(
        f"玩家 {payload.player_name}({payload.player_id}) 加入 {payload.mode} 队列 | 来自子服: {payload.server} | 当前队列长度: {len(matchmaking_queue[payload.mode])}")
    asyncio.create_task(check_matches())
    return {"status": "ok", "message": "已加入匹配队列"}


async def broadcast_battle_log(battle: BattleInstance, event_dict: dict):
    """广播战斗事件给相关服务器（确保每个服务器只收到一次）"""
    # 创建服务器集合（自动去重）
    servers_to_notify = set()

    # 收集所有相关服务器
    for player_id in battle.players:
        server_id = online_players.get(player_id)
        if server_id:
            servers_to_notify.add(server_id)

    event_dict["battle_id"] = battle.battle_id

    # 向每个相关服务器发送一次通知
    for server_id in servers_to_notify:
        try:
            await manager.send_message({
                "type": "battle_event",
                "data": event_dict
            }, server_id)
        except Exception as e:
            logger.error(f"向服务器 {server_id} 发送战斗事件失败: {e}")

async def match_timeout_handler(player_id: str, mode: str):
    await asyncio.sleep(MATCH_TIMEOUT)

    if player_id in pending_matches:
        queue = matchmaking_queue[mode]
        queue[:] = [e for e in queue if e["player_id"] != player_id]

        if player_id in online_players:
            del online_players[player_id]

        del pending_matches[player_id]
        logger.info(f"玩家 {player_id} 匹配超时，已从队列中移除")

@app.post("/leave-queue")
async def leave_queue(player_id: str = Body(..., embed=True)):
    if not player_id:
        return JSONResponse({"error": "缺少 player_id"}, status_code=400)

    if player_id in pending_matches:
        pending_matches[player_id].cancel()
        del pending_matches[player_id]

    removed = False
    for mode, queue in matchmaking_queue.items():
        before = len(queue)
        queue[:] = [e for e in queue if e["player_id"] != player_id]
        after = len(queue)
        if before != after:
            # 从服务器玩家映射中移除
            for server_id, players in list(manager.server_players.items()):
                if player_id in players:
                    players.remove(player_id)

            logger.info(
                f"玩家 {player_id} 离开匹配队列 | 当前 singles: {len(matchmaking_queue['singles'])}, doubles: {len(matchmaking_queue['doubles'])}")
            removed = True

    if player_id in online_players:
        del online_players[player_id]

    if not removed:
        logger.info(f"玩家 {player_id} 不在任何队列中")

    return {"status": "ok", "message": "离开匹配队列"}

# ===================== 管理接口 =====================
# os.makedirs("templates", exist_ok=True)
# templates = Jinja2Templates(directory="templates")
# app.mount("/img", StaticFiles(directory="templates/img"), name="img")
# app.mount("/js", StaticFiles(directory="templates/js"), name="js")

# @app.get("/", response_class=HTMLResponse)
# @app.get("/index", response_class=HTMLResponse)
# async def dashboard(request: Request):
#     return templates.TemplateResponse("index.html", {"request": request})


# # 获取宝可梦使用统计
# @app.get("/pokemon-usage")
# async def get_pokemon_usage(
#         x_api_key: Optional[str] = Header(None),
#         api_key: Optional[str] = Query(None),
#         limit: int = Query(10, ge=1, le=20)
# ):
#     provided_key = x_api_key or api_key
#     if provided_key != API_KEY:
#         raise HTTPException(status_code=403, detail="无效 API 密钥")

#     with get_db_connection() as conn:
#         cursor = conn.cursor()

#         # 获取总使用次数
#         cursor.execute("SELECT SUM(usage_count) as total_count FROM pokemon_usage")
#         total_row = cursor.fetchone()
#         total_count = total_row["total_count"] or 1  # 避免除以零

#         # 获取使用统计
#         cursor.execute("""
#             SELECT pokemon_name, usage_count 
#             FROM pokemon_usage 
#             ORDER BY usage_count DESC 
#             LIMIT ?
#         """, (limit,))

#         results = cursor.fetchall()
#         return [
#             {
#                 "name": row["pokemon_name"],
#                 "count": row["usage_count"],
#                 "usage_rate": (row["usage_count"] / total_count) * 100  # 计算使用率百分比
#             }
#             for row in results
#         ]

# @app.get("/trend-data")
# async def get_trend_data(
#         x_api_key: Optional[str] = Header(None),
#         api_key: Optional[str] = Query(None)
# ):
#     provided_key = x_api_key or api_key
#     if provided_key != API_KEY:
#         raise HTTPException(status_code=403, detail="无效 API 密钥")

#     realtime_status = {
#         "online_servers": len(manager.active_connections),
#         "queue_singles": len(matchmaking_queue["singles"]),
#         "queue_doubles": len(matchmaking_queue["doubles"]),
#         "active_battles": len(active_battles),
#         "online_players": (
#                 len(matchmaking_queue["singles"]) +
#                 len(matchmaking_queue["doubles"]) * 2 +
#                 (len(active_battles) * 2)
#         )
#     }

#     end_time = beijing_time().replace(minute=0, second=0, microsecond=0)  # 取当前整点
#     start_time = end_time - timedelta(hours=24)  # 24小时前整点

#     with get_db_connection() as conn:
#         cursor = conn.cursor()

#         # 生成完整的24小时时间点
#         full_hours = [(end_time - timedelta(hours=i)).strftime("%H:%M") for i in range(24, -1, -1)]

#         cursor.execute("""
#             SELECT 
#                 strftime('%H:00', timestamp) AS hour_minute,
#                 AVG(online_servers) AS avg_servers,
#                 AVG(active_battles) AS avg_battles,
#                 AVG(online_players) AS avg_players
#             FROM status_history
#             WHERE timestamp >= ? AND timestamp <= ?
#             GROUP BY strftime('%H', timestamp)  -- 按小时分组
#             ORDER BY timestamp
#         """, (start_time.isoformat(), end_time.isoformat()))

#         # 创建数据映射
#         data_map = {row["hour_minute"]: row for row in cursor.fetchall()}

#         # 填充完整24小时数据
#         servers_data = []
#         battles_data = []
#         players_data = []

#         for hour in full_hours:
#             if hour in data_map:
#                 row = data_map[hour]
#                 servers_data.append(row["avg_servers"])
#                 battles_data.append(row["avg_battles"])
#                 players_data.append(row["avg_players"])
#             else:
#                 # 无数据时使用0填充
#                 servers_data.append(0)
#                 battles_data.append(0)
#                 players_data.append(0)

#         return {
#             "realtime": realtime_status,
#             "hours": full_hours,  # 使用生成的完整时间点
#             "servers": servers_data,
#             "battles": battles_data,
#             "players": players_data
#         }

# @app.get("/ranking")
# async def get_ranking(
#         x_api_key: Optional[str] = Header(None),
#         api_key: Optional[str] = Query(None),
#         page: int = Query(1, ge=1),
#         per_page: int = Query(10, ge=5, le=100),
#         search: Optional[str] = Query(None)
# ):
#     provided_key = x_api_key or api_key
#     if provided_key != API_KEY:
#         raise HTTPException(status_code=403, detail="无效 API 密钥")

#     with get_db_connection() as conn:
#         cursor = conn.cursor()

#         base_query = """
#             WITH FullRanking AS (
#                 SELECT 
#                     player_id, 
#                     player_name, 
#                     rating, 
#                     wins, 
#                     losses, 
#                     last_server,
#                     ROW_NUMBER() OVER (
#                         ORDER BY 
#                             rating DESC,
#                             CASE WHEN wins + losses > 0 THEN wins * 1.0 / (wins + losses) ELSE 0 END DESC,
#                             wins DESC
#                     ) AS global_rank 
#                 FROM ratings
#             )
#             SELECT * FROM FullRanking
#             WHERE 1=1
#         """

#         params = []
#         if search:
#             base_query += " AND (player_id LIKE ? OR player_name LIKE ?)"
#             search_term = f"%{search}%"
#             params.extend([search_term, search_term])

#         count_query = f"SELECT COUNT(*) as total FROM ({base_query})"
#         cursor.execute(count_query, params)
#         total_count = cursor.fetchone()["total"]

#         total_pages = (total_count + per_page - 1) // per_page
#         offset = (page - 1) * per_page

#         data_query = base_query + " ORDER BY global_rank LIMIT ? OFFSET ?"
#         params.extend([per_page, offset])
#         rows = cursor.execute(data_query, params).fetchall()

#         return {
#             "players": [dict(row) for row in rows],
#             "pagination": {
#                 "page": page,
#                 "per_page": per_page,
#                 "total_pages": total_pages,
#                 "total_players": total_count,
#                 "current_page_size": len(rows)
#             }
#         }

# ===================== 历史数据记录任务 =====================
peak_status = {
    "online_servers": 0,
    "queue_singles": 0,
    "queue_doubles": 0,
    "active_battles": 0,
    "online_players": 0
}

current_status_cache = {
    "timestamp": beijing_time().isoformat(),
    "online_servers": 0,
    "queue_singles": 0,
    "queue_doubles": 0,
    "active_battles": 0,
    "online_players": 0
}


async def record_status_history():
    global peak_status, current_status_cache

    update_peak_status()

    while True:
        # 计算到下一个整点的等待时间
        now = beijing_time()
        next_hour = (now + timedelta(hours=1)).replace(minute=0, second=0, microsecond=0)
        wait_seconds = (next_hour - now).total_seconds()

        await asyncio.sleep(wait_seconds)  # 等待到下一个整点

        try:
            status = {
                "timestamp": next_hour.isoformat(),
                "online_servers": peak_status["online_servers"],
                "queue_singles": peak_status["queue_singles"],
                "queue_doubles": peak_status["queue_doubles"],
                "active_battles": peak_status["active_battles"],
                "online_players": peak_status["online_players"]
            }

            current_status_cache = status

            with get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("""
                    INSERT INTO status_history 
                    (timestamp, online_servers, queue_singles, queue_doubles, active_battles, online_players)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, (
                    status["timestamp"],
                    status["online_servers"],
                    status["queue_singles"],
                    status["queue_doubles"],
                    status["active_battles"],
                    status["online_players"]
                ))
                conn.commit()

                cursor.execute("""
                    DELETE FROM status_history 
                    WHERE timestamp < datetime('now', '-7 days')
                """)
                conn.commit()

            logger.info(f"记录状态峰值: {status}")

            # 重置峰值状态
            peak_status = {
                "online_servers": 0,
                "queue_singles": 0,
                "queue_doubles": 0,
                "active_battles": 0,
                "online_players": 0
            }

        except Exception as e:
            logger.error(f"记录状态历史失败: {e}")

def update_peak_status():
    global peak_status, current_status_cache

    current_online_players = (
            len(matchmaking_queue["singles"]) +
            len(matchmaking_queue["doubles"]) * 2 +
            (len(active_battles) * 2)
    )

    current_status = {
        "timestamp": beijing_time().isoformat(),
        "online_servers": len(manager.active_connections),
        "queue_singles": len(matchmaking_queue["singles"]),
        "queue_doubles": len(matchmaking_queue["doubles"]),
        "active_battles": len(active_battles),
        "online_players": current_online_players
    }

    current_status_cache = current_status

    for key in peak_status:
        if current_status[key] > peak_status[key]:
            peak_status[key] = current_status[key]

    asyncio.get_event_loop().call_later(60, update_peak_status)

# ===================== Elo & 战斗记录 =====================
def calculate_elo(winner_id: str, loser_id: str, cursor: sqlite3.Cursor, k: int = 32):
    rating1 = get_rating(winner_id, cursor)
    rating2 = get_rating(loser_id, cursor)

    expected1 = 1 / (1 + pow(10, (rating2 - rating1) / 400))
    expected2 = 1 / (1 + pow(10, (rating1 - rating2) / 400))

    new_rating1 = round(rating1 + k * (1 - expected1))
    new_rating2 = round(rating2 + k * (0 - expected2))

    winner_name = player_names.get(winner_id, winner_id)
    cursor.execute("""
        UPDATE ratings 
        SET rating = ?, wins = wins + 1, last_active = ?, player_name = ?
        WHERE player_id = ?
    """, (new_rating1, beijing_time().isoformat(), winner_name, winner_id))

    loser_name = player_names.get(loser_id, loser_id)
    cursor.execute("""
        UPDATE ratings 
        SET rating = ?, losses = losses + 1, last_active = ?, player_name = ?
        WHERE player_id = ?
    """, (new_rating2, beijing_time().isoformat(), loser_name, loser_id))

def get_rating(player_id: str, cursor: sqlite3.Cursor) -> int:
    cursor.execute("SELECT rating FROM ratings WHERE player_id = ?", (player_id,))
    row = cursor.fetchone()

    if row:
        return row["rating"]
    else:
        cursor.execute("""
            INSERT INTO ratings (player_id, rating, last_active)
            VALUES (?, ?, ?)
        """, (player_id, 1000, beijing_time().isoformat()))
        return 1000

def record_battle(player1: Dict, player2: Dict, mode: str) -> str:
    battle_id = str(uuid.uuid4())
    timestamp = beijing_time().isoformat()

    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("""
            INSERT INTO battles (
                battle_id, timestamp, player1, player2, mode, team1, team2
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (
            battle_id,
            timestamp,
            player1["player_id"],
            player2["player_id"],
            mode,
            json.dumps(player1["pokemons"]),
            json.dumps(player2["pokemons"])
        ))
        conn.commit()

    return battle_id

# ===================== 匹配逻辑 =====================
async def check_matches():
    """检查并创建匹配"""
    # 单打匹配逻辑
    singles_queue = matchmaking_queue["singles"]

    # 清理重复玩家
    seen = set()
    singles_queue[:] = [p for p in singles_queue if not (p["player_id"] in seen or seen.add(p["player_id"]))]

    if len(singles_queue) >= 2:
        # 按评分排序
        singles_queue.sort(key=lambda p: get_rating_safe(p["player_id"]))

        # 尝试匹配相近评分的玩家
        i = 0
        while i < len(singles_queue) - 1:
            p1 = singles_queue[i]
            r1 = get_rating_safe(p1["player_id"])

            # 查找最佳匹配（跳过相同玩家）
            best_match = None
            best_diff = float('inf')

            for j in range(i + 1, len(singles_queue)):
                p2 = singles_queue[j]

                # 防止自匹配
                if p1["player_id"] == p2["player_id"]:
                    continue

                if p1["server"] == p2["server"]:
                    continue  # 跳过同一服务器的玩家

                r2 = get_rating_safe(p2["player_id"])
                rating_diff = abs(r1 - r2)

                # 检查是否在容忍范围内
                if rating_diff <= MATCH_ELO_TOLERANCE:
                    # 检查玩家是否在线
                    if (p1["player_id"] in online_players and
                            p2["player_id"] in online_players):

                        # 记录最佳匹配
                        if rating_diff < best_diff:
                            best_match = (j, p2)
                            best_diff = rating_diff

            if best_match:
                j, p2 = best_match
                # 从队列中移除玩家
                player2 = singles_queue.pop(j)
                player1 = singles_queue.pop(i)

                # 取消匹配超时任务
                for player_id in [player1["player_id"], player2["player_id"]]:
                    if player_id in pending_matches:
                        pending_matches[player_id].cancel()
                        del pending_matches[player_id]

                # 记录对战
                battle_id = record_battle(player1, player2, "singles")

                logger.info(
                    f"[匹配成功] {player1['player_name']}（{player1['player_id']}） vs {player2['player_name']}（{player2['player_id']}） | 模式: singles | 对战ID: {battle_id}")
                logger.info(
                    f"[队列状态] singles: {len(matchmaking_queue['singles'])}, doubles: {len(matchmaking_queue['doubles'])} | 活跃对战数: {len(active_battles)}")

                # 创建战斗实例
                battle = BattleInstance(
                    battle_id,
                    player1,
                    player2,
                    "singles",
                    log_callback=None  # 先设为空
                )

                # +++ 如果战斗因超限已结束，立即处理结果 +++
                if battle.ended and battle.result_pending:
                    await handle_battle_result(BattleResult(
                        battle_id=battle_id,
                        winner=battle.result["winner"],
                        loser=battle.result["loser"]
                    ))
                    continue  # 跳过后续流程

                # 再手动绑定 log 回调
                battle.log_callback = lambda msg: asyncio.create_task(broadcast_battle_log(battle, msg))
                active_battles[battle_id] = battle

                # 添加调试日志
                logger.info(f"创建对战: {battle_id}")
                logger.info(f"玩家1 ID: {player1['player_id']}, 服务器: {online_players.get(player1['player_id'])}")
                logger.info(f"玩家2 ID: {player2['player_id']}, 服务器: {online_players.get(player2['player_id'])}")

                # 宝可梦使用记录
                record_pokemon_usage(player1, player2)

                # 通知双方玩家
                for player, opponent in [(player1, player2), (player2, player1)]:
                    server_id = online_players.get(player["player_id"])
                    if server_id:
                        opponent_active = opponent["pokemons"][0]

                        self_team = [
                            {
                                "slot": idx + 1,
                                "name": p["name"],
                                "name_key": p["name_key"],
                                "level": p["level"],
                                "types": p["types"],
                                "hp": p["hp"],
                                "max_hp": p["max_hp"],
                                "moves": p.get("moves", []),
                                "status": p.get("status")
                            }
                            for idx, p in enumerate(player["pokemons"])
                        ]

                        await manager.send_message({
                            "type": "match_found",
                            "battle_id": battle_id,
                            "self_id": player["player_id"],
                            "opponent": opponent["player_id"],
                            "opponent_name": opponent["player_name"],
                            "opponent_active": {
                                "name": opponent_active["name"],
                                "name_key": opponent_active["name_key"],
                                "level": opponent_active["level"],
                                "types": opponent_active["types"],
                                "moves": opponent_active.get("moves", []),
                            },
                            "opponent_team": [
                                {
                                    "name": p["name"],
                                    "name_key": p["name_key"],
                                    "level": p["level"],
                                    "moves": p.get("moves", []),
                                }
                                for p in opponent["pokemons"]
                            ],
                            "self_team": self_team,
                            "mode": "singles"
                        }, server_id)
                    else:
                        logger.warning(f"找不到玩家 {player['player_id']} 的服务器")

def get_rating_safe(player_id: str) -> int:
    with get_db_connection() as conn:
        cursor = conn.cursor()
        return get_rating(player_id, cursor)

# ===================== 后台任务 =====================
async def server_monitor():
    update_peak_status()

    while True:
        await asyncio.sleep(60)
        now = time.time()

        for server_id, last_ping in list(manager.last_ping.items()):
            if now - last_ping > 180:
                logger.warning(f"服务器 {server_id} 心跳超时，断开连接")
                manager.disconnect(server_id)

        current_time = time.time()
        for battle_id, battle in list(active_battles.items()):
            if current_time - battle.start_time > 1800:
                logger.warning(f"对战 {battle_id} 超时，强制结束")
                del active_battles[battle_id]

async def periodic_match_check():
    while True:
        await asyncio.sleep(3)
        await check_matches()


async def periodic_battle_processing():
    """定期处理战斗结果"""
    while True:
        await asyncio.sleep(1)
        # 检查所有活跃战斗
        for battle_id, battle in list(active_battles.items()):
            if hasattr(battle, "result_pending") and battle.result_pending:
                try:
                    # 处理战斗结果
                    result = BattleResult(
                        battle_id=battle.battle_id,
                        winner=battle.result["winner"],
                        loser=battle.result["loser"]
                    )
                    await handle_battle_result(result)

                    # 通知服务器
                    for player_id in battle.players:
                        server_id = online_players.get(player_id)
                        if server_id:
                            await manager.send_message({
                                "type": "battle_ended",
                                "battle_id": battle.battle_id,
                                "winner": result.winner
                            }, server_id)

                    # 从活跃战斗中移除
                    del active_battles[battle_id]
                    del battle.result_pending  # 清理标记

                except Exception as e:
                    logger.error(f"无需处理超时投降: {e}")

# ===================== WebSocket 消息处理 =====================
async def handle_ws_message(message: Dict, server_id: str):
    try:
        msg_type = message.get("type")

        if msg_type == "ping":
            manager.last_ping[server_id] = time.time()
            await manager.send_message({"type": "pong"}, server_id)

        elif msg_type == "request_battle_state":
            battle_id = message.get("battle_id")
            if not battle_id:
                return
            battle = active_battles.get(battle_id)
            if battle:
                logger.info(f"处理战斗状态请求: {battle_id} (来自服务器: {server_id})")
                # 只向请求的服务器发送状态更新
                await send_battle_state_to_server(battle, server_id)

        elif msg_type == "battle_command":
            try:
                cmd = BattleCommand(**message)
                logger.info(f"[指令处理] 玩家 {cmd.player_id} 发出指令: {message}")
                battle = active_battles.get(cmd.battle_id)

                # +++ 对战存在性检查 +++
                if not battle:
                    logger.warning(f"对战 {cmd.battle_id} 不存在或已结束")
                    return

                # +++ 检查玩家是否已选择行动 +++
                if battle.action_choices.get(cmd.player_id, False):
                    logger.warning(f"玩家 {cmd.player_id} 已在本回合选择过行动，忽略新指令")
                    return

                # 重置行动选择状态
                battle.action_choices[cmd.player_id] = True
                if battle:
                    try:
                        command_data = json.loads(cmd.command)
                    except json.JSONDecodeError:
                        if isinstance(cmd.command, dict):
                            command_data = cmd.command
                        else:
                            logger.error(f"无效的指令格式: {cmd.command}")
                            return

                    if command_data.get("type") == "switch":
                        slot = command_data.get("slot")
                        if not slot:
                            logger.error("缺少切换槽位参数")
                            return

                        # 执行宝可梦切换
                        battle.execute_switch(cmd.player_id, {"slot": slot})

                    elif command_data.get("type") == "forfeit":
                        loser_id = cmd.player_id
                        winner_id = next(pid for pid in battle.players if pid != loser_id)
                        await handle_battle_result(BattleResult(
                            battle_id=cmd.battle_id,
                            winner=winner_id,
                            loser=loser_id
                        ))

                        active_battles.pop(cmd.battle_id, None)
                        for player_id in battle.players:
                            srv = online_players.get(player_id)
                            if srv:
                                await manager.send_message({
                                    "type": "battle_ended",
                                    "battle_id": cmd.battle_id,
                                    "winner": winner_id
                                }, srv)
                        return
                    battle.set_action(cmd.player_id, command_data)
                    if battle.can_proceed():
                        battle.process_turn()
                        if battle.ended:
                            winner_id = battle.winner
                            loser_id = next(pid for pid in battle.players if pid != winner_id)
                            await handle_battle_result(BattleResult(
                                battle_id=cmd.battle_id,
                                winner=winner_id,
                                loser=loser_id
                            ))
                            # +++ 安全删除对战 +++
                            if cmd.battle_id in active_battles:
                                del active_battles[cmd.battle_id]
            except Exception as e:
                logger.error(f"处理指令时出错: {e}", exc_info=True)

        elif msg_type == "chat":
            try:
                chat = ChatMessage(**message)
                if chat.channel != "battle":
                    return
                battle = active_battles.get(chat.battle_id)
                if battle:
                    opponent_id = None
                    for pid in battle.players:
                        if pid != chat.player_id:
                            opponent_id = pid
                            break

                    if opponent_id:
                        opponent_server = online_players.get(opponent_id)
                        if opponent_server:
                            await manager.send_message({
                                "type": "chat",
                                "battle_id": chat.battle_id,
                                "message": chat.message,
                                "from": chat.player_id
                            }, opponent_server)
            except ValidationError as e:
                logger.error(f"无效的聊天消息: {e}")

        elif msg_type == "battle_result":
            try:
                result = BattleResult(**message)
                await handle_battle_result(result)
            except ValidationError as e:
                logger.error(f"无效的对战结果: {e}")

        else:
            logger.warning(f"来自 {server_id} 的未知消息类型: {msg_type}")

    except Exception as e:
        logger.error(f"处理消息时出错: {e}")


# 为特定服务器生成战斗视图
async def send_battle_state_to_server(battle: BattleInstance, server_id: str):
    """为特定服务器生成战斗视图"""
    # 找到该服务器上的玩家
    player_id = next(
        (pid for pid in battle.players if online_players.get(pid) == server_id),
        None
    )

    if not player_id:
        logger.warning(f"找不到服务器 {server_id} 上的玩家")
        return

    view = build_player_view(battle, player_id)
    await manager.send_message({
        "type": "battle_update",
        "battle_id": battle.battle_id,
        "turn": battle.turn,
        "view": view
    }, server_id)

def build_player_view(battle: BattleInstance, player_id: str) -> dict:
    """为特定玩家构建战斗视图"""
    player_data = battle.players[player_id]
    opponent_id = next(pid for pid in battle.players if pid != player_id)
    opponent_data = battle.players[opponent_id]

    # 只处理当前玩家的数据
    player_view = {
        "self": {
            "active": player_data["active"],
            "team": [{
                "hp": p["hp"],
                "max_hp": p["max_hp"],
                "status": p["status"],
                "stat_mod": p["stat_mod"],
                "moves": [
                    {"name": m["name"], "current_pp": m["current_pp"], "max_pp": m["max_pp"]}
                    for m in p["moves"]
                ] if idx == player_data["active"] else []
            } for idx, p in enumerate(player_data["team"])]
        },
        "opponent": {
            "active": opponent_data["active"],
            "team": [{
                "hp_percent": int(100 * p["hp"] / p["max_hp"]) if p["max_hp"] > 0 else 0,
                "status": p["status"]
            } for p in opponent_data["team"]]
        }
    }

    # 自动切换后的状态更新
    if battle.players[player_id].get("auto_switched"):
        player_view["auto_switched"] = True
        battle.players[player_id]["auto_switched"] = False  # 重置标志
    else:
        player_view["auto_switched"] = False

    return player_view

async def broadcast_battle_state(battle: BattleInstance):
    """广播战斗状态给相关服务器"""
    player_views = {}

    for player_id in battle.players:
        player_data = battle.players[player_id]
        opponent_id = next(pid for pid in battle.players if pid != player_id)
        opponent_data = battle.players[opponent_id]

        for pokemon in player_data["team"]:
            if pokemon["max_hp"] > 0:
                pokemon["hp_percent"] = int(100 * pokemon["hp"] / pokemon["max_hp"])
            else:
                pokemon["hp_percent"] = 0

        safe_opponent_team = []
        for pokemon in opponent_data["team"]:
            hp_percent = int(100 * pokemon["hp"] / pokemon["max_hp"]) if pokemon["max_hp"] > 0 else 0
            safe_opponent_team.append({
                "hp_percent": hp_percent,
                "status": pokemon["status"]
            })

        player_views[player_id] = {
            "self": {
                "active": player_data["active"],
                "team": [{
                    "hp": p["hp"],
                    "max_hp": p["max_hp"],
                    "status": p["status"],
                    "stat_mod": p["stat_mod"],
                    "pp": [m["current_pp"] for m in p["moves"]]
                } for p in player_data["team"]]
            },
            "opponent": {
                "active": opponent_data["active"],
                "team": safe_opponent_team
            }
        }

    for player_id, view in player_views.items():
        # 确保包含PP值
        active_pokemon = battle.players[player_id]["team"][0]
        view["self"]["team"][0]["moves"] = [
            {
                "name": m["name"],
                "current_pp": m["current_pp"],
                "max_pp": m["max_pp"],
            }
            for m in active_pokemon["moves"]
        ]
        server_id = online_players.get(player_id)
        if server_id:
            await manager.send_message({
                "type": "battle_update",
                "battle_id": battle.battle_id,
                "turn": battle.turn,
                "view": view
            }, server_id)

# ===================== 战斗结果处理 =====================
async def handle_battle_result(result: BattleResult):
    with get_db_connection() as conn:
        cursor = conn.cursor()

        old_winner = get_rating(result.winner, cursor)
        old_loser = get_rating(result.loser, cursor)

        cursor.execute("""
            UPDATE battles 
            SET winner = ?
            WHERE battle_id = ?
        """, (result.winner, result.battle_id))

        calculate_elo(result.winner, result.loser, cursor)

        new_winner = get_rating(result.winner, cursor)
        new_loser = get_rating(result.loser, cursor)

        conn.commit()

    if result.battle_id in active_battles:
        del active_battles[result.battle_id]
        active_battles.pop(result.battle_id, None)  # 安全删除

    winner_name = player_names.get(result.winner, result.winner)
    loser_name = player_names.get(result.loser, result.loser)

    battle_logger.info(
        f"[BattleID: {result.battle_id}] 胜者: {winner_name} ({result.winner}), 败者: {loser_name} ({result.loser}) | "
        f"ELO: {old_winner} → {new_winner} (+{new_winner - old_winner}), "
        f"{old_loser} → {new_loser} ({new_loser - old_loser})"
    )

    # 按服务器分组发送消息
    server_messages = {}
    for player_id in [result.winner, result.loser]:
        server_id = online_players.get(player_id)
        if server_id:
            if server_id not in server_messages:
                server_messages[server_id] = {
                    "elo_updates": [],
                    "battle_ended": False
                }

            # 添加ELO更新
            elo_change = new_winner - old_winner if player_id == result.winner else new_loser - old_loser
            server_messages[server_id]["elo_updates"].append({
                "player_id": player_id,
                "old_rating": old_winner if player_id == result.winner else old_loser,
                "new_rating": new_winner if player_id == result.winner else new_loser,
                "rating_change": elo_change
            })

    # 发送合并消息
    for server_id, messages in server_messages.items():
        await manager.send_message({
            "type": "battle_result",
            "battle_id": result.battle_id,
            "winner": result.winner,
            "elo_updates": messages["elo_updates"]
        }, server_id)

    return {"message": "战斗结束，Elo 分数已更新"}

async def main():
    asyncio.create_task(periodic_match_check())
    asyncio.create_task(server_monitor())
    asyncio.create_task(record_status_history())
    asyncio.create_task(periodic_battle_processing())

    config = uvicorn.Config(
        app,
        host="0.0.0.0",
        port=PORT,
        log_config=None,
        timeout_keep_alive=120
    )
    server = uvicorn.Server(config)
    await server.serve()

if __name__ == "__main__":
    asyncio.run(main())