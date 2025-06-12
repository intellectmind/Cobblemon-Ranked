package cn.kurt6.cobblemon_ranked.client.network

import cn.kurt6.cobblemon_ranked.client.gui.ModeScreen
import cn.kurt6.cobblemon_ranked.client.gui.ModeScreen.Companion.modeName
import cn.kurt6.cobblemon_ranked.network.LeaderboardPayload
import cn.kurt6.cobblemon_ranked.network.PlayerRankDataPayload
import cn.kurt6.cobblemon_ranked.network.RequestType
import cn.kurt6.cobblemon_ranked.network.SeasonInfoTextPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text.translatable

fun registerClientReceivers() {
    // 玩家战绩
    ClientPlayNetworking.registerGlobalReceiver(PlayerRankDataPayload.ID) { payload, _ ->
        val total = payload.wins + payload.losses
        val rate = if (total == 0) 0.0 else payload.wins.toDouble() / total * 100
        val rankStr = payload.globalRank?.let { "#$it" } ?: translatable("cobblemon_ranked.not_ranked").string

        val info = buildString {
            append(translatable("cobblemon_ranked.info.title", payload.playerName, modeName(payload.format).string, payload.seasonId).string + "\n")
            append(translatable("cobblemon_ranked.info.rank", payload.rankTitle, payload.elo).string + "\n")
            append(translatable("cobblemon_ranked.info.global_rank", rankStr).string + "\n")
            append(translatable("cobblemon_ranked.info.record", payload.wins, payload.losses, rate).string + "\n")
            append(translatable("cobblemon_ranked.info.streak", payload.winStreak, payload.bestWinStreak).string + "\n")
            append(translatable("cobblemon_ranked.info.flee", payload.fleeCount).string)
        }

        updateScreenInfo(RequestType.PLAYER, info)
    }

    // 赛季信息
    ClientPlayNetworking.registerGlobalReceiver(SeasonInfoTextPayload.ID) { payload, _ ->
        updateScreenInfo(RequestType.SEASON, payload.text)
    }

    // 排行榜
    ClientPlayNetworking.registerGlobalReceiver(LeaderboardPayload.ID) { payload, _ ->
        updateScreenInfo(RequestType.LEADERBOARD, payload.text)
    }
}

private fun updateScreenInfo(type: RequestType, text: String) {
    MinecraftClient.getInstance().execute {
        val screen = MinecraftClient.getInstance().currentScreen
        if (screen is ModeScreen) {
            screen.updateInfo(type, text)
        }
    }
}
