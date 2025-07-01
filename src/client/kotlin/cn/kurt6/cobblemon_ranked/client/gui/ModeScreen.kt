package cn.kurt6.cobblemon_ranked.client.gui

import cn.kurt6.cobblemon_ranked.network.RequestPlayerRankPayload
import cn.kurt6.cobblemon_ranked.network.RequestType
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class ModeScreen(private val mode: String) : RankedBaseScreen(Text.literal("Cobblemon - $mode")) {
    companion object {
        fun modeName(mode: String): Text {
            return Text.translatable("cobblemon_ranked.mode.$mode")
        }
    }

    private var leaderboardPage = 1

    private var playerInfoLines: List<Text> = listOf(Text.translatable("screen.cobblemon_ranked.loading.player_info"))
    private var seasonInfoLines: List<Text> = listOf(Text.translatable("screen.cobblemon_ranked.loading.season_info"))
    private var leaderboardLines: List<Text> = listOf(Text.translatable("screen.cobblemon_ranked.loading.leaderboard"))

    private lateinit var playerInfoRenderer: FancyMultilineTextRenderer
    private lateinit var seasonInfoRenderer: FancyMultilineTextRenderer
    private lateinit var leaderboardRenderer: FancyMultilineTextRenderer

    private var leaderboardRegionX = 0
    private var leaderboardRegionY = 0
    private var leaderboardRegionWidth = 0
    private var leaderboardRegionHeight = 0

    private var playerInfoRegionX = 0
    private var playerInfoRegionY = 0
    private var playerInfoRegionWidth = 0
    private var playerInfoRegionHeight = 0

    private var seasonInfoRegionX = 0
    private var seasonInfoRegionY = 0
    private var seasonInfoRegionWidth = 0
    private var seasonInfoRegionHeight = 0

    private var seasonScrollOffset = 0
    private var playerScrollOffset = 0
    private var leaderboardScrollOffset = 0

    val lang = MinecraftClient.getInstance().options.language ?: "en_us"
    val langSuffix = if (lang == "zh_cn") "zh" else "en"

    val leaderboard_Width = 500
    val leaderboard_Height = 800

    val panel_Width = 500
    val panel_Height = 350

    val seasonpanel_Width = 500
    val seasonpanel_Height = 400

    private val modeButtons = mutableListOf<ModeSwitchButton>()

    override fun init() {
        super.init()

        addModeSwitchButtons()

        val scaleX = client!!.window.scaledWidth / 1920f
        val scaleY = client!!.window.scaledHeight / 1080f

        val buttonWidth = (400 * 0.85 * scaleX).toInt()
        val buttonHeight = (107 * 0.85 * scaleY).toInt()
        val spacingY = (80 * scaleY).toInt()

        val totalHeight = 3 * buttonHeight + 2 * spacingY
        val startY = uiY + uiHeight / 2 - totalHeight / 2
        val centerX = uiX + uiWidth / 2 - buttonWidth / 2

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

        addDrawableChild(object : StandardImageButton(
            centerX, startY,
            buttonWidth, buttonHeight,
            Identifier.of("cobblemon_ranked", "textures/gui/button_match_$langSuffix.png")
        ) {
            override fun onClicked() {
                sendCommand("rank queue join $mode")
                NotificationManager.show(Text.literal(getLocalizedText("§6已加入 $mode 匹配队列", "§cJoined the $mode matching queue")))
            }
        })

        addDrawableChild(object : StandardImageButton(
            centerX, startY + buttonHeight + spacingY,
            buttonWidth, buttonHeight,
            Identifier.of("cobblemon_ranked", "textures/gui/button_cancel_$langSuffix.png")
        ) {
            override fun onClicked() {
                sendCommand("rank queue leave")
            }
        })

        addDrawableChild(object : StandardImageButton(
            centerX, startY + 2 * (buttonHeight + spacingY),
            buttonWidth, buttonHeight,
            Identifier.of("cobblemon_ranked", "textures/gui/button_state_$langSuffix.png")
        ) {
            override fun onClicked() {
                sendCommand("rank status")
            }
        })

        addDrawableChild(object : StandardImageButton(
            centerX, startY + 3 * (buttonHeight + spacingY),
            buttonWidth, buttonHeight,
            Identifier.of("cobblemon_ranked", "textures/gui/button_back_$langSuffix.png")
        ) {
            override fun onClicked() {
                client?.setScreen(RankedMainMenuScreen())
            }
        })

        addDrawableChild(
            ImageButton(
                (uiX + 240 * scaleX).toInt(), (uiY + 845 * scaleY).toInt(),
                (90 * scaleX).toInt(), (70 * scaleY).toInt(),
                Identifier.of("cobblemon_ranked", "textures/gui/button_prev.png"),
                Identifier.of("cobblemon_ranked", "textures/gui/button_prev_hover.png")
            ) {
                if (leaderboardPage > 1) {
                    leaderboardPage--
                    requestInfo(RequestType.LEADERBOARD)
                }
            })

        addDrawableChild(
            ImageButton(
                (uiX + 420 * scaleX).toInt(), (uiY + 845 * scaleY).toInt(),
                (90 * scaleX).toInt(), (70 * scaleY).toInt(),
                Identifier.of("cobblemon_ranked", "textures/gui/button_next.png"),
                Identifier.of("cobblemon_ranked", "textures/gui/button_next_hover.png")
            ) {
                leaderboardPage++
                requestInfo(RequestType.LEADERBOARD)
            })

        requestInfo(RequestType.PLAYER)
        requestInfo(RequestType.SEASON)
        requestInfo(RequestType.LEADERBOARD)

        addDrawableChild(
            Panel(
                (uiX + 120 * scaleX).toInt(),
                (uiY + 150 * scaleY).toInt(),
                (leaderboard_Width * scaleX).toInt(),
                (leaderboard_Height * scaleY).toInt(),
                Identifier.of("cobblemon_ranked", "textures/gui/panel_leaderboard.png")
            ).apply {
                panelAlpha = 0.8f
            }
        )

        addDrawableChild(
            Panel(
                (uiX + 1250 * scaleX).toInt(),
                (uiY + 150 * scaleY).toInt(),
                (panel_Width * scaleX).toInt(),
                (panel_Height * scaleY).toInt(),
                Identifier.of("cobblemon_ranked", "textures/gui/panel_player.png"),
            ).apply {
                panelAlpha = 0.8f
            }
        )

        addDrawableChild(
            Panel(
                (uiX + 1250 * scaleX).toInt(),
                (uiY + 550 * scaleY).toInt(),
                (seasonpanel_Width * scaleX).toInt(),
                (seasonpanel_Height * scaleY).toInt(),
                Identifier.of("cobblemon_ranked", "textures/gui/panel_season.png"),
            ).apply {
                panelAlpha = 0.8f
            }
        )
    }

    private fun addModeSwitchButtons() {
        // 清除旧按钮
        modeButtons.forEach { remove(it) }
        modeButtons.clear()

        val modes = listOf("singles", "doubles", "2v2singles")
        val scaleX = MinecraftClient.getInstance().window.scaledWidth / 1920f
        val scaleY = MinecraftClient.getInstance().window.scaledHeight / 1080f
        val buttonWidth = (130 * scaleX).toInt()
        val buttonHeight = (85 * scaleY).toInt()
        val spacingX = (50 * scaleX).toInt()

        val totalWidth = modes.size * buttonWidth + (modes.size - 1) * spacingX
        val startX = uiX + uiWidth / 2 - totalWidth / 2
        val y = uiY + (30 * scaleY).toInt()

        modes.forEachIndexed { i, m ->
            val isCurrent = m == mode
            val base = "button_mode_${m}"
            val textureNormal = Identifier.of("cobblemon_ranked", "textures/gui/${base}_${langSuffix}.png")
            val textureActive = Identifier.of("cobblemon_ranked", "textures/gui/${base}_active_${langSuffix}.png")

            val button = ModeSwitchButton(
                x = startX + i * (buttonWidth + spacingX),
                y = y,
                width = buttonWidth,
                height = buttonHeight,
                mode = m,
                currentMode = this.mode,
                textureNormal = textureNormal,
                textureActive = textureActive,
                onClickAction = {
                    client?.setScreen(ModeScreen(m))
                },
                alpha = if (m == mode) 0.8f else 0.4f
            )
            addDrawableChild(button)
            modeButtons.add(button)
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)

        val scaleX = client!!.window.scaledWidth / 1920f
        val scaleY = client!!.window.scaledHeight / 1080f

        // 渲染聊天内容
        val client = MinecraftClient.getInstance()
        client.inGameHud.chatHud.render(context, client.inGameHud.ticks, mouseX, mouseY, false)

        // 新尺寸
        val leaderboardWidth = (leaderboard_Width * scaleX).toInt()
        val leaderboardHeight = (leaderboard_Height * scaleY).toInt()

        val panelWidth = (panel_Width * scaleX).toInt()
        val panelHeight = (panel_Height * scaleY).toInt()

        val seasonpanelHeight = (seasonpanel_Height * scaleY).toInt()

        val panelMarginX = (100 * scaleX).toInt()
        val panelMarginY = (120 * scaleY).toInt()

        // 缩放：不小于 0.2f
        val textScale = ((scaleX + scaleY) / 2).coerceAtLeast(0.2f)

        leaderboardRegionX = uiX + panelMarginX + 23
        leaderboardRegionY = uiY + panelMarginY + 26
        leaderboardRegionWidth = leaderboardWidth - 8
        leaderboardRegionHeight = leaderboardHeight - (90 * scaleX).toInt() -5

        playerInfoRegionX = uiX + uiWidth - panelMarginX - panelWidth - 6
        playerInfoRegionY = uiY + panelMarginY + 27
        playerInfoRegionWidth = panelWidth - 8
        playerInfoRegionHeight = panelHeight - 8

        seasonInfoRegionX = playerInfoRegionX
        seasonInfoRegionY = uiY + panelMarginY + panelHeight + (20 * scaleY).toInt() + 40
        seasonInfoRegionWidth = panelWidth - 8
        seasonInfoRegionHeight = seasonpanelHeight - 8

        // 渲染玩家信息文本
        playerInfoRenderer = FancyMultilineTextRenderer(
            lines = playerInfoLines,
            x = playerInfoRegionX,
            y = playerInfoRegionY,
            width = playerInfoRegionWidth,
            height = playerInfoRegionHeight,
            scale = textScale,
            lineSpacing = 5,
            alignCenter = false,
            drawShadow = true,
            scrollOffset = playerScrollOffset
        )
        playerInfoRenderer.render(context, textRenderer)

        // 渲染赛季信息文本
        seasonInfoRenderer = FancyMultilineTextRenderer(
            lines = seasonInfoLines,
            x = seasonInfoRegionX,
            y = seasonInfoRegionY,
            width = seasonInfoRegionWidth,
            height = seasonInfoRegionHeight,
            scale = textScale,
            lineSpacing = 5,
            alignCenter = false,
            drawShadow = true,
            scrollOffset = seasonScrollOffset
        )
        seasonInfoRenderer.render(context, textRenderer)

        // 渲染排行榜信息文本（支持滚动）
        leaderboardRenderer = FancyMultilineTextRenderer(
            lines = leaderboardLines,
            x = leaderboardRegionX,
            y = leaderboardRegionY,
            width = leaderboardRegionWidth,
            height = leaderboardRegionHeight,
            scale = textScale,
            lineSpacing = 5,
            alignCenter = false,
            drawShadow = true,
            scrollOffset = leaderboardScrollOffset
        )
        leaderboardRenderer.render(context, textRenderer)

        super.render(context, mouseX, mouseY, delta)

        NotificationManager.render(context, client!!.window.scaledWidth, client.window.scaledHeight, textRenderer)

        // 添加右下角制作信息
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

    private fun requestInfo(type: RequestType) {
        val payload = when (type) {
            RequestType.PLAYER -> RequestPlayerRankPayload(type, format = mode)
            RequestType.LEADERBOARD -> RequestPlayerRankPayload(type, format = mode, extra = leaderboardPage.toString())
            else -> RequestPlayerRankPayload(type)
        }
        ClientPlayNetworking.send(payload)
    }

    private fun sendCommand(command: String) {
        val trimmedCommand = command.removePrefix("/")
        client?.player?.networkHandler?.sendChatCommand(trimmedCommand)
    }

    fun getLocalizedText(zh: String, en: String): String {
        return if (lang == "zh_cn") zh else en
    }

    private fun isInRegion(mouseX: Double, mouseY: Double, x: Int, y: Int, width: Int, height: Int): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        return when {
            isInRegion(
                mouseX,
                mouseY,
                leaderboardRegionX,
                leaderboardRegionY,
                leaderboardRegionWidth,
                leaderboardRegionHeight
            ) -> {
                leaderboardScrollOffset = leaderboardRenderer.handleScrollAndReturnOffset(verticalAmount, textRenderer)
                true
            }

            isInRegion(
                mouseX,
                mouseY,
                playerInfoRegionX,
                playerInfoRegionY,
                playerInfoRegionWidth,
                playerInfoRegionHeight
            ) -> {
                playerScrollOffset = playerInfoRenderer.handleScrollAndReturnOffset(verticalAmount, textRenderer)
                true
            }

            isInRegion(
                mouseX,
                mouseY,
                seasonInfoRegionX,
                seasonInfoRegionY,
                seasonInfoRegionWidth,
                seasonInfoRegionHeight
            ) -> {
                seasonScrollOffset = seasonInfoRenderer.handleScrollAndReturnOffset(verticalAmount, textRenderer)
                true
            }

            else -> false
        }
    }

    fun updateInfo(type: RequestType, text: String?) {
        if (text.isNullOrBlank()) {
            if (type == RequestType.LEADERBOARD && leaderboardPage > 1) {
                leaderboardPage--
            }
            when (type) {
                RequestType.PLAYER -> NotificationManager.show(
                    Text.literal(getLocalizedText("§c未找到您的战绩数据。", "§cYour ranked data could not be found."))
                )

                RequestType.SEASON -> NotificationManager.show(
                    Text.literal(getLocalizedText("§c未找到赛季信息。", "§cNo season information found."))
                )

                RequestType.LEADERBOARD -> NotificationManager.show(
                    Text.literal(getLocalizedText("§7暂无更多数据。", "§7No more data available."))
                )
            }
            return
        }

        val lines = text.lines().map { Text.literal(it) }
        when (type) {
            RequestType.PLAYER -> {
                playerInfoLines = lines
                playerScrollOffset = 0
            }
            RequestType.SEASON -> {
                seasonInfoLines = lines
                seasonScrollOffset = 0
            }
            RequestType.LEADERBOARD -> {
                leaderboardLines = lines
                leaderboardScrollOffset = 0
            }
        }
    }
}