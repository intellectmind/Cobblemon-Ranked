package cn.kurt6.cobblemon_ranked.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

class CrossServerScreen : RankedBaseScreen(Text.translatable("cobblemon_ranked.cross_server.title")) {
    // 自定义矩形类用于存储链接区域
    private class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(pointX: Double, pointY: Double): Boolean {
            return pointX >= x && pointY >= y && pointX < x + width && pointY < y + height
        }
    }

    // 存储链接区域的变量
    private var webPortalRect: Rect? = null
    private var authKeyRect: Rect? = null

    // 光标状态
    private var isHandCursor = false

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

        // 添加中间两个按钮
        val buttonWidth = (400 * 0.85 * scaleX).toInt()
        val buttonHeight = (107 * 0.85 * scaleY).toInt()
        val spacingY = (50 * scaleY).toInt()
        val centerX = uiX + uiWidth / 2 - buttonWidth / 2
        val centerY = uiY + uiHeight / 2 - (2 * buttonHeight + spacingY) / 2

        // 加入匹配按钮
        addDrawableChild(object : StandardImageButton(
            centerX, centerY,
            buttonWidth, buttonHeight,
            Identifier.of("cobblemon_ranked", "textures/gui/btn_singles_$langSuffix.png")
        ) {
            override fun onClicked() {
                sendCommand("rank cross join singles")
            }
        })

        // 离开匹配按钮
        addDrawableChild(object : StandardImageButton(
            centerX, centerY + buttonHeight + spacingY,
            buttonWidth, buttonHeight,
            Identifier.of("cobblemon_ranked", "textures/gui/button_cancel_$langSuffix.png")
        ) {
            override fun onClicked() {
                sendCommand("rank cross leave")
            }
        })
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)

        // 先调用super.render()确保基础元素正确渲染
        super.render(context, mouseX, mouseY, delta)

        val client = MinecraftClient.getInstance()
        val scaleX = client.window.scaledWidth / 1920f
        val scaleY = client.window.scaledHeight / 1080f

        // 准备文本
        val webPortal = Text.translatable("cobblemon_ranked.cross_server.web_portal",
            "http://139.196.103.55")
        val authKey = Text.translatable("cobblemon_ranked.cross_server.auth_key",
            "cobblemonranked")

        // 使用正确的缩放因子计算位置
        val baseY = (height * 0.1f).toInt()  // 屏幕高度10%处开始
        val spacing = (20 * scaleY).toInt()   // 使用scaleY计算间距
        val fontHeight = textRenderer.fontHeight

        // 重置矩阵确保文本位置正确
        context.matrices.push()
        context.matrices.translate(0f, 0f, 500f)  // 确保文本在顶层渲染

        // 第一行文本（门户链接）
        val webPortalWidth = textRenderer.getWidth(webPortal)
        val webPortalX = width / 2 - webPortalWidth / 2
        val webPortalColor = if (webPortalRect?.contains(mouseX.toDouble(), mouseY.toDouble()) == true) 0x00FFFF else 0x00FFAA
        context.drawText(textRenderer, webPortal, webPortalX, baseY, webPortalColor, true)
        webPortalRect = Rect(webPortalX, baseY, webPortalWidth, fontHeight)

        // 第二行文本（认证密钥）
        val authKeyWidth = textRenderer.getWidth(authKey)
        val authKeyX = width / 2 - authKeyWidth / 2
        val authKeyColor = if (authKeyRect?.contains(mouseX.toDouble(), mouseY.toDouble()) == true) 0x00FFFF else 0x00FFAA
        context.drawText(textRenderer, authKey, authKeyX, baseY + fontHeight + spacing, authKeyColor, false)
        authKeyRect = Rect(authKeyX, baseY + fontHeight + spacing, authKeyWidth, fontHeight)

        context.matrices.pop()  // 恢复矩阵状态

        // 渲染聊天内容
        client.inGameHud.chatHud.render(context, client.inGameHud.ticks, mouseX, mouseY, false)
        val title: Text = Text.translatable("screen.cobblemon_ranked.title")
        val titleX = uiX + uiWidth / 2 - textRenderer.getWidth(title) / 2
        val titleY = uiY + (30 * (MinecraftClient.getInstance().window.scaledHeight / 1080f)).toInt()
        context.drawText(textRenderer, title, titleX, titleY, 0xFFFFFF, true)

        // 添加右下角制作信息（保持原位置）
        val scaleFactor = 0.5f
        val madeByText = Text.literal("By Kurt")
        val textWidth = (textRenderer.getWidth(madeByText) * scaleFactor).toInt()
        val textHeight = (textRenderer.fontHeight * scaleFactor).toInt()
        val madeByX = width - textWidth - (10 * scaleX).toInt() - 2
        val madeByY = height - textHeight - (10 * scaleY).toInt() - 2

        context.matrices.push()
        context.matrices.translate(madeByX.toFloat(), madeByY.toFloat(), 0f)
        context.matrices.scale(scaleFactor, scaleFactor, 1f)
        context.drawText(textRenderer, madeByText, 0, 0, 0xAAAAAA, true)
        context.matrices.pop()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return false

        // 处理门户链接点击
        webPortalRect?.let {
            if (it.contains(mouseX, mouseY)) {
                Util.getOperatingSystem().open("http://x.cobblemonranked.ip-ddns.com:8000/index")
                return true
            }
        }

        // 处理认证密钥点击
        authKeyRect?.let {
            if (it.contains(mouseX, mouseY)) {
                client?.keyboard?.clipboard = "cobblemonranked"
                client?.player?.sendMessage(Text.translatable("cobblemon_ranked.copied"), false)
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        super.mouseMoved(mouseX, mouseY)

        // 检查是否悬停在链接上
        val newHandCursor = webPortalRect?.contains(mouseX, mouseY) == true ||
                authKeyRect?.contains(mouseX, mouseY) == true

        // 仅当状态改变时更新光标
        if (newHandCursor != isHandCursor) {
            isHandCursor = newHandCursor
            if (isHandCursor) {
                // 使用系统默认手型光标
                GLFW.glfwSetCursor(
                    MinecraftClient.getInstance().window.handle,
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR)
                )
            } else {
                // 恢复默认光标
                GLFW.glfwSetCursor(
                    MinecraftClient.getInstance().window.handle,
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
                )
            }
        }
    }

    private fun sendCommand(command: String) {
        val trimmedCommand = command.removePrefix("/")
        client?.player?.networkHandler?.sendChatCommand(trimmedCommand)
    }
}