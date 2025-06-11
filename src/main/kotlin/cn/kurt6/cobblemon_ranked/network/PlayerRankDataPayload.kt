// PlayerRankDataPayload.kt
package cn.kurt6.cobblemon_ranked.network

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

data class PlayerRankDataPayload(
    val playerName: String,
    val format: String,
    val seasonId: Int,
    val elo: Int,
    val wins: Int,
    val losses: Int,
    val winStreak: Int,
    val bestWinStreak: Int,
    val fleeCount: Int,
    val rankTitle: String,
    val globalRank: Int? // null = 未上榜
) : CustomPayload {
    override fun getId(): CustomPayload.Id<*> = ID

    fun write(buf: PacketByteBuf) {
        buf.writeString(playerName)
        buf.writeString(format)
        buf.writeInt(seasonId)
        buf.writeInt(elo)
        buf.writeInt(wins)
        buf.writeInt(losses)
        buf.writeInt(winStreak)
        buf.writeInt(bestWinStreak)
        buf.writeInt(fleeCount)
        buf.writeString(rankTitle)
        buf.writeBoolean(globalRank != null)
        if (globalRank != null) buf.writeInt(globalRank)
    }

    companion object {
        val ID = CustomPayload.Id<PlayerRankDataPayload>(
            Identifier.of("cobblemon_ranked", "player_rank_data")
        )

        val CODEC: PacketCodec<PacketByteBuf, PlayerRankDataPayload> = PacketCodec.of(
            { payload, buf -> payload.write(buf) },
            { buf -> read(buf) }
        )

        fun read(buf: PacketByteBuf): PlayerRankDataPayload {
            val playerName = buf.readString()
            val format = buf.readString()
            val seasonId = buf.readInt()
            val elo = buf.readInt()
            val wins = buf.readInt()
            val losses = buf.readInt()
            val winStreak = buf.readInt()
            val bestWinStreak = buf.readInt()
            val fleeCount = buf.readInt()
            val rankTitle = buf.readString()
            val hasRank = buf.readBoolean()
            val globalRank = if (hasRank) buf.readInt() else null

            return PlayerRankDataPayload(
                playerName, format, seasonId, elo, wins, losses,
                winStreak, bestWinStreak, fleeCount, rankTitle, globalRank
            )
        }
    }
}