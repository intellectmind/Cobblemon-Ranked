// RequestPlayerRankPayload.kt
package cn.kurt6.cobblemon_ranked.network

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

enum class RequestType {
    PLAYER,
    SEASON,
    LEADERBOARD
}

data class RequestPlayerRankPayload(
    val type: RequestType,
    val format: String = "singles",
    val extra: String? = null  // 可选参数：页码 / 其他扩展
) : CustomPayload {

    override fun getId(): CustomPayload.Id<*> = ID

    fun write(buf: PacketByteBuf) {
        buf.writeEnumConstant(type)
        buf.writeString(format)
        buf.writeBoolean(extra != null)
        if (extra != null) {
            buf.writeString(extra)
        }
    }

    companion object {
        val ID = CustomPayload.Id<RequestPlayerRankPayload>(
            Identifier.of("cobblemon_ranked", "request_player_rank")
        )

        val CODEC: PacketCodec<PacketByteBuf, RequestPlayerRankPayload> =
            PacketCodec.of<PacketByteBuf, RequestPlayerRankPayload>(
                { payload: RequestPlayerRankPayload, buf: PacketByteBuf -> payload.write(buf) },
                { buf: PacketByteBuf -> read(buf) }
            )

        fun read(buf: PacketByteBuf): RequestPlayerRankPayload {
            val type = buf.readEnumConstant(RequestType::class.java)
            val format = buf.readString()
            val hasExtra = buf.readBoolean()
            val extra = if (hasExtra) buf.readString() else null
            return RequestPlayerRankPayload(type, format, extra)
        }
    }
}