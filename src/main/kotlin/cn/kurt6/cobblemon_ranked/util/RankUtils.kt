package cn.kurt6.cobblemon_ranked.util

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket
import net.minecraft.network.packet.s2c.play.TitleS2CPacket
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

object RankUtils {
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

    fun calculateElo(
        winnerElo: Int,
        loserElo: Int,
        kFactor: Int,
        minElo: Int,
        loserProtectionRate: Double = 1.0
    ): Pair<Int, Int> {
        val winnerExpected = 1.0 / (1 + Math.pow(10.0, (loserElo - winnerElo) / 400.0))
        val loserExpected = 1.0 - winnerExpected

        val winnerGainRaw = (kFactor * (1 - winnerExpected)).toInt()
        val loserLossRaw = (kFactor * (0 - loserExpected)).toInt()

        val maxLoserLoss = (winnerGainRaw * loserProtectionRate).toInt()
        val actualLoserLoss = if (loserLossRaw < 0) {
            val absLoserLoss = -loserLossRaw
            if (absLoserLoss > maxLoserLoss) -maxLoserLoss else loserLossRaw
        } else {
            loserLossRaw
        }

        val newWinnerElo = winnerElo + winnerGainRaw
        val newLoserElo = loserElo + actualLoserLoss

        return Pair(
            maxOf(minElo, newWinnerElo),
            maxOf(minElo, newLoserElo)
        )
    }

    private fun <A, B> Pair<A, B>.swap(): Pair<B, A> = Pair(second, first)

    fun resolveStandardRankName(input: String): String? {
        return CobblemonRanked.config.rankTitles.values.firstOrNull {
            it.equals(input.trim(), ignoreCase = true)
        }
    }

    fun getRewardCommands(format: String, rankInput: String): List<String>? {
        val standardRank = resolveStandardRankName(rankInput) ?: return null
        return CobblemonRanked.config.rankRewards[format]?.get(standardRank)
    }
}