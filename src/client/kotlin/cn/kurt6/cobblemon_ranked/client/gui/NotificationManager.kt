package cn.kurt6.cobblemon_ranked.client.gui

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

object NotificationManager {
    private var notificationText: Text? = null
    private var displayTicks = 0
    private const val DISPLAY_TIME = 100  // 持续时间，单位 tick

    fun show(text: Text) {
        notificationText = text
        displayTicks = DISPLAY_TIME
    }

    fun render(context: DrawContext, screenWidth: Int, screenHeight: Int, textRenderer: TextRenderer) {
        if (displayTicks > 0 && notificationText != null) {
            val alpha = (displayTicks / DISPLAY_TIME.toFloat()).coerceIn(0f, 1f)
            val x = screenWidth / 2
            val y = (screenHeight * 0.75).toInt()

            val text = notificationText!!

            // 白色带阴影文字
            context.drawCenteredTextWithShadow(
                textRenderer,
                text,
                x,
                y,
                0xFFFFFF
            )

            displayTicks--
        }
    }
}
