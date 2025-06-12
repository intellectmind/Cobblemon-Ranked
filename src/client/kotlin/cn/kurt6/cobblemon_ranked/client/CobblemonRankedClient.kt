package cn.kurt6.cobblemon_ranked.client

import cn.kurt6.cobblemon_ranked.client.gui.RankedMainMenuScreen
import cn.kurt6.cobblemon_ranked.client.network.registerClientReceivers
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import org.lwjgl.glfw.GLFW

class CobblemonRankedClient : ClientModInitializer {
    private lateinit var openGuiKey: KeyBinding

    override fun onInitializeClient() {
        // 注册所有网络接收器
        registerClientReceivers()

        // 注册打开界面的快捷键
        openGuiKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.cobblemon_ranked.open_gui",
                GLFW.GLFW_KEY_X,
                "category.cobblemon_ranked"
            )
        )

        // 添加快捷键监听
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (openGuiKey.wasPressed()) {
                client.setScreen(RankedMainMenuScreen())
            }
        }
    }
}