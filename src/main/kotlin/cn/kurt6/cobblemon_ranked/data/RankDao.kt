// RankDao.kt
package cn.kurt6.cobblemon_ranked.data

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.DriverManager
import java.sql.SQLTimeoutException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class RankDao(dbFile: File) {
    private val database: Database
    private val connectionTimeoutMs = 5000L // 连接超时5秒
    private val queryTimeoutMs = 3000L     // 查询超时3秒

    init {
        val url = "jdbc:sqlite:${dbFile.absolutePath}?busy_timeout=$connectionTimeoutMs"
        database = Database.connect(
            url = url,
            driver = "org.sqlite.JDBC",
            setupConnection = {
                it.createStatement().queryTimeout =
                    TimeUnit.MILLISECONDS.toSeconds(queryTimeoutMs).toInt()
            }
        )

        executeWithTimeout("初始化数据库表") {
            transaction(db = database) {
                SchemaUtils.createMissingTablesAndColumns(
                    PlayerRankTable,
                    SeasonInfoTable,
                    PokemonUsageTable,
                    PokemonOriginalDataTable
                )
            }
        }
    }

    // 带超时保护的数据库操作执行方法
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

    fun savePlayerData(data: PlayerRankData) = executeWithTimeout("Save player data") {
        transaction(database) {
            // 更新或插入
            val existing = PlayerRankTable.select {
                (PlayerRankTable.playerId eq data.playerId.toString()) and
                        (PlayerRankTable.seasonId eq data.seasonId) and
                        (PlayerRankTable.format eq data.format)
            }.firstOrNull()

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
                    val ranksStr = data.claimedRanks
                        .filter { it.split(":").getOrNull(1) == data.format }
                        .joinToString(",")
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
                    val ranksStr = data.claimedRanks
                        .filter { it.split(":").getOrNull(1) == data.format }
                        .joinToString(",")
                    row[claimedRanks] = ranksStr
                    row[fleeCount] = data.fleeCount
                }
            }
        }
    }

    fun getPlayerData(playerId: UUID, seasonId: Int, format: String? = null): PlayerRankData? = executeWithTimeout("Get player data") {
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
        executeWithTimeout("Get leaderboard") {
            transaction(database) {
                PlayerRankTable.select {
                    (PlayerRankTable.seasonId eq seasonId) and
                            (PlayerRankTable.format eq format)
                }.orderBy(PlayerRankTable.elo to SortOrder.DESC)
                    .limit(limit, offset)  // 添加offset参数
                    .map(::rowToPlayerRankData)
            }
        }

    // 获取总数的方法
    fun getPlayerCount(seasonId: Int, format: String): Int =
        executeWithTimeout("Get player count") {
            transaction(database) {
                PlayerRankTable.select {
                    (PlayerRankTable.seasonId eq seasonId) and
                            (PlayerRankTable.format eq format)
                }.count().toInt()
            }
        }

    fun getPlayerRank(playerId: UUID, seasonId: Int, format: String): Int =
        executeWithTimeout("Get player rank") {
            transaction(database) {
                // 获取玩家ELO
                val playerElo = PlayerRankTable.select {
                    (PlayerRankTable.playerId eq playerId.toString()) and
                            (PlayerRankTable.seasonId eq seasonId) and
                            (PlayerRankTable.format eq format)
                }.firstOrNull()?.get(PlayerRankTable.elo) ?: return@transaction -1

                // 计算排名：ELO更高或相等的玩家数量
                PlayerRankTable.select {
                    (PlayerRankTable.seasonId eq seasonId) and
                            (PlayerRankTable.format eq format) and
                            // 使用 greaterEq 包含相同分数的玩家
                            (PlayerRankTable.elo greaterEq playerElo)
                }.count().toInt()
            }
        }

    // 赛季信息操作
    fun saveSeasonInfo(seasonId: Int, startDate: String, endDate: String, ended: Boolean = false, name: String = "") = executeWithTimeout("Save season info") {
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

    fun getLastSeasonInfo(): SeasonInfo? = executeWithTimeout("Get last season info") {
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

    fun markSeasonEnded(seasonId: Int) = executeWithTimeout("Mark season ended") {
        transaction(database) {
            SeasonInfoTable.update({ SeasonInfoTable.seasonId eq seasonId }) {
                it[SeasonInfoTable.ended] = true
            }
        }
    }

    // 赛季信息数据类
    data class SeasonInfo(
        val seasonId: Int,
        val startDate: String,
        val endDate: String,
        val ended: Boolean,
        val seasonName: String
    )

    fun getSeasonInfo(seasonId: Int): SeasonInfo? = executeWithTimeout("Get season info") {
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
        // 清理资源
    }

    // Exposed ORM 表定义
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
        val claimedRanks = text("claimed_ranks").default("[]")
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

    fun getAllPlayerData(seasonId: Int): List<PlayerRankData> = executeWithTimeout("Get all player data") {
        transaction(database) {
            PlayerRankTable.select { PlayerRankTable.seasonId eq seasonId }
                .map(::rowToPlayerRankData)
        }
    }

    // 在 RankDao 类中添加以下方法
    fun getParticipationCount(seasonId: Int, format: String): Long = executeWithTimeout("Get participation count by format") {
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

    fun deletePlayerData(playerId: UUID, seasonId: Int, format: String): Boolean = executeWithTimeout("Delete player data") {
        transaction(database) {
            val rowsDeleted = PlayerRankTable.deleteWhere {
                (PlayerRankTable.playerId eq playerId.toString()) and
                        (PlayerRankTable.seasonId eq seasonId) and
                        (PlayerRankTable.format eq format)
            }
            rowsDeleted > 0
        }
    }

    object PokemonUsageTable : Table("pokemon_usage") {
        val id = long("id").autoIncrement()
        val seasonId = integer("season_id")
        val pokemonSpecies = varchar("pokemon_species", 50)
        val count = integer("count")

        override val primaryKey = PrimaryKey(id)
    }

    fun incrementPokemonUsage(seasonId: Int, pokemonSpecies: String) = executeWithTimeout("Increment pokemon usage") {
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

    fun getPokemonUsage(seasonId: Int, limit: Int, offset: Int): List<Pair<String, Int>> = executeWithTimeout("Get pokemon usage") {
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

    fun getTotalPokemonUsage(seasonId: Int): Int = executeWithTimeout("Get total pokemon usage") {
        transaction(database) {
            PokemonUsageTable
                .select { PokemonUsageTable.seasonId eq seasonId }
                .count().toInt()
        }
    }

    fun getTotalPokemonUsageCount(seasonId: Int): Int = executeWithTimeout("Get total pokemon usage count") {
        transaction(database) {
            PokemonUsageTable
                .select { PokemonUsageTable.seasonId eq seasonId }
                .sumOf { it[PokemonUsageTable.count] }
        }
    }

    object PokemonOriginalDataTable : Table("pokemon_original_data") {
        val playerId = varchar("player_id", 36)
        val pokemonUuid = varchar("pokemon_uuid", 36)
        val originalLevel = integer("original_level")
        val originalExp = integer("original_exp")

        override val primaryKey = PrimaryKey(playerId, pokemonUuid)
    }

    fun savePokemonOriginalData(playerId: UUID, pokemonUuid: UUID, originalLevel: Int, originalExp: Int) = executeWithTimeout("Save pokemon original data") {
        transaction(database) {
            PokemonOriginalDataTable.insert { row ->
                row[PokemonOriginalDataTable.playerId] = playerId.toString()
                row[PokemonOriginalDataTable.pokemonUuid] = pokemonUuid.toString()
                row[PokemonOriginalDataTable.originalLevel] = originalLevel
                row[PokemonOriginalDataTable.originalExp] = originalExp
            }
        }
    }

    fun getPokemonOriginalData(playerId: UUID): Map<UUID, Pair<Int, Int>> = executeWithTimeout("Get pokemon original data") {
        transaction(database) {
            PokemonOriginalDataTable.select {
                PokemonOriginalDataTable.playerId eq playerId.toString()
            }.associate {
                UUID.fromString(it[PokemonOriginalDataTable.pokemonUuid]) to
                        Pair(
                            it[PokemonOriginalDataTable.originalLevel],
                            it[PokemonOriginalDataTable.originalExp]
                        )
            }
        }
    }

    fun deletePokemonOriginalData(playerId: UUID) = executeWithTimeout("Delete pokemon original data") {
        transaction(database) {
            PokemonOriginalDataTable.deleteWhere {
                PokemonOriginalDataTable.playerId eq playerId.toString()
            }
        }
    }
}