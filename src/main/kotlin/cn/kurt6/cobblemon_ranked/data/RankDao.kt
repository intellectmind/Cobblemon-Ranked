// RacnkDao.kt
// 数据库访问
package cn.kurt6.cobblemon_ranked.data

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.*

class RankDao(dbFile: File) {
    private val database: Database

    init {
        val url = "jdbc:sqlite:${dbFile.absolutePath}"
        database = Database.connect(url, "org.sqlite.JDBC")

        transaction(database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.createMissingTablesAndColumns(
                PlayerRankTable,
                SeasonInfoTable,
                PokemonUsageTable,
                PokemonOriginalDataTable
            )
        }
    }

    fun savePlayerData(data: PlayerRankData) {
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
                        .filter { it.split(":").getOrNull(1) == data.format } // 关键过滤
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
                        .filter { it.split(":").getOrNull(1) == data.format } // 关键过滤
                        .joinToString(",")
                    row[claimedRanks] = ranksStr
                    row[fleeCount] = data.fleeCount
                }
            }
        }
    }

    fun getPlayerData(playerId: UUID, seasonId: Int, format: String? = null): PlayerRankData? {
        return transaction(database) {
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

    fun getLeaderboard(seasonId: Int, format: String, limit: Int = 10): List<PlayerRankData> {
        return transaction(database) {
            PlayerRankTable.select {
                (PlayerRankTable.seasonId eq seasonId) and
                        (PlayerRankTable.format eq format)
            }.orderBy(PlayerRankTable.elo to org.jetbrains.exposed.sql.SortOrder.DESC)
                .limit(limit)
                .map {
                    rowToPlayerRankData(it)
                }
        }
    }

    // 赛季信息操作
    fun saveSeasonInfo(seasonId: Int, startDate: String, endDate: String, ended: Boolean = false, name: String = "") {
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

    fun getLastSeasonInfo(): SeasonInfo? {
        return transaction(database) {
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

    fun markSeasonEnded(seasonId: Int) {
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

    fun getSeasonInfo(seasonId: Int): SeasonInfo? {
        return transaction(database) {
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
        val claimedRanks = text("claimed_ranks").default("[]") // 已领取段位列表
        val fleeCount = integer("flee_count").default(0)

        override val primaryKey = PrimaryKey(id)
    }

    object SeasonInfoTable : LongIdTable("season_info") {
        val seasonId = integer("season_id").uniqueIndex()
        val startDate = varchar("start_date", 30)
        val endDate = varchar("end_date", 30)
        val ended = bool("ended").default(false) // 标记赛季是否已结束
        val seasonName = varchar("season_name", 50).default("")
    }

    // 获取所有玩家数据
    fun getAllPlayerData(seasonId: Int): List<PlayerRankData> {
        return transaction(database) {
            PlayerRankTable.select { PlayerRankTable.seasonId eq seasonId }
                .map(::rowToPlayerRankData)
        }
    }

    // 参与人数统计
    fun getParticipationCount(seasonId: Int): Long {
        return transaction(database) {
            PlayerRankTable
                .slice(PlayerRankTable.playerId)
                .select { PlayerRankTable.seasonId eq seasonId }
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
                // 只加载当前格式的奖励记录
                row[PlayerRankTable.claimedRanks]
                    .split(",")
                    .filter { it.split(":").getOrNull(1) == row[PlayerRankTable.format] } // 过滤
                    .toMutableSet()
            } else {
                mutableSetOf()
            },
            fleeCount = row.getOrNull(PlayerRankTable.fleeCount) ?: 0
        )
    }

    fun deletePlayerData(playerId: UUID, seasonId: Int, format: String): Boolean {
        return transaction(database) {
            val rowsDeleted = PlayerRankTable.deleteWhere {
                (PlayerRankTable.playerId eq playerId.toString()) and
                        (PlayerRankTable.seasonId eq seasonId) and
                        (PlayerRankTable.format eq format)
            }
            rowsDeleted > 0
        }
    }

    // 宝可梦使用统计
    object PokemonUsageTable : Table("pokemon_usage") {
        val id = long("id").autoIncrement()
        val seasonId = integer("season_id")
        val pokemonSpecies = varchar("pokemon_species", 50)
        val count = integer("count")

        override val primaryKey = PrimaryKey(id)
    }

    // 统计方法
    fun incrementPokemonUsage(seasonId: Int, pokemonSpecies: String) {
        transaction(database) {
            // 查找现有记录
            val existing = PokemonUsageTable.select {
                (PokemonUsageTable.seasonId eq seasonId) and
                        (PokemonUsageTable.pokemonSpecies eq pokemonSpecies.lowercase())
            }.firstOrNull()

            if (existing != null) {
                // 更新计数
                PokemonUsageTable.update({
                    (PokemonUsageTable.seasonId eq seasonId) and
                            (PokemonUsageTable.pokemonSpecies eq pokemonSpecies.lowercase())
                }) { row ->
                    row[count] = existing[PokemonUsageTable.count] + 1
                }
            } else {
                // 插入新记录
                PokemonUsageTable.insert { row ->
                    row[PokemonUsageTable.seasonId] = seasonId
                    row[PokemonUsageTable.pokemonSpecies] = pokemonSpecies.lowercase()
                    row[count] = 1
                }
            }
        }
    }

    // 查询方法
    fun getPokemonUsage(seasonId: Int, limit: Int, offset: Int): List<Pair<String, Int>> {
        return transaction(database) {
            PokemonUsageTable
                .select { PokemonUsageTable.seasonId eq seasonId }
                .orderBy(PokemonUsageTable.count to SortOrder.DESC)
                .limit(limit, offset.toLong()) // 将 offset 转换为 Long
                .map {
                    it[PokemonUsageTable.pokemonSpecies] to it[PokemonUsageTable.count]
                }
        }
    }

    // 总数查询
    fun getTotalPokemonUsage(seasonId: Int): Int {
        return transaction(database) {
            PokemonUsageTable
                .select { PokemonUsageTable.seasonId eq seasonId }
                .count().toInt() // 添加 .toInt() 转换
        }
    }

    // 获取所有宝可梦使用次数的总和
    fun getTotalPokemonUsageCount(seasonId: Int): Int {
        return transaction(database) {
            PokemonUsageTable
                .select { PokemonUsageTable.seasonId eq seasonId }
                .sumOf { it[PokemonUsageTable.count] }
        }
    }

    // 添加宝可梦原始数据表
    object PokemonOriginalDataTable : Table("pokemon_original_data") {
        val playerId = varchar("player_id", 36)
        val pokemonUuid = varchar("pokemon_uuid", 36)
        val originalLevel = integer("original_level")
        val originalExp = integer("original_exp")

        override val primaryKey = PrimaryKey(playerId, pokemonUuid)
    }

    // 添加保存原始数据的方法
    fun savePokemonOriginalData(playerId: UUID, pokemonUuid: UUID, originalLevel: Int, originalExp: Int) {
        transaction(database) {
            PokemonOriginalDataTable.insert { row ->
                row[PokemonOriginalDataTable.playerId] = playerId.toString()
                row[PokemonOriginalDataTable.pokemonUuid] = pokemonUuid.toString()
                row[PokemonOriginalDataTable.originalLevel] = originalLevel
                row[PokemonOriginalDataTable.originalExp] = originalExp
            }
        }
    }

    // 添加获取原始数据的方法
    fun getPokemonOriginalData(playerId: UUID): Map<UUID, Pair<Int, Int>> {
        return transaction(database) {
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

    // 添加删除原始数据的方法
    fun deletePokemonOriginalData(playerId: UUID) {
        transaction(database) {
            PokemonOriginalDataTable.deleteWhere {
                PokemonOriginalDataTable.playerId eq playerId.toString()
            }
        }
    }
}