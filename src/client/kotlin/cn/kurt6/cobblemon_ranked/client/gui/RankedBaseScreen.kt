package cn.kurt6.cobblemon_ranked.client.gui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.random.Random
import kotlin.math.cos
import kotlin.math.sin

abstract class RankedBaseScreen(title: Text) : Screen(title) {
    protected lateinit var backgroundTexture: Identifier
    protected val particles = mutableListOf<Particle>()
    protected val random = Random.create()
    protected var animationTime = 0f

    protected var uiX = 0
    protected var uiY = 0
    protected var uiWidth = 0
    protected var uiHeight = 0

    override fun init() {
        super.init()
        loadTextures()

        uiWidth = (width * 1).toInt()
        uiHeight = (height * 1).toInt()
        uiX = (width - uiWidth) / 2
        uiY = (height - uiHeight) / 2

        particles.clear()
        repeat(50) {
            particles.add(
                Particle(
                    x = uiX + random.nextDouble() * uiWidth,
                    y = uiY + random.nextDouble() * uiHeight,
                    size = random.nextDouble() * 3 + 1,
                    speed = random.nextDouble() * 20 + 10,
                    angle = random.nextDouble() * Math.PI * 2,
                    color = ((0x60 + random.nextInt(0x40)) shl 24) or
                            (random.nextInt(0x40) shl 16) or
                            (random.nextInt(0xA0) shl 8) or
                            0xFF
                )
            )
        }
    }

    private fun loadTextures() {
        backgroundTexture = Identifier.of("cobblemon_ranked", "textures/gui/ranked_bg.png")
    }

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        animationTime += delta
        context.drawTexture(backgroundTexture, uiX, uiY, 0f, 0f, uiWidth, uiHeight, uiWidth, uiHeight)
        context.fill(uiX, uiY, uiX + uiWidth, uiY + uiHeight, 0x90101010u.toInt())

        particles.forEach {
            it.update(delta)
            it.render(context)
        }

        context.fillGradient(uiX, uiY, uiX + uiWidth, uiY + 40, 0x80000000u.toInt(), 0x00000000u.toInt())
        context.fillGradient(uiX, uiY + uiHeight - 40, uiX + uiWidth, uiY + uiHeight, 0x00000000u.toInt(), 0x80000000u.toInt())
    }

    override fun close() {
        particles.clear()
        super.close()
    }

    protected inner class Particle(
        var x: Double,
        var y: Double,
        var size: Double,
        var speed: Double,
        var angle: Double,
        var color: Int
    ) {
        fun update(delta: Float) {
            val safeDelta = delta.coerceIn(0f, 0.1f)
            x += cos(angle) * speed * safeDelta
            y += sin(angle) * speed * safeDelta

            if (x < uiX - 50) x = (uiX + uiWidth + 50).toDouble()
            if (x > uiX + uiWidth + 50) x = (uiX - 50).toDouble()
            if (y < uiY - 50) y = (uiY + uiHeight + 50).toDouble()
            if (y > uiY + uiHeight + 50) y = (uiY - 50).toDouble()
        }

        fun render(context: DrawContext) {
            context.fill(
                x.toInt(), y.toInt(),
                (x + size).toInt(), (y + size).toInt(),
                color
            )
        }
    }
}
