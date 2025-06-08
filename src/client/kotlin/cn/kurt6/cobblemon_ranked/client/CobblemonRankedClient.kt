package cn.kurt6.cobblemon_ranked.client

import cn.kurt6.cobblemon_ranked.client.gui.RankedMainScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import org.lwjgl.glfw.GLFW

class CobblemonRankedClient : ClientModInitializer {
    private lateinit var openGuiKey: KeyBinding

    override fun onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding("key.cobblemon_ranked.open_gui", GLFW.GLFW_KEY_Z, "category.cobblemon_ranked")
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (openGuiKey.wasPressed()) {
                client.setScreen(RankedMainScreen())
            }
        }
    }
}