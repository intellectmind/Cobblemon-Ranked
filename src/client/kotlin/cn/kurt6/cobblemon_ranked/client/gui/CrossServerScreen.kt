package cn.kurt6.cobblemon_ranked.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class CrossServerScreen : RankedBaseScreen(Text.translatable("cobblemon_ranked.cross_server.title")) {
    override fun init() {
        super.init()

        val client = MinecraftClient.getInstance()
        val scaleX = client.window.scaledWidth / 1920f
        val scaleY = client.window.scaledHeight / 1080f

        val lang = client.options.language ?: "en_us"
        val langSuffix = if (lang == "zh_cn") "zh" else "en"

        // 关闭按钮：右上角
        addDrawableChild(object : StandardImageButton(
            (uiX + uiWidth - 90 * scaleX).toInt(),
            (uiY + 20 * scaleY).toInt(),
            (80 * 0.85 * scaleX).toInt(), (73 * 0.85 * scaleY).toInt(),
            Identifier.of("cobblemon_ranked", "textures/gui/btn_close.png"),
            Identifier.of("cobblemon_ranked", "textures/gui/hover_overlay_btn_close.png")
        ) {
            override fun onClicked() {
                close()
            }
        })

        // 返回主菜单按钮，底部居中
        val backButtonWidth = (400 * 0.85 * scaleX).toInt()
        val backButtonHeight = (107 * 0.85 * scaleY).toInt()
        val backButtonX = uiX + uiWidth / 2 - backButtonWidth / 2
        val backButtonY = uiY + uiHeight - (150 * scaleY).toInt()

        addDrawableChild(object : StandardImageButton(
            backButtonX, backButtonY, backButtonWidth, backButtonHeight,
            Identifier.of("cobblemon_ranked", "textures/gui/button_back_$langSuffix.png")
        ) {
            override fun onClicked() {
                client.setScreen(RankedMainMenuScreen())
            }
        })
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)

        val scaleX = client!!.window.scaledWidth / 1920f
        val scaleY = client!!.window.scaledHeight / 1080f

        // 渲染聊天内容
        val client = MinecraftClient.getInstance()
        client.inGameHud.chatHud.render(context, client.inGameHud.ticks, mouseX, mouseY, false)

        // 中央提示框，自适应大小
        val boxWidth = (400 * scaleX).toInt()
        val boxHeight = (100 * scaleY).toInt()
        val boxX = uiX + uiWidth / 2 - boxWidth / 2
        val boxY = uiY + uiHeight / 2 - boxHeight / 2

        context.fillGradient(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xC0202020.toInt(), 0xC0303030.toInt())
        context.drawBorder(boxX, boxY, boxWidth, boxHeight, 0x50FFFFFF)

        // 提示文字
        val message = Text.translatable("cobblemon_ranked.cross_server.developing").string
        val textX = uiX + uiWidth / 2 - textRenderer.getWidth(message) / 2
        val textY = boxY + boxHeight / 2 - 4

        context.drawText(textRenderer, message, textX, textY, 0xFFFFFF, true)

        super.render(context, mouseX, mouseY, delta)
    }
}