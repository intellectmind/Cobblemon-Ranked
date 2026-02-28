package cn.kurt6.cobblemon_ranked.network

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

data class ClientVersionPayload(
    val version: String
) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<ClientVersionPayload>(Identifier.of("cobblemon_ranked", "client_version"))
        const val MINIMUM_VERSION = "1.4.1"

        val CODEC: PacketCodec<PacketByteBuf, ClientVersionPayload> = PacketCodec.of(
            { payload, buf ->
                buf.writeString(payload.version)
            },
            { buf ->
                ClientVersionPayload(buf.readString())
            }
        )

        fun isVersionCompatible(version: String): Boolean {
            return try {
                val clientParts = version.split(".").map { it.toIntOrNull() ?: 0 }
                val minParts = MINIMUM_VERSION.split(".").map { it.toInt() }

                for (i in 0 until minOf(clientParts.size, minParts.size)) {
                    when {
                        clientParts[i] > minParts[i] -> return true
                        clientParts[i] < minParts[i] -> return false
                    }
                }
                clientParts.size >= minParts.size
            } catch (e: Exception) {
                false
            }
        }
    }
}