// utils.kt
package cn.kurt6.cobblemon_ranked.crossserver

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import com.cobblemon.mod.common.api.moves.Move
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.util.Identifier

object Utils {
    /**
     * 获取玩家完整的宝可梦队伍，转为 JSON 列表
     */
    fun getPokemonTeam(player: ServerPlayerEntity): List<JsonObject> {
        return Cobblemon.storage.getParty(player)
            .filterNotNull()
            .map { it.toJson() }
    }

    /**
     * 将 Pokemon 对象序列化为 JsonObject，用于跨服传输
     */
    fun Pokemon.toJson(): JsonObject {
        return JsonObject().apply {
            // 1. 基础信息
            addProperty("name", species.name)
            addProperty("name_key", "cobblemon.species.${species.name.lowercase()}.name")
            addProperty("uuid", uuid.toString())
            addProperty("level", level)
            addProperty("hp", currentHealth)
            // 特性
            addProperty("ability", ability.name)
            // 个体最终数值
            addProperty("max_hp", maxHealth)
            addProperty("attack", attack)
            addProperty("defense", defence)
            addProperty("special_attack", specialAttack)
            addProperty("special_defense", specialDefence)
            addProperty("speed", speed)

            // 2. 形态信息
            form?.let {
                addProperty("form", it.name)
            }

            // 3. 技能数组
            add("moves", movesToJson())

            // 4. 持有物
            getHeldItemReflectively(this@toJson)?.let { itemStack ->
                val itemId = getItemIdLower(itemStack)
                if (itemId != null) {
                    addProperty("item", itemId)
                }
            }

            // 5. 属性
            add("types", JsonArray().apply {
                types.forEach { type ->
                    add(type.name.lowercase())
                }
            })
        }
    }

    private fun Pokemon.movesToJson(): JsonArray {
        return JsonArray().apply {
            moveSet?.getMoves()?.forEach { move ->
                add(JsonObject().apply {
                    addProperty("name", move.name)
                    addProperty("name_key", "cobblemon.move.${move.name.lowercase()}")
                    addProperty("power", move.power.toInt())
                    addProperty("type", move.type.name.lowercase())
                    addProperty("category", move.damageCategory.name.lowercase())
                    addProperty("accuracy", move.accuracy.toInt())
                    addProperty("max_pp", move.maxPp)
                    addProperty("description_key", "cobblemon.move.${move.name.lowercase()}.desc")
//                    add("effect_chances", JsonArray().apply {
//                        move.template.effectChances.forEach { add(it) }
//                    })
//                    addProperty("raised_pp_stages", move.raisedPpStages)  // PP强化阶段
                })
            }
        }
    }

    /**
     * 反射获取 Pokemon 对象的 heldItem 字段值
     */
    fun getHeldItemReflectively(pokemon: Pokemon): ItemStack? {
        return runCatching {
            val heldItemField = pokemon.javaClass.getDeclaredField("heldItem").apply {
                isAccessible = true
            }
            val heldItemObj = heldItemField.get(pokemon) ?: return null

            // 直接就是 ItemStack 的情况
            if (heldItemObj is ItemStack) {
                return heldItemObj
            }

            // 尝试解析 item 字段
            val itemField = heldItemObj.javaClass.getDeclaredField("item").apply {
                isAccessible = true
            }
            itemField.get(heldItemObj) as? ItemStack
        }.onFailure {
            CobblemonRanked.logger.error("获取持有物失败: ${it.message}")
        }.getOrNull()
    }

    /**
     * 获取物品的注册ID
     */
    fun getItemIdLower(itemStack: ItemStack): String? {
        return runCatching {
            Registries.ITEM.getId(itemStack.item).toString().lowercase()
        }.onFailure {
            CobblemonRanked.logger.error("获取物品ID失败: ${it.message}")
        }.getOrNull()
    }
}