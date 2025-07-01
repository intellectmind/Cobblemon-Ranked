package cn.kurt6.cobblemon_ranked.network

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

data class LeaderboardPayload(val text: String, val page: Int) : CustomPayload {
    override fun getId() = ID

    fun write(buf: PacketByteBuf) {
        buf.writeString(text)
        buf.writeVarInt(page)
    }

    companion object {
        val ID = CustomPayload.Id<LeaderboardPayload>(
            Identifier.of("cobblemon_ranked", "leaderboard_data")
        )

        val CODEC: PacketCodec<PacketByteBuf, LeaderboardPayload> = PacketCodec.of(
            { payload, buf -> payload.write(buf) },
            { buf -> LeaderboardPayload(buf.readString(), buf.readVarInt()) }
        )
    }
}
