package cn.kurt6.cobblemon_ranked.data

import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.logger
import cn.kurt6.cobblemon_ranked.config.DatabaseConfig
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.SQLTimeoutException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class RankDao(dbConfig: DatabaseConfig, configDir: File) {
    private val database: Database
    private val isMySQL: Boolean
    private val connectionTimeoutMs = 5000L
    private val queryTimeoutMs = 3000L
    init {
        isMySQL = dbConfig.databaseType.lowercase() == "mysql"

        database = when (dbConfig.databaseType.lowercase()) {
            "mysql" -> {
                try {
                    val mysql = dbConfig.mysql
                    val jdbcUrl = "jdbc:mysql://${mysql.host}:${mysql.port}/${mysql.database}?${mysql.parameters}"
                    Database.connect(
                        url = jdbcUrl,
                        driver = "com.mysql.cj.jdbc.Driver",
                        user = mysql.username,
                        password = mysql.password,
                        setupConnection = {
                            it.createStatement().queryTimeout = TimeUnit.MILLISECONDS.toSeconds(queryTimeoutMs).toInt()
                        }
                    )
                } catch (e: Exception) {
                    logger.error("Failed to connect to MySQL, falling back to SQLite: ${e.message}")
                    val dbFile = configDir.resolve(dbConfig.sqliteFile)
                    val url = "jdbc:sqlite:${dbFile.absolutePath}?busy_timeout=$connectionTimeoutMs"
                    Database.connect(
                        url = url,
                        driver = "org.sqlite.JDBC",
                        setupConnection = {
                            it.createStatement().queryTimeout = TimeUnit.MILLISECONDS.toSeconds(queryTimeoutMs).toInt()
                        }
                    )
                }
            }
            "sqlite" -> {
                val dbFile = configDir.resolve(dbConfig.sqliteFile)
                val url = "jdbc:sqlite:${dbFile.absolutePath}?busy_timeout=$connectionTimeoutMs"
                Database.connect(
                    url = url,
                    driver = "org.sqlite.JDBC",
                    setupConnection = {
                        it.createStatement().queryTimeout = TimeUnit.MILLISECONDS.toSeconds(queryTimeoutMs).toInt()
                    }
                )
            }
            else -> throw IllegalArgumentException("Unsupported database type: ${dbConfig.databaseType}")
        }

        executeWithRetry("初始化数据库表", maxRetries = 1) {
            transaction(db = database) {
                SchemaUtils.createMissingTablesAndColumns(
                    PlayerRankTable,
                    SeasonInfoTable,
                    PokemonUsageTable,
                    ReturnLocationTable
                )
            }
        }
    }

    private fun <T> executeWithTimeout(operation: String, block: () -> T): T {
        val startTime = TimeSource.Monotonic.markNow()
        try {
            return block()
        } catch (e: Exception) {
            if (startTime.elapsedNow() > queryTimeoutMs.milliseconds) {
                throw SQLTimeoutException("数据库操作'$operation'超时（超过${queryTimeoutMs}毫秒）")
            }
            throw e
        }
    }

    private fun <T> executeWithRetry(
        operation: String,
        maxRetries: Int = 3,
        block: () -> T
    ): T {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return executeWithTimeout(operation, block)
            } catch (e: SQLTimeoutException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    val delayMs = 100L * (attempt + 1)
                    Thread.sleep(delayMs)
                    logger.warn("Database operation '$operation' timed out, retry ${attempt + 1}/$maxRetries (delay: ${delayMs}ms)")
                }
            } catch (e: Exception) {
                logger.error("Database operation '$operation' failed", e)
                throw e
            }
        }

        throw lastException ?: RuntimeException("Failed after $maxRetries retries: $operation")
    }

    object ReturnLocationTable : Table("player_return_location") {
        val playerId = varchar("player_id", 36)
        val worldId = varchar("world_id", 255)
        val x = double("x")
        val y = double("y")
        val z = double("z")
        val timestamp = long("timestamp").default(System.currentTimeMillis())

        override val primaryKey = PrimaryKey(playerId)
    }

    fun saveReturnLocation(playerId: UUID, worldId: String, x: Double, y: Double, z: Double) =
        executeWithRetry("Save return location") {
            transaction(database) {
                ReturnLocationTable.deleteWhere { ReturnLocationTable.playerId eq playerId.toString() }
                ReturnLocationTable.insert { row ->
                    row[ReturnLocationTable.playerId] = playerId.toString()
                    row[ReturnLocationTable.worldId] = worldId
                    row[ReturnLocationTable.x] = x
                    row[ReturnLocationTable.y] = y
                    row[ReturnLocationTable.z] = z
                    row[timestamp] = System.currentTimeMillis()
                }
            }
        }

    fun getReturnLocation(playerId: UUID): Pair<String, Triple<Double, Double, Double>>? =
        executeWithRetry("Get return location") {
            transaction(database) {
                ReturnLocationTable.select { ReturnLocationTable.playerId eq playerId.toString() }
                    .firstOrNull()?.let {
                        val worldId = it[ReturnLocationTable.worldId]
                        val x = it[ReturnLocationTable.x]
                        val y = it[ReturnLocationTable.y]
                        val z = it[ReturnLocationTable.z]
                        Pair(worldId, Triple(x, y, z))
                    }
            }
        }

    fun deleteReturnLocation(playerId: UUID) = executeWithRetry("Delete return location") {
        transaction(database) {
            ReturnLocationTable.deleteWhere { ReturnLocationTable.playerId eq playerId.toString() }
        }
    }

    fun cleanupOldReturnLocations() = executeWithRetry("Cleanup old return locations") {
        transaction(database) {
            val dayAgo = System.currentTimeMillis() - 86400000L
            ReturnLocationTable.deleteWhere { timestamp less dayAgo }
        }
    }

    fun savePlayerData(data: PlayerRankData) = executeWithRetry("Save player data") {
        transaction(database) {
            val existing = PlayerRankTable.select {
                (PlayerRankTable.playerId eq data.playerId.toString()) and
                        (PlayerRankTable.seasonId eq data.seasonId) and
                        (PlayerRankTable.format eq data.format)
            }.firstOrNull()

            val ranksStr = data.claimedRanks
                .filter { it.split(":").getOrNull(1) == data.format }
                .joinToString(",")
                .ifEmpty { "" }

            if (existing != null) {
                PlayerRankTable.update({
                    (PlayerRankTable.playerId eq data.playerId.toString()) and
                            (PlayerRankTable.seasonId eq data.seasonId) and
                            (PlayerRankTable.format eq data.format)
                }) { row ->
                    row[playerName] = data.playerName
                    row[elo] = data.elo
                    row[wins] = data.wins
                    row[losses] = data.losses
                    row[winStreak] = data.winStreak
                    row[bestWinStreak] = data.bestWinStreak
                    row[claimedRanks] = ranksStr
                    row[fleeCount] = data.fleeCount
                }
            } else {
                PlayerRankTable.insert { row ->
                    row[playerId] = data.playerId.toString()
                    row[playerName] = data.playerName
                    row[seasonId] = data.seasonId
                    row[format] = data.format
                    row[elo] = data.elo
                    row[wins] = data.wins
                    row[losses] = data.losses
                    row[winStreak] = data.winStreak
                    row[bestWinStreak] = data.bestWinStreak
                    row[claimedRanks] = ranksStr
                    row[fleeCount] = data.fleeCount
                }
            }
        }
    }

    fun getPlayerData(playerId: UUID, seasonId: Int, format: String? = null): PlayerRankData? =
        executeWithRetry("Get player data") {
            transaction(database) {
                val query = PlayerRankTable.select {
                    (PlayerRankTable.playerId eq playerId.toString()) and
                            (PlayerRankTable.seasonId eq seasonId)
                }

                if (format != null) {
                    query.andWhere { PlayerRankTable.format eq format }
                }

                query.firstOrNull()?.let {
                    PlayerRankData(
                        playerId = UUID.fromString(it[PlayerRankTable.playerId]),
                        playerName = it[PlayerRankTable.playerName],
                        seasonId = it[PlayerRankTable.seasonId],
                        format = it[PlayerRankTable.format],
                        elo = it[PlayerRankTable.elo],
                        wins = it[PlayerRankTable.wins],
                        losses = it[PlayerRankTable.losses],
                        winStreak = it[PlayerRankTable.winStreak],
                        bestWinStreak = it[PlayerRankTable.bestWinStreak],
                        claimedRanks = if (it[PlayerRankTable.claimedRanks].isNotBlank()) {
                            it[PlayerRankTable.claimedRanks].split(",").toMutableSet()
                        } else {
                            mutableSetOf()
                        },
                        fleeCount = it[PlayerRankTable.fleeCount]
                    )
                }
            }
        }

    fun getLeaderboard(seasonId: Int, format: String, offset: Long, limit: Int): List<PlayerRankData> =
        executeWithRetry("Get leaderboard") {
            transaction(database) {
                PlayerRankTable.select {
                    (PlayerRankTable.seasonId eq seasonId) and
                            (PlayerRankTable.format eq format)
                }.orderBy(PlayerRankTable.elo to SortOrder.DESC)
                    .limit(limit, offset)
                    .map(::rowToPlayerRankData)
            }
        }

    fun getPlayerCount(seasonId: Int, format: String): Int =
        executeWithRetry("Get player count") {
            transaction(database) {
                PlayerRankTable.select {
                    (PlayerRankTable.seasonId eq seasonId) and
                            (PlayerRankTable.format eq format)
                }.count().toInt()
            }
        }

    fun getPlayerRank(playerId: UUID, seasonId: Int, format: String): Int =
        executeWithRetry("Get player rank") {
            transaction(database) {
                val playerElo = PlayerRankTable.select {
                    (PlayerRankTable.playerId eq playerId.toString()) and
                            (PlayerRankTable.seasonId eq seasonId) and
                            (PlayerRankTable.format eq format)
                }.firstOrNull()?.get(PlayerRankTable.elo) ?: return@transaction -1

                PlayerRankTable.select {
                    (PlayerRankTable.seasonId eq seasonId) and
                            (PlayerRankTable.format eq format) and
                            (PlayerRankTable.elo greaterEq playerElo)
                }.count().toInt()
            }
        }

    fun saveSeasonInfo(seasonId: Int, startDate: String, endDate: String, ended: Boolean = false, name: String = "") = executeWithRetry("Save season info") {
        transaction(database) {
            val existing = SeasonInfoTable.select { SeasonInfoTable.seasonId eq seasonId }.firstOrNull()

            if (existing != null) {
                SeasonInfoTable.update({ SeasonInfoTable.seasonId eq seasonId }) {
                    it[SeasonInfoTable.startDate] = startDate
                    it[SeasonInfoTable.endDate] = endDate
                    it[SeasonInfoTable.ended] = ended
                    it[SeasonInfoTable.seasonName] = name
                }
            } else {
                SeasonInfoTable.insert {
                    it[SeasonInfoTable.seasonId] = seasonId
                    it[SeasonInfoTable.startDate] = startDate
                    it[SeasonInfoTable.endDate] = endDate
                    it[SeasonInfoTable.ended] = ended
                    it[SeasonInfoTable.seasonName] = name
                }
            }
        }
    }

    fun getLastSeasonInfo(): SeasonInfo? = executeWithRetry("Get last season info") {
        transaction(database) {
            SeasonInfoTable
                .selectAll()
                .orderBy(SeasonInfoTable.seasonId to SortOrder.DESC)
                .firstOrNull()
                ?.let {
                    SeasonInfo(
                        seasonId = it[SeasonInfoTable.seasonId],
                        startDate = it[SeasonInfoTable.startDate],
                        endDate = it[SeasonInfoTable.endDate],
                        ended = it[SeasonInfoTable.ended],
                        seasonName = it[SeasonInfoTable.seasonName]
                    )
                }
        }
    }

    fun markSeasonEnded(seasonId: Int) = executeWithRetry("Mark season ended") {
        transaction(database) {
            SeasonInfoTable.update({ SeasonInfoTable.seasonId eq seasonId }) {
                it[SeasonInfoTable.ended] = true
            }
        }
    }

    data class SeasonInfo(
        val seasonId: Int,
        val startDate: String,
        val endDate: String,
        val ended: Boolean,
        val seasonName: String
    )

    fun getSeasonInfo(seasonId: Int): SeasonInfo? = executeWithRetry("Get season info") {
        transaction(database) {
            SeasonInfoTable
                .select { SeasonInfoTable.seasonId eq seasonId }
                .firstOrNull()
                ?.let {
                    SeasonInfo(
                        seasonId = it[SeasonInfoTable.seasonId],
                        startDate = it[SeasonInfoTable.startDate],
                        endDate = it[SeasonInfoTable.endDate],
                        ended = it[SeasonInfoTable.ended],
                        seasonName = it[SeasonInfoTable.seasonName]
                    )
                }
        }
    }

    fun close() {
        logger.info("RankDao closed")
    }

    object PlayerRankTable : Table("player_rank_data") {
        val id = long("id").autoIncrement()
        val playerId = varchar("player_id", 36)
        val playerName = varchar("player_name", 50).default("未知玩家")
        val seasonId = integer("season_id")
        val format = varchar("format", 50)
        val elo = integer("elo")
        val wins = integer("wins")
        val losses = integer("losses")
        val winStreak = integer("win_streak")
        val bestWinStreak = integer("best_win_streak")
        val claimedRanks = text("claimed_ranks")
        val fleeCount = integer("flee_count").default(0)

        override val primaryKey = PrimaryKey(id)
    }

    object SeasonInfoTable : LongIdTable("season_info") {
        val seasonId = integer("season_id").uniqueIndex("season_info_season_id")
        val startDate = varchar("start_date", 30)
        val endDate = varchar("end_date", 30)
        val ended = bool("ended").default(false)
        val seasonName = varchar("season_name", 50).default("")
    }

    object PokemonUsageTable : Table("pokemon_usage") {
        val id = long("id").autoIncrement()
        val seasonId = integer("season_id")
        val pokemonSpecies = varchar("pokemon_species", 50)
        val count = integer("count")

        override val primaryKey = PrimaryKey(id)
    }

    fun getAllPlayerData(seasonId: Int): List<PlayerRankData> = executeWithRetry("Get all player data") {
        transaction(database) {
            PlayerRankTable.select { PlayerRankTable.seasonId eq seasonId }
                .map(::rowToPlayerRankData)
        }
    }

    fun getParticipationCount(seasonId: Int, format: String): Long = executeWithRetry("Get participation count by format") {
        transaction(database) {
            PlayerRankTable
                .slice(PlayerRankTable.playerId)
                .select {
                    (PlayerRankTable.seasonId eq seasonId) and
                            (PlayerRankTable.format eq format)
                }
                .withDistinct()
                .count()
        }
    }

    private fun rowToPlayerRankData(row: ResultRow): PlayerRankData {
        return PlayerRankData(
            playerId = UUID.fromString(row[PlayerRankTable.playerId]),
            playerName = row[PlayerRankTable.playerName],
            seasonId = row[PlayerRankTable.seasonId],
            format = row[PlayerRankTable.format],
            elo = row[PlayerRankTable.elo],
            wins = row[PlayerRankTable.wins],
            losses = row[PlayerRankTable.losses],
            winStreak = row[PlayerRankTable.winStreak],
            bestWinStreak = row[PlayerRankTable.bestWinStreak],
            claimedRanks = if (row[PlayerRankTable.claimedRanks].isNotBlank()) {
                row[PlayerRankTable.claimedRanks].split(",").toMutableSet()
            } else {
                mutableSetOf()
            },
            fleeCount = row.getOrNull(PlayerRankTable.fleeCount) ?: 0
        )
    }

    fun deletePlayerData(playerId: UUID, seasonId: Int, format: String): Boolean = executeWithRetry("Delete player data") {
        transaction(database) {
            val rowsDeleted = PlayerRankTable.deleteWhere {
                (PlayerRankTable.playerId eq playerId.toString()) and
                        (PlayerRankTable.seasonId eq seasonId) and
                        (PlayerRankTable.format eq format)
            }
            rowsDeleted > 0
        }
    }

    fun incrementPokemonUsage(seasonId: Int, pokemonSpecies: String) = executeWithRetry("Increment pokemon usage") {
        transaction(database) {
            val existing = PokemonUsageTable.select {
                (PokemonUsageTable.seasonId eq seasonId) and
                        (PokemonUsageTable.pokemonSpecies eq pokemonSpecies.lowercase())
            }.firstOrNull()

            if (existing != null) {
                PokemonUsageTable.update({
                    (PokemonUsageTable.seasonId eq seasonId) and
                            (PokemonUsageTable.pokemonSpecies eq pokemonSpecies.lowercase())
                }) { row ->
                    row[count] = existing[PokemonUsageTable.count] + 1
                }
            } else {
                PokemonUsageTable.insert { row ->
                    row[PokemonUsageTable.seasonId] = seasonId
                    row[PokemonUsageTable.pokemonSpecies] = pokemonSpecies.lowercase()
                    row[count] = 1
                }
            }
        }
    }

    fun getPokemonUsage(seasonId: Int, limit: Int, offset: Int): List<Pair<String, Int>> = executeWithRetry("Get pokemon usage") {
        transaction(database) {
            PokemonUsageTable
                .select { PokemonUsageTable.seasonId eq seasonId }
                .orderBy(PokemonUsageTable.count to SortOrder.DESC)
                .limit(limit, offset.toLong())
                .map {
                    it[PokemonUsageTable.pokemonSpecies] to it[PokemonUsageTable.count]
                }
        }
    }

    fun getTotalPokemonUsage(seasonId: Int): Int = executeWithRetry("Get total pokemon usage") {
        transaction(database) {
            PokemonUsageTable
                .select { PokemonUsageTable.seasonId eq seasonId }
                .count().toInt()
        }
    }

    fun getTotalPokemonUsageCount(seasonId: Int): Int = executeWithRetry("Get total pokemon usage count") {
        transaction(database) {
            PokemonUsageTable
                .select { PokemonUsageTable.seasonId eq seasonId }
                .sumOf { it[PokemonUsageTable.count] }
        }
    }

    fun getUsageStatistics(seasonId: Int): Map<String, Int> = executeWithRetry("Get usage statistics") {
        transaction(database) {
            PokemonUsageTable
                .select { PokemonUsageTable.seasonId eq seasonId }
                .associate {
                    it[PokemonUsageTable.pokemonSpecies].lowercase() to it[PokemonUsageTable.count]
                }
        }
    }
}