package cn.kurt6.cobblemon_ranked.network

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

data class SeasonInfoTextPayload(val text: String) : CustomPayload {
    override fun getId(): CustomPayload.Id<*> = ID

    fun write(buf: PacketByteBuf) {
        buf.writeString(text)
    }

    companion object {
        val ID = CustomPayload.Id<SeasonInfoTextPayload>(
            Identifier.of("cobblemon_ranked", "season_info_text")
        )

        val CODEC: PacketCodec<PacketByteBuf, SeasonInfoTextPayload> = PacketCodec.of(
            { payload: SeasonInfoTextPayload, buf: PacketByteBuf -> payload.write(buf) },
            { buf: PacketByteBuf -> read(buf) }
        )

        fun read(buf: PacketByteBuf): SeasonInfoTextPayload {
            return SeasonInfoTextPayload(buf.readString())
        }
    }
}