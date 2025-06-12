package cn.kurt6.cobblemon_ranked.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import net.minecraft.util.Identifier

abstract class StandardImageButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val texture: Identifier,
    private val hoverOverlay: Identifier? = Identifier.of("cobblemon_ranked", "textures/gui/hover_overlay.png") // 默认蒙版
) : ClickableWidget(x, y, width, height, Text.empty()) {

    private var hoverProgress = 0f
    private val animationSpeed = 0.2f

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val target = if (isHovered) 1f else 0f
        hoverProgress += (target - hoverProgress) * animationSpeed

        // 渲染背景
        context.drawTexture(texture, x, y, 0f, 0f, width, height, width, height)

        // 渲染自定义 hover overlay
        if (hoverProgress > 0.01f && hoverOverlay != null) {
            val inset = 1
            val overlayX = x + inset
            val overlayY = y + inset
            val overlayW = width - inset * 2
            val overlayH = height - inset * 2

            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()

            context.drawTexture(
                hoverOverlay,
                overlayX, overlayY,
                0f, 0f,
                overlayW, overlayH,
                overlayW, overlayH
            )

            RenderSystem.disableBlend()
        }
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        appendDefaultNarrations(builder)
    }

    override fun onClick(mouseX: Double, mouseY: Double) {
        MinecraftClient.getInstance().soundManager.play(
            net.minecraft.client.sound.PositionedSoundInstance.master(
                net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f
            )
        )

        onClicked()
    }

    abstract fun onClicked()
}