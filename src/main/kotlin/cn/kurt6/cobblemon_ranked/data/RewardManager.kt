package cn.kurt6.cobblemon_ranked.data

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.CobblemonRanked.Companion.seasonManager
import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import net.minecraft.util.Formatting

class RewardManager(private val rankDao: RankDao) {
    fun grantRankRewardIfEligible(player: PlayerEntity, rank: String, format: String, server: MinecraftServer) {
        val uuid = player.uuid
        val seasonId = seasonManager.currentSeasonId
        val lang = CobblemonRanked.config.defaultLang

        val playerData = rankDao.getPlayerData(uuid, seasonId, format) ?: return

        if (!playerData.hasClaimedReward(rank, format)) {
            val requiredWinRate = CobblemonRanked.config.rankRequirements[rank] ?: 0.0
            val totalGames = playerData.wins + playerData.losses
            val winRate = if (totalGames > 0) playerData.wins.toDouble() / totalGames else 0.0

            if (winRate < requiredWinRate) {
                val ratePercent = (requiredWinRate * 100).toInt().toString()
                val denyMsg = MessageConfig.get("reward.not_eligible", lang, "rate" to ratePercent, "rank" to rank)
                player.sendMessage(Text.literal(denyMsg))
                return
            }
            BattleHandler.grantRankReward(player, rank, format, server)

            playerData.markRewardClaimed(rank, format)
            rankDao.savePlayerData(playerData)

            val name = player.name.string
            val message = Text.literal(MessageConfig.get("reward.broadcast", lang, "player" to name, "rank" to rank))
            server.playerManager.broadcast(message, false)
        }
    }

    fun grantRankReward(player: PlayerEntity, rank: String, format: String, server: MinecraftServer) {
        val config = CobblemonRanked.config
        val lang = CobblemonRanked.config.defaultLang
        val rewards = config.rankRewards[format]?.get(rank)

        if (rewards.isNullOrEmpty()) {
            player.sendMessage(Text.literal(MessageConfig.get("reward.not_configured", lang)).formatted(Formatting.RED))
            return
        }

        rewards.forEach { command ->
            executeRewardCommand(command, player, server)
        }

        player.sendMessage(Text.literal(MessageConfig.get("reward.granted", lang, "rank" to rank)).formatted(Formatting.GREEN))
    }

    private fun executeRewardCommand(command: String, player: PlayerEntity, server: MinecraftServer) {
        val formattedCommand = command
            .replace("{player}", player.name.string)
            .replace("{uuid}", player.uuid.toString())
        server.commandManager.executeWithPrefix(server.commandSource, formattedCommand)
    }
}