// RankUtils.kt
// 工具类集合
package cn.kurt6.cobblemon_ranked.util

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.data.PlayerRankData
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket
import net.minecraft.network.packet.s2c.play.TitleS2CPacket
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

object RankUtils {
    // 消息工具
    fun sendMessage(player: PlayerEntity, message: String) {
        player.sendMessage(Text.literal(message), false)
    }

    fun sendTitle(
        player: PlayerEntity,
        title: String,
        subtitle: String? = null,
        fadeIn: Int = 10,
        stay: Int = 70,
        fadeOut: Int = 20
    ) {
        if (player is ServerPlayerEntity) {
            player.networkHandler.sendPacket(TitleFadeS2CPacket(fadeIn, stay, fadeOut))
            player.networkHandler.sendPacket(TitleS2CPacket(Text.literal(title)))
            subtitle?.let {
                player.networkHandler.sendPacket(SubtitleS2CPacket(Text.literal(it)))
            }
        }
    }

    /**
     * ELO计算工具
     * @param winnerElo 胜者当前ELO
     * @param loserElo 败者当前ELO
     * @param kFactor K因子（影响ELO变化幅度）
     * @param minElo 最低ELO分数
     * @return Pair<新胜者ELO, 新败者ELO>
     */
    fun calculateElo(
        winnerElo: Int,
        loserElo: Int,
        kFactor: Int,
        minElo: Int
    ): Pair<Int, Int> {
        val winnerExpected = 1.0 / (1 + Math.pow(10.0, (loserElo - winnerElo) / 400.0))
        val loserExpected = 1.0 - winnerExpected

        val newWinnerElo = winnerElo + (kFactor * (1 - winnerExpected)).toInt()
        val newLoserElo = loserElo + (kFactor * (0 - loserExpected)).toInt()

        // 确保ELO不低于最小值
        return Pair(
            maxOf(minElo, newWinnerElo),
            maxOf(minElo, newLoserElo)
        )
    }

    private fun <A, B> Pair<A, B>.swap(): Pair<B, A> = Pair(second, first)

    fun updateElo(player: ServerPlayerEntity, win: Boolean) {
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val format = "doubles"
        val dao = CobblemonRanked.rankDao

        val data = dao.getPlayerData(player.uuid, seasonId, format)
            ?: PlayerRankData(
                playerId = player.uuid,
                playerName = player.name.string, // 设置默认名
                seasonId = seasonId,
                format = format
            ).apply {
                elo = CobblemonRanked.config.initialElo
            }

        data.playerName = player.name.string // 同步最新玩家名

        val opponentElo = CobblemonRanked.config.initialElo // 对手未知，默认值
        val (newElo, _) = if (win) {
            calculateElo(data.elo, opponentElo, CobblemonRanked.config.eloKFactor, CobblemonRanked.config.minElo)
        } else {
            calculateElo(opponentElo, data.elo, CobblemonRanked.config.eloKFactor, CobblemonRanked.config.minElo).swap()
        }

        data.elo = newElo
        if (win) {
            data.wins++
            data.winStreak++
            if (data.winStreak > data.bestWinStreak) data.bestWinStreak = data.winStreak
        } else {
            data.losses++
            data.winStreak = 0
        }

        dao.savePlayerData(data)
    }

    /**
     * 将玩家输入的段位（如 "gold"）标准化为配置中使用的段位名（如 "Gold"）
     */
    fun resolveStandardRankName(input: String): String? {
        return CobblemonRanked.config.rankTitles.values.firstOrNull {
            it.equals(input.trim(), ignoreCase = true)
        }
    }

    /**
     * 根据玩家输入的段位和格式，获取配置中的奖励指令列表（null 表示未配置）
     */
    fun getRewardCommands(format: String, rankInput: String): List<String>? {
        val standardRank = resolveStandardRankName(rankInput) ?: return null
        return CobblemonRanked.config.rankRewards[format]?.get(standardRank)
    }
}