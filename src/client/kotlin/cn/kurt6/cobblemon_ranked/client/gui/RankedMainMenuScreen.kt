package cn.kurt6.cobblemon_ranked.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class RankedMainMenuScreen : RankedBaseScreen(Text.literal("Cobblemon")) {

    override fun init() {
        super.init()

        val client = MinecraftClient.getInstance()
        val scaleX = client.window.scaledWidth / 1920f
        val scaleY = client.window.scaledHeight / 1080f

        val buttonWidth = (400 * 0.9 * scaleX).toInt()
        val buttonHeight = (107 * 0.9 * scaleY).toInt()
        val spacingY = (80 * scaleY).toInt()
        val spacingX = (80 * scaleX).toInt()

        // 计算按钮总高度并使其在竖直方向居中
        val totalHeight = 3 * buttonHeight + 2 * spacingY
        val startY = uiY + uiHeight / 2 - totalHeight / 2

        // 计算按钮组整体居中起点
        val totalWidth = 2 * buttonWidth + spacingX
        val startX = uiX + uiWidth / 2 - totalWidth / 2

        // 关闭按钮：靠右上角
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

        val lang = client.options.language ?: "en_us"
        val langSuffix = if (lang == "zh_cn") "zh" else "en"

        val buttons = listOf(
            Triple("singles", "btn_singles_$langSuffix.png", 0),
            Triple("doubles", "btn_doubles_$langSuffix.png", 0),
            Triple("2v2singles", "btn_2v2singles_$langSuffix.png", 1),
            Triple("exit_queue", "button_cancel_$langSuffix.png", 1),
            Triple("text_menu", "btn_text_$langSuffix.png", 2),
            Triple("cross_server", "btn_cross_$langSuffix.png", 2)
        )

        buttons.chunked(2).forEachIndexed { row, pair ->
            pair.forEachIndexed { col, (mode, textureFile, _) ->
                val x = startX + col * (buttonWidth + spacingX)
                val y = startY + row * (buttonHeight + spacingY)
                val texture = Identifier.of("cobblemon_ranked", "textures/gui/$textureFile")

                addDrawableChild(object : StandardImageButton(x, y, buttonWidth, buttonHeight, texture) {
                    override fun onClicked() {
                        val player = client.player ?: return

                        when (mode) {
                            "text_menu" -> {
                                player.networkHandler.sendChatCommand("rank gui")
                                close()
                            }
                            "exit_queue" -> {
                                player.networkHandler.sendChatCommand("rank queue leave")
                                close()
                            }
                            "cross_server" -> {
                                client.setScreen(CrossServerScreen())
                            }
                            else -> {
                                client.setScreen(ModeScreen(mode))
                            }
                        }
                    }
                })
            }
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        // 渲染聊天内容
        val client = MinecraftClient.getInstance()
        client.inGameHud.chatHud.render(context, client.inGameHud.ticks, mouseX, mouseY, false)

        val title: Text = Text.translatable("screen.cobblemon_ranked.title")
        val titleX = uiX + uiWidth / 2 - textRenderer.getWidth(title) / 2
        val titleY = uiY + (30 * (MinecraftClient.getInstance().window.scaledHeight / 1080f)).toInt()
        context.drawText(textRenderer, title, titleX, titleY, 0xFFFFFF, true)
    }
}