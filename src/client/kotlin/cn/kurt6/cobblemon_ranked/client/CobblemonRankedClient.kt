//package cn.kurt6.cobblemon_ranked.client
//
//import cn.kurt6.cobblemon_ranked.client.config.RankConfigScreen
//import net.fabricmc.api.ClientModInitializer
//import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
//import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
//import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
//import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
//import net.minecraft.client.option.KeyBinding
//import net.minecraft.client.util.InputUtil
//import org.lwjgl.glfw.GLFW
//
//object CobblemonRankedClient : ClientModInitializer {
//    private lateinit var configKeyBinding: KeyBinding
//
//    override fun onInitializeClient() {
//        // 注册配置按键
//        configKeyBinding = KeyBindingHelper.registerKeyBinding(
//            KeyBinding(
//                "key.cobblemon_ranked.config",
//                InputUtil.Type.KEYSYM,
//                GLFW.GLFW_KEY_R,
//                "category.cobblemon_ranked"
//            )
//        )
//
//        // 按键监听
//        ClientTickEvents.END_CLIENT_TICK.register { client ->
//            while (configKeyBinding.wasPressed()) {
//                client.setScreen(RankConfigScreen(client.currentScreen))
//            }
//        }
//
//        // 注册客户端命令
//        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
//            dispatcher.register(
//                literal("rankconfig")
//                    .executes {
//                        it.source.client.setScreen(RankConfigScreen(it.source.client.currentScreen))
//                        1
//                    }
//            )
//        }
//    }
//}

package cn.kurt6.cobblemon_ranked.client

import net.fabricmc.api.ClientModInitializer

class CobblemonRankedClient : ClientModInitializer {
    override fun onInitializeClient() {
        // 客户端初始化 - 留空即可
    }
}