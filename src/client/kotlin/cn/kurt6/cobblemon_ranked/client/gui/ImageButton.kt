package cn.kurt6.cobblemon_ranked.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class ImageButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val texture: Identifier,
    private val hoverOverlay: Identifier? = Identifier.of("cobblemon_ranked", "textures/gui/hover_overlay.png"),
    private val onClick: () -> Unit
) : ClickableWidget(x, y, width, height, Text.empty()) {

    private var hoverProgress = 0f
    private val animationSpeed = 0.2f

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val target = if (isHovered) 1f else 0f
        hoverProgress += (target - hoverProgress) * animationSpeed

        // 渲染按钮背景图片
        context.drawTexture(texture, x, y, 0f, 0f, width, height, width, height)

        // 渲染悬浮图层
        if (hoverProgress > 0.01f && hoverOverlay != null) {
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()

            val inset = 1
            context.drawTexture(
                hoverOverlay,
                x + inset, y + inset,
                0f, 0f,
                width - inset * 2, height - inset * 2,
                width - inset * 2, height - inset * 2
            )

            RenderSystem.disableBlend()
        }
    }

    override fun onClick(mouseX: Double, mouseY: Double) {
        MinecraftClient.getInstance().soundManager.play(
            net.minecraft.client.sound.PositionedSoundInstance.master(
                net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f
            )
        )
        onClick()
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        appendDefaultNarrations(builder)
    }
}