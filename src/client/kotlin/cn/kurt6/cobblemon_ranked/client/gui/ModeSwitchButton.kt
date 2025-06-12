package cn.kurt6.cobblemon_ranked.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class ModeSwitchButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val mode: String,
    private val currentMode: String,
    private val textureNormal: Identifier,
    private val textureActive: Identifier,
    private val onClickAction: () -> Unit,
    private val alpha: Float = 0.4f
) : ClickableWidget(x, y, width, height, Text.empty()) {

    private var isHovered = false

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()

        isHovered = super.isMouseOver(mouseX.toDouble(), mouseY.toDouble())

        val textureToUse = when {
            mode == currentMode -> textureActive
            isHovered -> textureNormal // Change texture when hovered
            else -> textureNormal
        }

        val hoverAlpha = if (isHovered) 0.6f else alpha // Increase alpha when hovered
        RenderSystem.setShaderColor(1f, 1f, 1f, hoverAlpha)

        context.drawTexture(textureToUse, x, y, 0f, 0f, width, height, width, height)

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        RenderSystem.disableBlend()
    }

    override fun onClick(mouseX: Double, mouseY: Double) {
        onClickAction()
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {}
}