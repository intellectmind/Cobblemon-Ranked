package cn.kurt6.cobblemon_ranked.util

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.server.network.ServerPlayerEntity

object PokemonUsageValidator {

    /**
     * 验证宝可梦是否符合使用率限制
     */
    fun validateUsageRestrictions(
        player: ServerPlayerEntity,
        pokemon: Pokemon,
        seasonId: Int,
        lang: String
    ): ValidationResult {
        val config = CobblemonRanked.config
        val dao = CobblemonRanked.rankDao

        // 1. 检查最初形态限制
        if (config.onlyBaseFormWithEvolution) {
            val result = validateBaseFormWithEvolution(pokemon, lang)
            if (!result.isValid) return result
        }

        // 2. 检查使用率限制
        if (config.banUsageBelow > 0.0 || config.banUsageAbove > 0.0 || config.banTopUsed > 0) {
            val result = validateUsageRate(pokemon, seasonId, lang)
            if (!result.isValid) return result
        }

        return ValidationResult(true)
    }

    /**
     * 验证是否为能够进化的最初形态
     */
    private fun validateBaseFormWithEvolution(pokemon: Pokemon, lang: String): ValidationResult {
        val species = pokemon.species

        // 检查是否是最初形态
        if (!isBaseForm(species)) {
            return ValidationResult(
                false,
                MessageConfig.get("battle.team.not_base_form", lang, "name" to species.name)
            )
        }

        // 检查是否能够进化
        if (!canEvolve(species)) {
            return ValidationResult(
                false,
                MessageConfig.get("battle.team.cannot_evolve", lang, "name" to species.name)
            )
        }

        return ValidationResult(true)
    }

    /**
     * 验证使用率限制
     */
    private fun validateUsageRate(pokemon: Pokemon, seasonId: Int, lang: String): ValidationResult {
        val config = CobblemonRanked.config
        val dao = CobblemonRanked.rankDao
        val speciesName = pokemon.species.name.lowercase()

        // 获取当前赛季的使用数据
        val usageStats = dao.getUsageStatistics(seasonId)
        val totalUsage = usageStats.values.sum()

        if (totalUsage == 0) {
            // 赛季初期没有数据，允许所有宝可梦
            return ValidationResult(true)
        }

        val pokemonUsage = usageStats[speciesName] ?: 0
        val usageRate = pokemonUsage.toDouble() / totalUsage

        // 检查使用率下限
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

        // 检查使用率上限
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

        // 检查排行限制
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

    /**
     * 判断是否为最初形态
     */
    private fun isBaseForm(species: Species): Boolean {
        // 检查是否有前置进化（preEvolution）
        return species.preEvolution == null
    }

    /**
     * 判断是否能够进化
     */
    private fun canEvolve(species: Species): Boolean {
        // 检查是否有任何进化路线
        val evolutions = species.evolutions
        return evolutions.isNotEmpty()
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
}