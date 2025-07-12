// RankModels.kt
// 数据模型
package cn.kurt6.cobblemon_ranked.data

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import java.util.*

data class PlayerRankData(
    val playerId: UUID,
    val seasonId: Int,
    val format: String,
    var elo: Int = 1000,
    var wins: Int = 0,
    var losses: Int = 0,
    var winStreak: Int = 0,
    var bestWinStreak: Int = 0,
    var playerName: String = "未知玩家",
    val claimedRanks: MutableSet<String> = mutableSetOf(),
    var fleeCount: Int = 0
) {
    val winRate: Double
        get() = if (wins + losses == 0) 0.0 else wins.toDouble() / (wins + losses) * 100

    fun getRankTitle(): String {
        return CobblemonRanked.config.rankTitles
            .filterKeys { it <= elo }
            .maxByOrNull { it.key }
            ?.value ?: "青铜"
    }

    fun hasClaimedReward(rank: String, format: String): Boolean {
        // 确保只检查当前模式的奖励
        return claimedRanks.any {
            it.startsWith("$seasonId:$format:") && it.endsWith(":$rank")
        }
    }

    fun markRewardClaimed(rank: String, format: String) {
        // 格式: seasonId:format:rank
        claimedRanks.add("$seasonId:$format:$rank")
    }
}

data class SeasonRemainingTime(
    val days: Long,
    val hours: Long,
    val minutes: Long
) {
    override fun toString() = "${days}d ${hours}h ${minutes}m"
}