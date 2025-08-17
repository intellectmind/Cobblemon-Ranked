package cn.kurt6.cobblemon_ranked.crossserver

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object CrossServerSocket {
    val modVersion = "1.2.0"

    var webSocket: WebSocket? = null
    private val config = CobblemonRanked.config
    private var manualDisconnect = false

    private fun JsonObject.deepCopy(): JsonObject {
        return JsonParser.parseString(toString()).asJsonObject
    }

    private val playerMap = ConcurrentHashMap<String, ServerPlayerEntity>()
    val battleSessions = ConcurrentHashMap<String, BattleSession>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private var heartbeatJob: Job? = null
    private var connectionInitiator: ServerCommandSource? = null

    private fun cleanupConnection() {
        webSocket = null
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun connect(source: ServerCommandSource? = null) {
        manualDisconnect = false // 重置标记

        // 每次都从全局配置获取最新值
        val currentConfig = CobblemonRanked.config

        if (!currentConfig.enableCrossServer) {
            CobblemonRanked.logger.info("Cross-server matchmaking is disabled, skipping connection")
            return
        }

        cleanupConnection()
        // 保存连接发起者（如果有）
        connectionInitiator = source

        val baseUrl = currentConfig.cloudWebSocketUrl.removeSuffix("/")
        val fullUrl = "$baseUrl/${currentConfig.cloudServerId}?token=${currentConfig.cloudToken}&version=${modVersion}"

        CobblemonRanked.logger.info("Using config: cloudServerId=${currentConfig.cloudServerId}, token=${currentConfig.cloudToken.take(3)}, apiUrl=${currentConfig.cloudApiUrl}")

        val request = Request.Builder().url(fullUrl).build()
        webSocket = httpClient.newWebSocket(request, socketListener)
    }

    private fun startHeartbeat() {
        val lang = CobblemonRanked.config.defaultLang
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(IO).launch {
            log(MessageConfig.get("cross.log.heartbeat_start", lang))
            // 向连接发起者发送成功消息（如果有）
            connectionInitiator?.let { source ->
                val lang = config.defaultLang
                val successMessage = MessageConfig.get("cross.log.heartbeat_start", lang)
                source.sendMessage(Text.literal(successMessage))
            }
            while (isActive) {
                delay(20_000)

                try {
                    val ping = JsonObject().apply {
                        addProperty("type", "ping")
                    }
                    val message = ping.toString()
                    webSocket?.send(message)
                } catch (e: Exception) {
                    logError("cross.log.heartbeat_failed", e, "error" to (e.message ?: "null"))
                    disconnect()
                    delay(5_000)
                    connect()
                }
            }
        }
    }

    private fun handleBattleEvent(json: JsonObject) {
        val eventType = json["event_type"].asString
        val battleId = json["battle_id"].asString
        val session = battleSessions.values.find { it.battleId == battleId } ?: return
        val player = session.player
        val lang = config.defaultLang

        when (eventType) {
            "turn_start" -> {
                requestBattleState(battleId)
            }
            "battle_start" -> {
                val player1 = json["player1"].asString
                val player2 = json["player2"].asString
                val pokemon1 = json["pokemon1"].asString
                val pokemon2 = json["pokemon2"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.start", lang))
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.players", lang,
                    "player1" to player1, "player2" to player2))
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.lead", lang,
                    "player" to player1, "pokemon" to pokemon1))
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.lead", lang,
                    "player" to player2, "pokemon" to pokemon2))
            }
            "move_used" -> {
                val playerName = json["player"].asString
                val pokemon = json["pokemon"].asString
                val move = json["move"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.move_used", lang,
                    "playerName" to playerName, "pokemon" to pokemon, "move" to move))
            }
            "move_missed" -> {
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.move_missed", lang))
            }
            "damage_dealt" -> {
                val targetPlayer = json["target_player"].asString
                val targetPokemon = json["target_pokemon"].asString
                val damage = json["damage"].asInt
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.damage_dealt", lang,
                    "targetPlayer" to targetPlayer, "targetPokemon" to targetPokemon, "damage" to damage))
            }
            "critical_hit" -> {
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.critical_hit", lang))
            }
            "effectiveness" -> {
                val effectiveness = json["effectiveness"].asFloat
                val messageKey = when {
                    effectiveness == 0f -> "cross.battle.effectiveness.none"
                    effectiveness <= 0.5f -> "cross.battle.effectiveness.very_bad"
                    effectiveness < 1f -> "cross.battle.effectiveness.bad"
                    effectiveness == 2f -> "cross.battle.effectiveness.super"
                    effectiveness > 2f -> "cross.battle.effectiveness.very_super"
                    effectiveness > 1f -> "cross.battle.effectiveness.good"
                    else -> ""
                }
                if (messageKey.isNotEmpty()) {
                    RankUtils.sendMessage(player, MessageConfig.get(messageKey, lang))
                }
            }
            "status_applied" -> {
                val pokemon = json["pokemon"].asString
                val status = json["status"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.status_applied", lang,
                    "pokemon" to pokemon, "status" to MessageConfig.get("cross.status.$status", lang)))
            }
            "status_damage" -> {
                val pokemon = json["pokemon"].asString
                val status = json["status"].asString
                val damage = json["damage"].asInt
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.status_damage", lang,
                    "pokemon" to pokemon, "status" to MessageConfig.get("cross.status.$status", lang), "damage" to damage))
            }
            "pokemon_fainted" -> {
                val playerName = json["player"].asString
                val pokemon = json["pokemon"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.pokemon_fainted", lang,
                    "playerName" to playerName, "pokemon" to pokemon))
            }
            "switch_out" -> {
                val playerName = json["player"].asString
                val pokemon = json["pokemon"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.switch_out", lang,
                    "playerName" to playerName, "pokemon" to pokemon))
            }
            "switch_in" -> {
                val playerName = json["player"].asString
                val pokemon = json["pokemon"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.switch_in", lang,
                    "playerName" to playerName, "pokemon" to pokemon))
            }
            "stat_change" -> {
                val pokemon = json["pokemon"].asString
                val stat = json["stat"].asString
                val direction = json["direction"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.stat_change", lang,
                    "pokemon" to pokemon,
                    "stat" to MessageConfig.get("cross.stat.$stat", lang),
                    "direction" to MessageConfig.get("cross.direction.$direction", lang)))
            }
            "ability_triggered" -> {
                val pokemon = json["pokemon"].asString
                val ability = json["ability"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.ability_triggered", lang,
                    "pokemon" to pokemon, "ability" to ability))
            }
            "move_unusable" -> {
                val pokemon = json["pokemon"].asString
                val move = json["move"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.move_unusable", lang,
                    "pokemon" to pokemon, "move" to move))
            }
            "battle_ended" -> {
                val winner = json["winner"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.ended", lang))
                if (winner == "forfeit") {
                    RankUtils.sendMessage(player, MessageConfig.get("cross.battle.forfeit_self", lang))
                } else if (player.uuidAsString == winner) {
                    RankUtils.sendMessage(player, MessageConfig.get("cross.battle.win", lang))
                } else {
                    RankUtils.sendMessage(player, MessageConfig.get("cross.battle.lose", lang))
                }
            }
            "slow_start_ended" -> {
                val pokemon = json["pokemon"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.slow_start_ended", lang,
                    "pokemon" to pokemon))
            }
            "opponent_action_taken" -> {
                val playerName = json["player"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.opponent_action_taken", lang,
                    "playerName" to playerName))
            }
            "timeout_move" -> {
                val playerName = json["player"].asString
                RankUtils.sendMessage(player, MessageConfig.get("cross.battle.timeout", lang,
                    "playerName" to playerName))
            }
        }
    }

    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private fun isConnected(): Boolean {
        return webSocket != null
    }
    private fun scheduleReconnect() {
        if (manualDisconnect) return
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(IO).launch {
            // 添加延迟确保完全断开
            delay(100)
            if (isConnected()) return@launch

            // +++ 超过10次停止重连 +++
            if (reconnectAttempts > 10) {
                log("cross.log.reconnect_stop")
                return@launch
            }

            reconnectAttempts++

            val delayTime = minOf(2_000L * (1 shl minOf(reconnectAttempts, 5)), 32_000L)
            delay(delayTime)

            if (webSocket == null) {
                log("cross.log.reconnect_attempt", "attempts" to reconnectAttempts)
                connect()
            }
        }
    }

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            log("cross.log.connected")
            reconnectAttempts = 0
            startHeartbeat()

            // 向连接发起者发送成功消息（如果有）
            connectionInitiator?.let { source ->
                val lang = config.defaultLang
                val successMessage = MessageConfig.get("cross.log.connected", lang)
                source.sendMessage(Text.literal(successMessage))
            }
            // 无论是否有源，都清空连接发起者
            connectionInitiator = null

            // 通知所有在队列中的玩家
            playerMap.values.forEach { player ->
                RankUtils.sendMessage(player, MessageConfig.get("cross.queue.connection_restored", config.defaultLang))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
//            log("cross.log.message_received", "message" to text) // 收到的服务器消息
            try {
                val json = JsonParser.parseString(text).asJsonObject
                when (val type = json["type"].asString) {
                    "battle_event" -> handleBattleEvent(json.getAsJsonObject("data"))
                    "match_found" -> handleMatchFound(json)
                    "battle_update" -> handleBattleUpdate(json)
                    "battle_ended" -> handleBattleEnded(json)
                    "chat" -> handleChatMessage(json)
                    "battle_result", "elo_update" -> handleEloUpdate(json)
                    "pong" -> handlePong(json)
                    else -> log("cross.log.unknown_message_type", "type" to type)
                }
            } catch (e: Exception) {
                logError("cross.log.parse_failed", e, "error" to (e.message ?: "null"), "raw" to text)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            log("cross.log.closing", "code" to code, "reason" to reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logError("cross.log.connection_failed", t, "error" to (t.message ?: "null"))
            cleanupConnection()
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            log("cross.log.connection_closed", "code" to code, "reason" to reason)
            disconnect()
            scheduleReconnect()
            // 通知所有在队列中的玩家
            playerMap.values.forEach { player ->
                RankUtils.sendMessage(player, MessageConfig.get("cross.queue.connection_lost", config.defaultLang))
            }
        }
    }

    private fun handlePong(json: JsonObject) {}

    // 处理宝可梦名称本地化
    private fun getLocalizedPokemonName(pokemon: JsonObject): String {
        return try {
            // 使用 name_key 字段
            val nameKey = pokemon.get("name_key")?.asString
                ?: "cobblemon.species.${pokemon.get("name").asString.lowercase()}.name"

            // 使用 CommblemonLang 获取本地化字符串
            val baseName = CommblemonLang.get(
                key = nameKey,
                lang = config.defaultLang,
                "name" to pokemon.get("name").asString
            )

            // 添加等级信息
            val level = pokemon.get("level")?.asInt ?: 1
            "$baseName Lv.$level"  // 在名字后添加等级
        } catch (e: Exception) {
            CobblemonRanked.logger.error("Failed to localize pokemon name", e)
            val baseName = pokemon.get("name").asString
            val level = pokemon.get("level")?.asInt ?: 1
            "$baseName Lv.$level"  // 在名字后添加等级
        }
    }

    private fun handleMatchFound(json: JsonObject) {
        val selfId = json["self_id"]?.asString ?: return
        val player = playerMap[selfId] ?: return
        val battleId = json["battle_id"]?.asString ?: return
        val opponentName = json["opponent_name"]?.asString ?: MessageConfig.get("cross.unknown_opponent", config.defaultLang)
        val lang = config.defaultLang

        val opponentTeamJson = json["opponent_team"]?.asJsonArray
        val opponentTeam = if (opponentTeamJson != null) {
            opponentTeamJson.map { it.asJsonObject }
        } else {
            val opponentActive = json["opponent_active"]?.asJsonObject
            if (opponentActive != null) {
                listOf(opponentActive)
            } else {
                logError("cross.log.missing_opponent_team")
                return
            }
        }

        val selfTeam = json["self_team"].asJsonArray.map { pokemon ->
            pokemon.asJsonObject
        }

        val session = BattleSession(
            battleId = battleId,
            player = player,
            opponentName = opponentName,
            opponentTeam = opponentTeam,
            selfTeam = selfTeam
        )
        battleSessions[player.uuidAsString] = session

        // PP值验证
        for (pokemon in selfTeam) {
            val moves = pokemon.getAsJsonArray("moves")
            moves?.forEach { moveElement ->
                val move = moveElement.asJsonObject
                if (move["current_pp"]?.asInt == 0) {
                    val maxPP = move["max_pp"]?.asInt ?: 10
                    move.addProperty("current_pp", maxPP)
                }
            }
        }

        // 发送匹配成功消息
        RankUtils.sendMessage(player, MessageConfig.get("cross.battle.match_found", lang))
        // 发送对手信息
        RankUtils.sendMessage(player, MessageConfig.get("cross.battle.opponent", lang, "name" to opponentName))

        // 获取完整的对手队伍
        val fullOpponentTeam = if (json.has("opponent_team")) {
            json["opponent_team"].asJsonArray.map { it.asJsonObject }
        } else {
            // 如果只有opponent_active，创建一个单宝可梦的临时队伍
            val active = json["opponent_active"]?.asJsonObject
            if (active != null) listOf(active) else emptyList()
        }

        // 发送对手队伍所有宝可梦
        RankUtils.sendMessage(player, MessageConfig.get("cross.battle.opponent_team", lang))
        fullOpponentTeam.forEachIndexed { index, pokemon ->
            val pokemonName = getLocalizedPokemonName(pokemon)
            val position = if (index == 0)
                MessageConfig.get("cross.lead", lang)
            else
                "${index + 1}. "

            RankUtils.sendMessage(player, "$position$pokemonName")
        }

        // 发送玩家队伍信息
        RankUtils.sendMessage(player, MessageConfig.get("cross.battle.your_team", lang))

        // 只显示第一只宝可梦
        if (selfTeam.isNotEmpty()) {
            val firstPokemon = selfTeam[0]
            val name = getLocalizedPokemonName(firstPokemon)
            val hp = firstPokemon["hp"]?.asInt ?: 0
            val maxHp = firstPokemon["max_hp"]?.asInt ?: 1
            RankUtils.sendMessage(
                player,
                MessageConfig.get("cross.battle.pokemon_info", lang, "name" to name, "hp" to hp, "maxHp" to maxHp)
            )
        }

        // 更新玩家状态：进入战斗
        player.addCommandTag("ranked_cross_in_battle1")

        // 显示更换宝可梦选项
        showSwitchOptions(player, session)

        showCurrentPokemonMoves(player, session)
        RankUtils.sendMessage(player, MessageConfig.get("cross.battle.forfeit_command", lang))
        RankUtils.sendMessage(player, MessageConfig.get("cross.battle.chat", lang))
        RankUtils.sendMessage(player, MessageConfig.get("cross.battle.turn_start", lang, "turn" to 1))

        requestBattleState(battleId)
    }

    private fun requestBattleState(battleId: String) {
        val message = JsonObject().apply {
            addProperty("type", "request_battle_state")
            addProperty("battle_id", battleId)
        }
        webSocket?.send(message.toString())
//        log("cross.log.request_battle_state", "battleId" to battleId)
    }

    private fun handleBattleUpdate(json: JsonObject) {
        val battleId = json["battle_id"]?.asString ?: return
        val session = battleSessions.values.find { it.battleId == battleId } ?: return
        val player = session.player
        val turn = json["turn"]?.asInt ?: 0
        val view = json["view"]?.asJsonObject ?: return
        val lang = config.defaultLang

        val selfState = view["self"]?.asJsonObject ?: return
        val selfActive = selfState["active"]?.asInt ?: 0
        val selfTeam = selfState["team"]?.asJsonArray ?: return

        if (turn == 0) return

        session.selfActiveIndex = selfActive

        val selfPokemon = if (selfActive >= 0 && selfActive < selfTeam.size()) {
            selfTeam.get(selfActive)?.asJsonObject
        } else {
            null
        }

        val selfPokemonName = if (selfActive < session.selfTeam.size) {
            getLocalizedPokemonName(session.selfTeam[selfActive])
        } else {
            "你的宝可梦${selfActive + 1}"
        }

        val selfHp = selfPokemon?.get("hp")?.asInt ?: 0
        val selfMaxHp = selfPokemon?.get("max_hp")?.asInt ?: 1
        val selfStatusElement = selfPokemon?.get("status")
        val selfStatus = when {
            selfStatusElement == null || selfStatusElement.isJsonNull -> MessageConfig.get("cross.status.normal", lang)
            else -> MessageConfig.get("cross.status.${selfStatusElement.asString}", lang)
        }

        val opponentState = view["opponent"]?.asJsonObject ?: return
        val opponentActive = opponentState["active"]?.asInt ?: 0
        val opponentTeam = opponentState["team"]?.asJsonArray ?: return

        val opponentPokemonName = if (opponentActive < session.opponentTeam.size) {
            getLocalizedPokemonName(session.opponentTeam[opponentActive])
        } else {
            "对手的宝可梦${opponentActive + 1}"
        }

        val opponentPokemon = if (opponentActive >= 0 && opponentActive < opponentTeam.size()) {
            opponentTeam.get(opponentActive)?.asJsonObject
        } else {
            null
        }

        val opponentStatusElement = opponentPokemon?.get("status")
        val opponentStatus = when {
            opponentStatusElement == null || opponentStatusElement.isJsonNull -> MessageConfig.get("cross.status.normal", lang)
            else -> MessageConfig.get("cross.status.${opponentStatusElement.asString}", lang)
        }

        val opponentHpPercent = opponentPokemon?.get("hp_percent")?.asInt ?: 0

        // 获取PP值数据
        val selfMoves = selfPokemon?.getAsJsonArray("moves")
        val selfMovesFull = session.selfTeam.getOrNull(selfActive)?.getAsJsonArray("moves")
        val movePP = StringBuilder(MessageConfig.get("cross.battle.current_pp", lang) + "\n")

        player.sendMessage(Text.literal(MessageConfig.get("cross.battle.current_pp", lang)))

        selfMoves?.forEachIndexed { index, moveElement ->
            val moveCurrent = moveElement.asJsonObject
            // 获取匹配时存储的完整招式数据
            val moveFull = if (selfMovesFull != null && index < selfMovesFull.size()) {
                selfMovesFull.get(index).asJsonObject
            } else {
                moveCurrent
            }

            // 使用完整数据获取本地化名称
            val nameKey = moveFull["name_key"]?.asString ?: "cobblemon.move.${moveFull["name"]?.asString ?: "unknown"}"
            val name = getLocalizedString(nameKey, lang, moveFull["name"]?.asString ?: "???")

            val currentPP = moveCurrent["current_pp"]?.asInt ?: 0
            val maxPP = moveFull["max_pp"]?.asInt ?: 0
            val hoverText = buildMoveHoverText(moveFull, lang)

            player.sendMessage(Text.empty()
                .append(link("[${index + 1}]", "/rank cross battle move ${index + 1}", hoverText))
                .append(Text.literal(" $name §7(PP: $currentPP/$maxPP)")))
        }


        // 更新当前队伍状态
        val selfTeamArray = selfState["team"]?.asJsonArray ?: return
        session.currentSelfTeam.clear()
        selfTeamArray.forEach { element ->
            session.currentSelfTeam.add(element.asJsonObject)
        }

        showSwitchOptions(player, session)

        val battleInfo = MessageConfig.get("cross.battle.state_title", lang, "turn" to turn) + "\n" +
                MessageConfig.get("cross.battle.your_pokemon", lang, "name" to selfPokemonName) + " " +
                MessageConfig.get("cross.battle.hp", lang, "current" to selfHp, "max" to selfMaxHp) + " " +
                MessageConfig.get("cross.battle.status", lang, "status" to selfStatus) + "\n" +
//                movePP.toString().trim() + "\n" +
                MessageConfig.get("cross.battle.opponent_pokemon", lang, "name" to opponentPokemonName) + " " +
                MessageConfig.get("cross.battle.hp_percent", lang, "percent" to opponentHpPercent) + " " +
                MessageConfig.get("cross.battle.status", lang, "status" to opponentStatus)

        // +++ 重置行动选择状态 +++
        session.resetActions()

        RankUtils.sendMessage(player, battleInfo)
        RankUtils.sendMessage(player, "================================")
        RankUtils.sendMessage(player, MessageConfig.get("cross.battle.turn_start", lang, "turn" to turn + 1))
    }

    private fun handleBattleEnded(json: JsonObject) {
        val battleId = json["battle_id"]?.asString ?: return
        val winnerId = json["winner"]?.asString ?: return
        val lang = config.defaultLang

        val session = battleSessions.values.find { it.battleId == battleId } ?: return
        val player = session.player

        // +++ 确保完全清除所有状态 +++
        player.removeCommandTag("ranked_cross_in_battle1")
        player.removeCommandTag("ranked_cross_in_queue")
        playerMap.remove(player.uuidAsString)
        battleSessions.remove(player.uuidAsString)

        RankUtils.sendMessage(player, MessageConfig.get("cross.battle.ended", lang))
        if (winnerId == "forfeit") {
            RankUtils.sendMessage(player, MessageConfig.get("cross.battle.forfeit_self", lang))
        } else if (player.uuidAsString == winnerId) {
            RankUtils.sendMessage(player, MessageConfig.get("cross.battle.win", lang))
        } else {
            RankUtils.sendMessage(player, MessageConfig.get("cross.battle.lose", lang))
        }
    }

    private fun handleEloUpdate(json: JsonObject) {
        // 检查消息格式（可能是elo_update或battle_result）
        val updateJson = if (json.has("elo_updates")) {
            // 处理battle_result格式
            val updates = json.getAsJsonArray("elo_updates")
            if (updates.size() > 0) updates[0].asJsonObject else null
        } else {
            json
        } ?: return

        // 解析数据
        val playerId = updateJson["player_id"].asString
        val oldRating = updateJson["old_rating"].asInt
        val newRating = updateJson["new_rating"].asInt
        val ratingChange = updateJson["rating_change"].asInt

        // 发送消息给玩家
        val lang = config.defaultLang
        val player = playerMap[playerId]
        if (player != null) {
            val changeSymbol = if (ratingChange >= 0) "+" else ""
            RankUtils.sendMessage(player, MessageConfig.get("cross.elo.update", lang,
                "oldRating" to oldRating,
                "newRating" to newRating,
                "change" to "$changeSymbol$ratingChange"))
        } else {
            log("cross.log.player_not_found", "playerId" to playerId)
        }
    }

    private fun handleChatMessage(json: JsonObject) {
        val battleId = json["battle_id"]?.asString ?: return
        val message = json["message"]?.asString ?: return
        val from = json["from"]?.asString ?: return
        val lang = config.defaultLang

        val session = battleSessions.values.find { it.battleId == battleId } ?: return
        val player = session.player

        if (from != player.uuidAsString) {
            RankUtils.sendMessage(player, MessageConfig.get("cross.chat.message", lang,
                "opponentName" to session.opponentName, "message" to message))
        }
    }

    fun joinMatchmakingQueue(player: ServerPlayerEntity, pokemonList: List<JsonObject>, mode: String) {
        val lang = config.defaultLang
        if (!config.enableCrossServer) {
            RankUtils.sendMessage(player, MessageConfig.get("cross.cross_server_disabled", lang))
            return
        }

        if (webSocket == null) {
            RankUtils.sendMessage(player, MessageConfig.get("cross.queue.not_connected", lang))
            return
        }

        if (playerMap.containsKey(player.uuidAsString)) {
            RankUtils.sendMessage(player, MessageConfig.get("cross.queue.already_in_queue", lang))
            return
        }

        playerMap[player.uuidAsString] = player

        val json = JsonObject().apply {
            addProperty("player_id", player.uuidAsString)
            addProperty("player_name", player.name.string)
            addProperty("server", config.cloudServerId)
            addProperty("mode", mode)
            add(
                "pokemons",
                pokemonList.fold(JsonParser.parseString("[]").asJsonArray) { arr, p -> arr.apply { add(p) } })
        }

        sendHttpRequest(
            url = "${config.cloudApiUrl}/join-queue",
            body = json,
            onSuccess = {
                RankUtils.sendMessage(player, MessageConfig.get("cross.queue.join_success", lang, "mode" to mode))
                // 添加跨服匹配标识
                player.addCommandTag("ranked_cross_in_queue")
                        },
            onError = { errorMsg ->
                // 处理403错误
                val message = if (errorMsg.contains("410")) {
                    MessageConfig.get("cross.queue.join_failed.authenticated_only", lang)
                }
                else if (errorMsg.contains("429")) {
                    MessageConfig.get("cross.queue.join_failed.battles_exceeds", lang)
                }
                else {
                    MessageConfig.get("cross.queue.join_failed", lang, "error" to errorMsg)
                }
                playerMap.remove(player.uuidAsString)
                player.removeCommandTag("ranked_cross_in_queue")
                RankUtils.sendMessage(player, message)
            }
        )
    }

    fun leaveMatchmakingQueue(player: ServerPlayerEntity) {
        val lang = config.defaultLang
        if (!config.enableCrossServer) {
            RankUtils.sendMessage(player, MessageConfig.get("cross.cross_server_disabled", lang))
            return
        }

        if (webSocket == null) {
            RankUtils.sendMessage(player, MessageConfig.get("cross.queue.not_connected", lang))
            return
        }

        val json = JsonObject().apply {
            addProperty("player_id", player.uuidAsString)
        }

        playerMap.remove(player.uuidAsString)

        sendHttpRequest(
            url = "${config.cloudApiUrl}/leave-queue",
            body = json,
            onSuccess = {
                RankUtils.sendMessage(player, MessageConfig.get("cross.queue.leave_success", lang))
                // 移除队列标签
                player.removeCommandTag("ranked_cross_in_queue")
                        },
            onError = {
                RankUtils.sendMessage(player, MessageConfig.get("cross.queue.leave_failed", lang, "error" to it))
                playerMap.remove(player.uuidAsString)
                player.removeCommandTag("ranked_cross_in_queue")
            }
        )
    }

    fun sendBattleCommand(player: ServerPlayerEntity, commandType: String, data: String) {
        val lang = config.defaultLang
        val session = battleSessions[player.uuidAsString] ?: run {
            RankUtils.sendMessage(player, MessageConfig.get("cross.battle.no_active", lang))
            return
        }

        // +++ 检查是否已选择行动 +++
        if (session.hasChosenAction(player.uuidAsString)) {
            RankUtils.sendMessage(player, MessageConfig.get("cross.battle.already_chosen", lang))
            return
        }

        val commandStr = when (commandType) {
            "move" -> "{\"type\":\"move\",\"slot\":$data}"
            "switch" -> "{\"type\":\"switch\",\"slot\":$data}"
            "forfeit" -> "{\"type\":\"forfeit\"}"
            else -> return
        }

        val message = JsonObject().apply {
            addProperty("type", "battle_command")
            addProperty("battle_id", session.battleId)
            addProperty("player_id", player.uuidAsString)
            addProperty("command", commandStr)
        }

        // 标记行动已选择
        session.markActionChosen(player.uuidAsString)

//        log("cross.log.send_battle_command",
//            "battleId" to session.battleId,
//            "playerId" to player.uuidAsString,
//            "command" to commandStr)

        webSocket?.send(message.toString())
        RankUtils.sendMessage(player, MessageConfig.get("cross.battle.command_sent", lang, "command" to commandType.uppercase()))
    }

    private fun getLocalizedString(key: String, lang: String, fallback: String): String {
        return try {
            // 1. 首先尝试从硬编码语言数据获取
            val hardcodedResult = CommblemonLang.get(key, lang)
            if (hardcodedResult != key) {
                return hardcodedResult
            }

            // 2. 尝试去除后缀的键名（如 .name/.desc）
            if (key.contains('.')) {
                val baseKey = key.substringBeforeLast('.')
                // 直接使用 Lang 获取翻译
                val baseTranslated = CommblemonLang.get(baseKey, lang)
                if (baseTranslated != baseKey) {
                    return baseTranslated
                }
            }

            // 3. 尝试从硬编码的英文回退
            if (lang != "en") {
                val englishFallback = CommblemonLang.get(key, "en")
                if (englishFallback != key) {
                    return englishFallback
                }
            }

            // 4. 最终回退
            fallback
        } catch (e: Exception) {
            // 记录错误并返回回退值
            logError("cross.log.localize_string_failed", e,
                "key" to key,
                "lang" to lang,
                "error" to (e.message ?: "unknown")) // 移除不安全的类型转换
            fallback
        }
    }

    private fun buildMoveHoverText(move: JsonObject, lang: String): String {
        // 获取基础属性值
        val type = move["type"]?.asString ?: "???"
        val category = move["category"]?.asString ?: "???"
        val power = move["power"]?.asInt ?: 0
        val accuracy = move["accuracy"]?.asInt ?: 0

        // 根据语言配置本地化属性类型和分类
        val localizedType = if (lang == "zh") {
            when (type.lowercase()) {
                "normal" -> "一般"
                "fire" -> "火"
                "water" -> "水"
                "electric" -> "电"
                "grass" -> "草"
                "ice" -> "冰"
                "fighting" -> "格斗"
                "poison" -> "毒"
                "ground" -> "地面"
                "flying" -> "飞行"
                "psychic" -> "超能力"
                "bug" -> "虫"
                "rock" -> "岩石"
                "ghost" -> "幽灵"
                "dragon" -> "龙"
                "dark" -> "恶"
                "steel" -> "钢"
                "fairy" -> "妖精"
                else -> type
            }
        } else {
            type
        }

        val localizedCategory = if (lang == "zh") {
            when (category.lowercase()) {
                "physical" -> "物理"
                "special" -> "特殊"
                "status" -> "变化"
                else -> category
            }
        } else {
            category
        }

        // +++ 修复描述获取逻辑 +++
        val moveDescription = when {
            move.has("description_key") -> {
                // 直接使用 CommblemonLang 获取描述
                CommblemonLang.get(move["description_key"].asString, lang)
            }
            move.has("description") -> {
                move["description"].asString
            }
            else -> ""
        }

        // 获取本地化文本
        val typeText = MessageConfig.get("cross.move.type", lang, "type" to localizedType)
        val powerText = MessageConfig.get("cross.move.power", lang, "power" to power)
        val accuracyText = if (accuracy == 0) {
            MessageConfig.get("cross.move.accuracy.sure_hit", lang)
        } else {
            MessageConfig.get("cross.move.accuracy", lang, "accuracy" to accuracy)
        }
        val categoryText = MessageConfig.get("cross.move.category", lang, "category" to localizedCategory)

        return """
    $moveDescription
    $typeText | $powerText | $accuracyText | $categoryText
    """.trimIndent()
    }

    private fun showCurrentPokemonMoves(player: ServerPlayerEntity, session: BattleSession) {
        val lang = config.defaultLang
        // 使用初始队伍获取完整的技能信息（包含name_key等）
        val initialPokemon = session.selfTeam.getOrNull(session.selfActiveIndex) ?: return
        val initialMoves = initialPokemon.getAsJsonArray("moves") ?: return

        // 使用当前队伍获取实时PP值
        val currentPokemon = session.currentSelfTeam.getOrNull(session.selfActiveIndex) ?: return
        val currentMoves = currentPokemon.getAsJsonArray("moves") ?: return

        // 发送标题
        player.sendMessage(Text.literal(MessageConfig.get("cross.battle.current_moves", lang)))

        var displayedCount = 0
        for (i in 0 until minOf(4, initialMoves.size())) {
            val initialMove = initialMoves.get(i).asJsonObject
            val currentMove = if (i < currentMoves.size()) currentMoves.get(i).asJsonObject else initialMove

            // 获取PP值（优先使用currentMove中的值）
            val currentPP = currentMove["current_pp"]?.asInt ?: initialMove["max_pp"]?.asInt ?: 0
            val maxPP = initialMove["max_pp"]?.asInt ?: 0

            // 获取技能名称（使用initialMove中的完整信息）
            val nameKey = initialMove["name_key"]?.asString
                ?: "cobblemon.move.${initialMove["name"]?.asString ?: "unknown"}"
            val moveName = getLocalizedString(nameKey, lang, initialMove["name"]?.asString ?: "???")

            // 构建悬浮文本（使用initialMove中的完整信息）
            val hoverText = buildMoveHoverText(initialMove, lang)

            player.sendMessage(Text.empty()
                .append(link("[${++displayedCount}]", "/rank cross battle move ${i + 1}", hoverText))
                .append(Text.literal(" $moveName §7(PP: $currentPP/$maxPP)"))
            )
        }

        if (displayedCount == 0) {
            player.sendMessage(Text.literal(MessageConfig.get("cross.battle.no_moves_available", lang)))
        }

        player.sendMessage(Text.literal(MessageConfig.get("cross.battle.click_hint", lang)))
    }

    private fun showSwitchOptions(player: ServerPlayerEntity, session: BattleSession) {
        val lang = config.defaultLang

        // 使用currentSelfTeam获取实时血量
        val team = session.currentSelfTeam

        // 发送标题
        RankUtils.sendMessage(player, MessageConfig.get("cross.battle.switch_options", lang))

        // 显示所有宝可梦选项（过滤血量为0的宝可梦）
        team.forEachIndexed { index, pokemon ->
            // 跳过血量为0的宝可梦
            val hp = pokemon["hp"]?.asInt ?: 0
            if (hp <= 0) return@forEachIndexed

            // 使用初始队伍获取名称（确保有name_key字段）
            val name = getLocalizedPokemonName(session.selfTeam[index])
            val maxHp = pokemon["max_hp"]?.asInt ?: 1

            // 计算百分比（避免除以0）
            val hpPercent = if (maxHp > 0) (hp * 100) / maxHp else 0

            // 创建可点击的切换链接
            val switchText = Text.empty()
                .append(link("[${index + 1}]", "/rank cross battle switch ${index + 1}"))
                .append(Text.literal(" $name §7(HP: $hpPercent%)"))

            player.sendMessage(switchText)
        }
    }

    private fun link(text: String, command: String, hoverKey: String? = null): Text {
        val lang = CobblemonRanked.config.defaultLang
        val hoverText = hoverKey?.let {
            MessageConfig.get(it, lang, "command" to command)
        } ?: MessageConfig.get(
            "command.hint",  // 使用配置系统中的默认key
            lang,
            "command" to command
        )

        return Text.literal(text).setStyle(
            Style.EMPTY
                .withColor(0x55FF55)  // 保留原有的绿色
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hoverText)))
        )
    }

    fun sendChatMessage(player: ServerPlayerEntity, message: String) {
        val lang = config.defaultLang
        val session = battleSessions[player.uuidAsString] ?: run {
            RankUtils.sendMessage(player, MessageConfig.get("cross.battle.no_active", lang))
            return
        }

        val chatJson = JsonObject().apply {
            addProperty("type", "chat")
            addProperty("battle_id", session.battleId)
            addProperty("message", message)
            addProperty("player_id", player.uuidAsString)
        }

        webSocket?.send(chatJson.toString())
    }

    private fun sendHttpRequest(url: String, body: JsonObject, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val mediaType = "application/json".toMediaType()
        val requestBody = body.toString().toRequestBody(mediaType)

        val request = Request.Builder().url(url).post(requestBody).build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: MessageConfig.get("cross.error.unknown", config.defaultLang))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) onSuccess()
                else onError("HTTP ${response.code}")
                response.close()
            }
        })
    }

    fun disconnect() {
        manualDisconnect = true // 标记为手动断开
        log("cross.log.disconnected")
        webSocket?.close(1000, "close")
        webSocket = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()  // 取消计划中的重连
        reconnectJob = null      // 清空引用
        reconnectAttempts = 0    // 重置重连计数器
        playerMap.clear()
        battleSessions.clear()
    }

    fun handlePlayerDisconnect(player: ServerPlayerEntity) {
        // 清除标签
        player.removeCommandTag("ranked_cross_in_queue")
        player.removeCommandTag("ranked_cross_in_battle1")

        // 处理战斗中的投降
        if (battleSessions.containsKey(player.uuidAsString)) {
            sendForfeitCommand(player)
        }
        // 处理队列中的离开
        else if (playerMap.containsKey(player.uuidAsString)) {
            leaveMatchmakingQueue(player)
        }

        // 确保完全移除
        playerMap.remove(player.uuidAsString)
        battleSessions.remove(player.uuidAsString)
    }

    fun handlePlayerJoin(player: ServerPlayerEntity) {
        // 强制清除可能残留的标签
        player.removeCommandTag("ranked_cross_in_queue")
        player.removeCommandTag("ranked_cross_in_battle1")

        // 从匹配队列和战斗会话中移除玩家（防御性编程）
        playerMap.remove(player.uuidAsString)
        battleSessions.remove(player.uuidAsString)
    }

    private fun sendForfeitCommand(player: ServerPlayerEntity) {
        val session = battleSessions[player.uuidAsString] ?: return
        val message = JsonObject().apply {
            addProperty("type", "battle_command")
            addProperty("battle_id", session.battleId)
            addProperty("player_id", player.uuidAsString)
            addProperty("command", "{\"type\":\"forfeit\"}")
        }

//        log("cross.log.auto_forfeit", "player" to player.name.string, "battleId" to session.battleId)
        webSocket?.send(message.toString())
        playerMap.remove(player.uuidAsString)
        battleSessions.remove(player.uuidAsString)
    }

    private fun log(messageKey: String, vararg args: Pair<String, Any>) {
        val lang = config.defaultLang
        CobblemonRanked.logger.info("[Cross-server] ${MessageConfig.get(messageKey, lang, *args)}")
    }

    private fun logError(messageKey: String, exception: Throwable? = null, vararg args: Pair<String, Any>) {
        val lang = config.defaultLang
        val message = MessageConfig.get(messageKey, lang, *args)
        CobblemonRanked.logger.error("[Cross-server] $message")
//        exception?.let { CobblemonRanked.logger.error("异常详情", it) }
    }

    data class BattleSession(
        val battleId: String,
        val player: ServerPlayerEntity,
        val opponentName: String,
        val opponentTeam: List<JsonObject>,
        val selfTeam: List<JsonObject>
    ) {
        var selfActiveIndex: Int = 0
        var opponentActiveIndex: Int = 0
        // +++ 追踪行动选择状态 +++
        val actionChosen: MutableSet<String> = mutableSetOf()

        var currentSelfTeam: MutableList<JsonObject> = mutableListOf<JsonObject>().apply {
            addAll(selfTeam.map { it.deepCopy() })
        }

        fun hasChosenAction(playerId: String): Boolean {
            return playerId in actionChosen
        }

        fun markActionChosen(playerId: String) {
            actionChosen.add(playerId)
        }

        fun resetActions() {
            actionChosen.clear()
        }
    }
}