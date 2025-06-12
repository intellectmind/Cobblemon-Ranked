package cn.kurt6.cobblemon_ranked.client.gui

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.text.OrderedText
import kotlin.math.max
import kotlin.math.min

class FancyMultilineTextRenderer(
    private val lines: List<Text>,
    private val x: Int,
    private val y: Int,
    private val width: Int,
    private val height: Int,
    private val scale: Float = 1.0f,
    private val lineSpacing: Int = 4,
    private val alignCenter: Boolean = false,
    private val drawShadow: Boolean = true,
    private var scrollOffset: Int = 0
) {
    fun render(context: DrawContext, textRenderer: TextRenderer) {
        context.matrices.push()

        val finalScale = scale.coerceAtLeast(0.8f)
        context.matrices.scale(finalScale, finalScale, 1f)

        val wrappedLines = lines.flatMap { textRenderer.wrapLines(it, width) }
        val visibleLines = getVisibleLines(wrappedLines, textRenderer)

        val maxLineWidth = wrappedLines.maxOfOrNull { textRenderer.getWidth(it) } ?: 0
        val scaledX = (x + (width - maxLineWidth) / 2) / finalScale

        var offsetY = y / finalScale

        for (line in visibleLines) {
            val lineWidth = textRenderer.getWidth(line)
            val renderX = if (alignCenter) (x + (width - lineWidth) / 2) / finalScale else scaledX

            context.drawText(
                textRenderer,
                line,
                renderX.toInt(),
                offsetY.toInt(),
                0xFFFFFF,
                drawShadow
            )
            offsetY += (textRenderer.fontHeight + lineSpacing)
        }

        // 绘制滚动条（如果内容超出）
        drawScrollbar(context, textRenderer, wrappedLines.size)

        context.matrices.pop()
    }

    private fun getVisibleLines(wrapped: List<OrderedText>, renderer: TextRenderer): List<OrderedText> {
        val maxLines = height / (renderer.fontHeight + lineSpacing)
        val start = scrollOffset.coerceAtMost(wrapped.size)
        val end = (start + maxLines).coerceAtMost(wrapped.size)
        return wrapped.subList(start, end)
    }

    fun handleScroll(scrollDelta: Double, textRenderer: TextRenderer) {
        val wrappedLines = lines.flatMap { textRenderer.wrapLines(it, width) }
        val maxLines = height / (textRenderer.fontHeight + lineSpacing)
        val maxOffset = max(0, wrappedLines.size - maxLines)

        if (scrollDelta < 0) {
            scrollOffset = min(scrollOffset + 1, maxOffset)
        } else if (scrollDelta > 0) {
            scrollOffset = max(scrollOffset - 1, 0)
        }
    }

    private fun drawScrollbar(context: DrawContext, textRenderer: TextRenderer, totalLines: Int) {
        val lineHeight = textRenderer.fontHeight + lineSpacing
        val maxVisibleLines = height / lineHeight

        if (totalLines <= maxVisibleLines) return // 不需要滚动条

        val scrollbarX = x + width - 4
        val scrollbarY = y
        val scrollbarWidth = 4
        val scrollbarHeight = height

        val scrollRatio = scrollOffset.toFloat() / (totalLines - maxVisibleLines).toFloat()
        val handleHeight = (height * maxVisibleLines.toFloat() / totalLines).toInt().coerceAtLeast(10)
        val handleY = scrollbarY + ((height - handleHeight) * scrollRatio).toInt()

        // 背景轨道
        context.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0xFF202020.toInt())

        // 滑块（handle）
        context.fill(scrollbarX, handleY, scrollbarX + scrollbarWidth, handleY + handleHeight, 0xFFFFFFFF.toInt())
    }

    fun handleScrollAndReturnOffset(scrollDelta: Double, textRenderer: TextRenderer): Int {
        handleScroll(scrollDelta, textRenderer)
        return scrollOffset
    }
}