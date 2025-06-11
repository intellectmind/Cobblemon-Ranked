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
            addLogger(StdOutSqlLogger) // 调试时开启
            SchemaUtils.createMissingTablesAndColumns(PlayerRankTable, SeasonInfoTable)
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
                    val ranksStr = data.claimedRanks.takeIf { it.isNotEmpty() }?.joinToString(",") ?: ""
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
                    val ranksStr = data.claimedRanks.takeIf { it.isNotEmpty() }?.joinToString(",") ?: ""
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
    fun saveSeasonInfo(seasonId: Int, startDate: String, endDate: String, ended: Boolean = false) {
        transaction(database) {
            // 更新或插入赛季信息
            val existing = SeasonInfoTable.select { SeasonInfoTable.seasonId eq seasonId }.firstOrNull()

            if (existing != null) {
                SeasonInfoTable.update({ SeasonInfoTable.seasonId eq seasonId }) {
                    it[SeasonInfoTable.startDate] = startDate
                    it[SeasonInfoTable.endDate] = endDate
                    it[SeasonInfoTable.ended] = ended
                }
            } else {
                SeasonInfoTable.insert {
                    it[SeasonInfoTable.seasonId] = seasonId
                    it[SeasonInfoTable.startDate] = startDate
                    it[SeasonInfoTable.endDate] = endDate
                    it[SeasonInfoTable.ended] = ended
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
                        ended = it[SeasonInfoTable.ended]
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
        val ended: Boolean
    )

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
            claimedRanks = if (row[PlayerRankTable.claimedRanks].isNotBlank())
                row[PlayerRankTable.claimedRanks].split(",").toMutableSet()
            else
                mutableSetOf(),
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
}