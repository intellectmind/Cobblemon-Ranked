package cn.kurt6.cobblemon_ranked.client.gui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.text.Style
import net.minecraft.text.Text

class RankedMainScreen : Screen(Text.translatable("screen.cobblemon_ranked.title")) {
    companion object {
        private const val BUTTON_WIDTH = 160
        private const val BUTTON_HEIGHT = 20
        private const val BUTTON_SPACING = 25
    }

    private data class RankedButton(
        val labelKey: String,
        val command: String? = null,
        val tooltipKey: String? = null,
        val onClick: (() -> Unit)? = null
    )

    private val infoButtons = listOf(
        RankedButton("gui.cobblemon_ranked.button.gui", "rank gui", "tooltip.cobblemon_ranked.button.gui"),
        RankedButton("gui.cobblemon_ranked.button.stats", "rank gui_myinfo", "tooltip.cobblemon_ranked.button.stats"),
        RankedButton("gui.cobblemon_ranked.button.season", "rank season", "tooltip.cobblemon_ranked.button.season"),
        RankedButton("gui.cobblemon_ranked.button.leaderboard", "rank gui_top", "tooltip.cobblemon_ranked.button.leaderboard"),
    )

    private val actionButtons = listOf(
        RankedButton("gui.cobblemon_ranked.button.join_singles", "rank queue join singles", "tooltip.cobblemon_ranked.button.join_singles"),
        RankedButton("gui.cobblemon_ranked.button.join_doubles", "rank queue join doubles", "tooltip.cobblemon_ranked.button.join_doubles"),
        RankedButton("gui.cobblemon_ranked.button.join_2v2singles", "rank queue join 2v2singles", "tooltip.cobblemon_ranked.button.join_2v2singles"),
        RankedButton("gui.cobblemon_ranked.button.leave", "rank queue leave", "tooltip.cobblemon_ranked.button.leave"),
        RankedButton("gui.cobblemon_ranked.button.close", tooltipKey = "tooltip.cobblemon_ranked.button.close", onClick = { close() })
    )


    override fun init() {
        val centerX = width / 2 - 10
        val centerY = height / 2

        var offsetY = centerY - (infoButtons.size * BUTTON_SPACING + 40)

        // 分区标题：信息
        val infoTitle = TextWidget(Text.literal("§f§l▶ Cobblemon Rank"), textRenderer)
        infoTitle.setPosition(centerX - BUTTON_WIDTH / 2, offsetY)
        addDrawableChild(infoTitle)
        offsetY += BUTTON_SPACING

        infoButtons.forEach { btn ->
            addRankedButton(centerX, offsetY, btn)
            offsetY += BUTTON_SPACING
        }

        offsetY += 10

        actionButtons.forEach { btn ->
            addRankedButton(centerX, offsetY, btn)
            offsetY += BUTTON_SPACING
        }
    }

    private fun addRankedButton(x: Int, y: Int, data: RankedButton) {
        val label = Text.translatable(data.labelKey).copy().setStyle(
            Style.EMPTY.withColor(0xFFFFFF)
        )

        val button = ButtonWidget.builder(label) {
            data.onClick?.invoke() ?: data.command?.let { sendCommand(it) }
            client?.setScreen(null)
        }.dimensions(x - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()

        data.tooltipKey?.let {
            button.setTooltip(Tooltip.of(Text.translatable(it)))
        }

        addDrawableChild(button)
    }


    private fun sendCommand(cmd: String) {
        client?.player?.networkHandler?.sendChatCommand(cmd)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0xAA000000.toInt())
        super.render(context, mouseX, mouseY, delta)
    }
}
