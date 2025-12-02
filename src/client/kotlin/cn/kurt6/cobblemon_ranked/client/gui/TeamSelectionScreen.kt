package cn.kurt6.cobblemon_ranked.client.gui

import cn.kurt6.cobblemon_ranked.network.SelectionPokemonInfo
import cn.kurt6.cobblemon_ranked.network.TeamSelectionSubmitPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

class TeamSelectionScreen(
    private val limit: Int,
    private val timeLimit: Int,
    private val opponentName: String,
    private val opponentTeam: List<SelectionPokemonInfo>,
    private val myTeam: List<SelectionPokemonInfo>
) : Screen(Text.translatable("cobblemon_ranked.selection.title")) {

    private val selectedIndices = mutableListOf<Int>()
    private var confirmButton: ButtonWidget? = null

    private var timeRemaining = timeLimit
    private var lastTick = System.currentTimeMillis()

    private val myCardWidth = 72
    private val myCardHeight = 36
    private val gapX = 8
    private val gapY = 8

    override fun shouldCloseOnEsc(): Boolean {
        return false
    }

    override fun init() {
        super.init()
        val centerX = width / 2

        confirmButton = ButtonWidget.builder(Text.translatable("cobblemon_ranked.selection.confirm")) {
            submitSelection()
        }.dimensions(centerX - 50, height - 26, 100, 20).build()
        confirmButton?.active = false
        addDrawableChild(confirmButton)
    }

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {

    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val centerX = width / 2

            val totalWidth = (3 * myCardWidth) + (2 * gapX)
            val startX = centerX - (totalWidth / 2)
            val startY = height / 2 + 10

            myTeam.forEachIndexed { index, _ ->
                val col = index % 3
                val row = index / 3
                val x = startX + col * (myCardWidth + gapX)
                val y = startY + row * (myCardHeight + gapY)

                if (mouseX >= x && mouseX <= x + myCardWidth && mouseY >= y && mouseY <= y + myCardHeight) {
                    toggleSelection(index)
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun toggleSelection(index: Int) {
        if (selectedIndices.contains(index)) {
            selectedIndices.remove(index)
        } else {
            if (selectedIndices.size < limit) {
                selectedIndices.add(index)
            }
        }
        updateButtonState()
    }

    private fun updateButtonState() {
        if (selectedIndices.size == limit) {
            confirmButton?.active = true
            confirmButton?.message = Text.translatable("cobblemon_ranked.selection.confirm")
        } else {
            confirmButton?.active = false
            confirmButton?.message = Text.translatable("cobblemon_ranked.selection.pick", selectedIndices.size, limit)
        }
    }

    private fun submitSelection() {
        val sortedUuids = selectedIndices.map { myTeam[it].uuid }
        ClientPlayNetworking.send(TeamSelectionSubmitPayload(sortedUuids))
        close()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fillGradient(0, 0, width, height, -1072689136, -804253680)

        drawHeader(context)
        drawOpponentSection(context)
        drawMyTeamSection(context, mouseX, mouseY)

        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawHeader(context: DrawContext) {
        val centerX = width / 2

        if (System.currentTimeMillis() - lastTick > 1000) {
            timeRemaining--
            lastTick = System.currentTimeMillis()
            if (timeRemaining <= 0) {
                close()
            }
        }

        val timeColor = if (timeRemaining < 10) 0xFFFF5555.toInt() else 0xFFFFFFFF.toInt()
        val timeText = Text.translatable("cobblemon_ranked.selection.time", timeRemaining)
        context.drawCenteredTextWithShadow(textRenderer, timeText, width - 60, 15, timeColor)

        val titleText = Text.translatable("cobblemon_ranked.selection.title")
        context.drawCenteredTextWithShadow(textRenderer, titleText, centerX, 15, 0xFFD700)
    }

    private fun drawOpponentSection(context: DrawContext) {
        val centerX = width / 2
        val startY = 40

        val opText = Text.translatable("cobblemon_ranked.selection.opponent", opponentName)
        context.drawCenteredTextWithShadow(textRenderer, opText, centerX, startY - 10, 0xFFFFAA)

        val opCardWidth = 70
        val opCardHeight = 35
        val opGap = 5
        val totalWidth = (3 * opCardWidth) + (2 * opGap)
        val startX = centerX - (totalWidth / 2)

        opponentTeam.forEachIndexed { index, info ->
            val col = index % 3
            val row = index / 3
            val x = startX + col * (opCardWidth + opGap)
            val y = startY + row * (opCardHeight + opGap)

            context.fill(x, y, x + opCardWidth, y + opCardHeight, 0x66550000)
            context.drawBorder(x, y, opCardWidth, opCardHeight, 0xFFAA0000.toInt())

            renderPokemonInfo(context, info, x, y, opCardWidth, false)
        }
    }

    private fun drawMyTeamSection(context: DrawContext, mouseX: Int, mouseY: Int) {
        val centerX = width / 2
        val startY = height / 2 + 10

        val teamText = Text.translatable("cobblemon_ranked.selection.your_team")
        context.drawCenteredTextWithShadow(textRenderer, teamText, centerX, startY - 12, 0xAAFFAA)

        val totalWidth = (3 * myCardWidth) + (2 * gapX)
        val startX = centerX - (totalWidth / 2)

        myTeam.forEachIndexed { index, info ->
            val col = index % 3
            val row = index / 3
            val x = startX + col * (myCardWidth + gapX)
            val y = startY + row * (myCardHeight + gapY)

            val isSelected = selectedIndices.contains(index)
            val isHovered = mouseX >= x && mouseX <= x + myCardWidth && mouseY >= y && mouseY <= y + myCardHeight

            var bgColor = 0x66000000
            var borderColor = 0xFF555555.toInt()

            if (isSelected) {
                bgColor = 0x88004400.toInt()
                borderColor = 0xFF00FF00.toInt()
            } else if (isHovered) {
                bgColor = 0x66333333
                borderColor = 0xFFAAAAAA.toInt()
            }

            context.fill(x, y, x + myCardWidth, y + myCardHeight, bgColor)
            context.drawBorder(x, y, myCardWidth, myCardHeight, borderColor)

            if (isSelected) {
                val order = selectedIndices.indexOf(index) + 1
                context.drawText(textRenderer, "#$order", x + myCardWidth - 15, y + 2, 0xFF00FF00.toInt(), true)
            }

            renderPokemonInfo(context, info, x, y, myCardWidth, true)
        }
    }

    private fun renderPokemonInfo(context: DrawContext, info: SelectionPokemonInfo, x: Int, y: Int, width: Int, isSelf: Boolean) {
        val nameColor = if (info.shiny) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt()
        val displayName = if (info.displayName.length > 9) info.displayName.substring(0, 8) + "." else info.displayName
        context.drawText(textRenderer, displayName, x + 4, y + 4, nameColor, true)

        val genderKey = when(info.gender) {
            "MALE" -> "cobblemon_ranked.selection.male"
            "FEMALE" -> "cobblemon_ranked.selection.female"
            else -> ""
        }
        val genderStr = if (genderKey.isNotEmpty()) Text.translatable(genderKey).string else ""
        val lvlStr = Text.translatable("cobblemon_ranked.selection.lvl", info.level).string
        val detailStr = "$lvlStr $genderStr"

        context.drawText(textRenderer, detailStr, x + 4, y + 15, 0xFFAAAAAA.toInt(), true)

        if (info.shiny) {
            val shinyIcon = Text.translatable("cobblemon_ranked.selection.shiny")
            context.drawText(textRenderer, shinyIcon, x + width - 10, y + 15, 0xFFFF0000.toInt(), true)
        }

        if (isSelf && !selectedIndices.contains(myTeam.indexOf(info)) && selectedIndices.size >= limit) {
            context.fill(x, y, x + width, y + 36, 0xAA000000.toInt())
        }
    }

    override fun shouldPause() = false
}