package cn.kurt6.cobblemon_ranked.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class Panel(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val texture: Identifier,
) : ClickableWidget(x, y, width, height, Text.empty()) {

    var panelAlpha: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (panelAlpha <= 0f) return

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        context.setShaderColor(1f, 1f, 1f, panelAlpha)

        context.drawTexture(texture, x, y, 0f, 0f, width, height, width, height)

        context.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {}
}