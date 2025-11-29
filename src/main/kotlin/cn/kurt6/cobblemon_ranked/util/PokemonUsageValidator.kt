package cn.kurt6.cobblemon_ranked.util

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.server.network.ServerPlayerEntity

object PokemonUsageValidator {

    fun validateUsageRestrictions(
        player: ServerPlayerEntity,
        pokemon: Pokemon,
        seasonId: Int,
        lang: String
    ): ValidationResult {
        val config = CobblemonRanked.config

        if (config.onlyBaseFormWithEvolution) {
            val result = validateBaseFormWithEvolution(pokemon, lang)
            if (!result.isValid) return result
        }

        if (config.banUsageBelow > 0.0 || config.banUsageAbove > 0.0 || config.banTopUsed > 0) {
            val result = validateUsageRate(pokemon, seasonId, lang)
            if (!result.isValid) return result
        }

        return ValidationResult(true)
    }

    private fun validateBaseFormWithEvolution(pokemon: Pokemon, lang: String): ValidationResult {
        val species = pokemon.species

        if (!isBaseForm(species)) {
            return ValidationResult(
                false,
                MessageConfig.get("battle.team.not_base_form", lang, "name" to species.name)
            )
        }

        if (!canEvolve(species)) {
            return ValidationResult(
                false,
                MessageConfig.get("battle.team.cannot_evolve", lang, "name" to species.name)
            )
        }

        return ValidationResult(true)
    }

    private fun validateUsageRate(pokemon: Pokemon, seasonId: Int, lang: String): ValidationResult {
        val config = CobblemonRanked.config
        val dao = CobblemonRanked.rankDao
        val speciesName = pokemon.species.name.lowercase()

        val usageStats = dao.getUsageStatistics(seasonId)
        val totalUsage = usageStats.values.sum()

        if (totalUsage == 0) {
            return ValidationResult(true)
        }

        val pokemonUsage = usageStats[speciesName] ?: 0
        val usageRate = pokemonUsage.toDouble() / totalUsage

        if (config.banUsageBelow > 0.0 && usageRate < config.banUsageBelow) {
            val threshold = String.format("%.1f%%", config.banUsageBelow * 100)
            val current = String.format("%.2f%%", usageRate * 100)
            return ValidationResult(
                false,
                MessageConfig.get("battle.team.usage_too_low", lang,
                    "name" to pokemon.species.name,
                    "rate" to current,
                    "threshold" to threshold
                )
            )
        }

        if (config.banUsageAbove > 0.0 && usageRate > config.banUsageAbove) {
            val threshold = String.format("%.1f%%", config.banUsageAbove * 100)
            val current = String.format("%.2f%%", usageRate * 100)
            return ValidationResult(
                false,
                MessageConfig.get("battle.team.usage_too_high", lang,
                    "name" to pokemon.species.name,
                    "rate" to current,
                    "threshold" to threshold
                )
            )
        }

        if (config.banTopUsed > 0) {
            val topPokemon = usageStats.entries
                .sortedByDescending { it.value }
                .take(config.banTopUsed)
                .map { it.key }

            if (speciesName in topPokemon) {
                val rank = topPokemon.indexOf(speciesName) + 1
                return ValidationResult(
                    false,
                    MessageConfig.get("battle.team.in_top_used", lang,
                        "name" to pokemon.species.name,
                        "rank" to rank.toString(),
                        "limit" to config.banTopUsed.toString()
                    )
                )
            }
        }

        return ValidationResult(true)
    }

    private fun isBaseForm(species: Species): Boolean {
        return species.preEvolution == null
    }

    private fun canEvolve(species: Species): Boolean {
        val evolutions = species.evolutions
        return evolutions.isNotEmpty()
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
}