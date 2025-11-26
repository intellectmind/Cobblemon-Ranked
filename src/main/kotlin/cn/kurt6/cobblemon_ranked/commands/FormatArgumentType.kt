package cn.kurt6.cobblemon_ranked.commands

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.CommandSource
import net.minecraft.text.Text
import java.util.concurrent.CompletableFuture

class FormatArgumentType : ArgumentType<String> {
    companion object {
        private val INVALID_FORMAT = DynamicCommandExceptionType { format ->
            Text.literal("Invalid battle format: $format")
        }

        @JvmStatic
        fun getFormat(ctx: CommandContext<*>, name: String): String {
            return ctx.getArgument(name, String::class.java)
        }
    }

    override fun parse(reader: StringReader): String {
        val start = reader.cursor
        val format = reader.readUnquotedString()

        val validFormats = CobblemonRanked.config.allowedFormats
        if (!validFormats.contains(format)) {
            reader.cursor = start
            throw INVALID_FORMAT.createWithContext(reader, format)
        }

        return format
    }

    override fun <S : Any?> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        return CommandSource.suggestMatching(
            CobblemonRanked.config.allowedFormats,
            builder
        )
    }
}