package cn.kurt6.cobblemon_ranked.network

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.UUID

data class SelectionPokemonInfo(
    val uuid: UUID,
    val species: String,
    val displayName: String,
    val level: Int,
    val gender: String,
    val shiny: Boolean,
    val form: String
) {
    companion object {
        fun write(buf: PacketByteBuf, info: SelectionPokemonInfo) {
            buf.writeUuid(info.uuid)
            buf.writeString(info.species)
            buf.writeString(info.displayName)
            buf.writeInt(info.level)
            buf.writeString(info.gender)
            buf.writeBoolean(info.shiny)
            buf.writeString(info.form)
        }

        fun read(buf: PacketByteBuf): SelectionPokemonInfo {
            return SelectionPokemonInfo(
                buf.readUuid(),
                buf.readString(),
                buf.readString(),
                buf.readInt(),
                buf.readString(),
                buf.readBoolean(),
                buf.readString()
            )
        }
    }
}

data class TeamSelectionStartPayload(
    val limit: Int,
    val timeLimitSeconds: Int,
    val opponentName: String,
    val opponentTeam: List<SelectionPokemonInfo>,
    val yourTeam: List<SelectionPokemonInfo>
) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<TeamSelectionStartPayload>(Identifier.of("cobblemon_ranked", "team_selection_start"))

        val CODEC: PacketCodec<PacketByteBuf, TeamSelectionStartPayload> = PacketCodec.of(
            { payload, buf ->
                buf.writeVarInt(payload.limit)
                buf.writeVarInt(payload.timeLimitSeconds)
                buf.writeString(payload.opponentName)

                buf.writeVarInt(payload.opponentTeam.size)
                payload.opponentTeam.forEach { SelectionPokemonInfo.write(buf, it) }

                buf.writeVarInt(payload.yourTeam.size)
                payload.yourTeam.forEach { SelectionPokemonInfo.write(buf, it) }
            },
            { buf ->
                val limit = buf.readVarInt()
                val time = buf.readVarInt()
                val opName = buf.readString()

                val opCount = buf.readVarInt()
                val opTeam = List(opCount) { SelectionPokemonInfo.read(buf) }

                val myCount = buf.readVarInt()
                val myTeam = List(myCount) { SelectionPokemonInfo.read(buf) }

                TeamSelectionStartPayload(limit, time, opName, opTeam, myTeam)
            }
        )
    }
}

data class TeamSelectionSubmitPayload(
    val selectedUuids: List<UUID>
) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<TeamSelectionSubmitPayload>(Identifier.of("cobblemon_ranked", "team_selection_submit"))

        val CODEC: PacketCodec<PacketByteBuf, TeamSelectionSubmitPayload> = PacketCodec.of(
            { payload, buf ->
                buf.writeVarInt(payload.selectedUuids.size)
                payload.selectedUuids.forEach { buf.writeUuid(it) }
            },
            { buf ->
                val count = buf.readVarInt()
                val uuids = List(count) { buf.readUuid() }
                TeamSelectionSubmitPayload(uuids)
            }
        )
    }
}