package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel

/**
 * Shared drawing helpers, colours, and sample data for the onboarding illustrations
 * ([EditorVisual], [SyntaxVisual], [ToolWindowVisual]). Kept separate from [OnboardingDialog] so the
 * dialog stays focused on flow while the pixel-level painting lives here.
 */
object OnboardingArt {

    val cardBg: JBColor get() = JBColor(Color(0xF7F8FA), Color(0x2B2D30))
    val cardBorder: Color get() = JBColor.border()
    val stripeBg: JBColor get() = JBColor(Color(0xE6E8EB), Color(0x313438))
    val accent: JBColor get() = JBColor(Color(0x4C8DF6), Color(0x548AF7))

    /** The IDE's real editor font (e.g. JetBrains Mono) so code mocks read as code, without serifs. */
    fun codeFont(size: Float): Font =
        EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN).deriveFont(size)

    /** The baseline y that vertically centres text of the given [fm] within a [boxTop]..+[boxH] box. */
    fun centeredBaseline(boxTop: Int, boxH: Int, fm: java.awt.FontMetrics): Int =
        boxTop + (boxH + fm.ascent) / 2

    /** A translucent tint of [c], used for pill/button fills. */
    fun tint(c: Color, alpha: Int): Color = Color(c.red, c.green, c.blue, alpha)

    /** One coloured comment part: its text, colour, and whether it's drawn bold. */
    data class Seg(val text: String, val color: Color, val bold: Boolean = false)

    /** Draws comment [segs] left-to-right at (x, [baseline]); returns the x after the last part. */
    fun drawSegments(g2: Graphics2D, x: Int, baseline: Int, plain: Font, bold: Font, segs: List<Seg>): Int {
        var cx = x
        for (s in segs) {
            g2.font = if (s.bold) bold else plain
            g2.color = s.color
            g2.drawString(s.text, cx, baseline)
            cx += g2.fontMetrics.stringWidth(s.text)
        }
        return cx
    }

    /** `// TODO [auth] (high) refresh the token` — the full structured format. */
    fun fullComment(faint: Color): List<Seg> = listOf(
        Seg("  // ", faint),
        Seg("TODO", Config.keywordColor("TODO"), bold = true),
        Seg(" [auth]", Config.tagColor("auth"), bold = true),
        Seg(" (high)", Config.priorityColor("high") ?: JBColor.GRAY, bold = true),
        Seg(" refresh the token", Config.descriptionColor()),
    )

    /** `// FIXME handle the 401 case` — keyword + description only (tag & priority optional). */
    fun minimalComment(faint: Color): List<Seg> = listOf(
        Seg("  // ", faint),
        Seg("FIXME", Config.keywordColor("FIXME"), bold = true),
        Seg(" handle the 401 case", Config.descriptionColor()),
    )
}

/**
 * Base panel for the onboarding illustrations: transparent, and paints the shared rounded-card
 * background so each subclass only has to draw its own content on top.
 */
abstract class OnboardingCard : JPanel() {

    init { isOpaque = false }

    /** Enables antialiasing and paints the card background; returns the graphics for content. */
    protected fun beginCard(g: Graphics): Graphics2D {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.color = OnboardingArt.cardBg
        g2.fillRoundRect(0, 0, width - 1, height - 1, 14, 14)
        g2.color = OnboardingArt.cardBorder
        g2.drawRoundRect(0, 0, width - 1, height - 1, 14, 14)
        return g2
    }
}

/** Step 1 — a mini editor: the full comment format on one line, the minimal form on the next. */
class EditorVisual : OnboardingCard() {

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = beginCard(g)
        val plain = OnboardingArt.codeFont(13f)
        val bold = plain.deriveFont(Font.BOLD)
        g2.font = plain
        val faint = JBColor.GRAY
        val x = 20
        var y = 34
        val step = g2.fontMetrics.height + 8

        g2.color = faint; g2.drawString("function login() {", x, y); y += step
        OnboardingArt.drawSegments(g2, x, y, plain, bold, OnboardingArt.fullComment(faint)); y += step
        OnboardingArt.drawSegments(g2, x, y, plain, bold, OnboardingArt.minimalComment(faint)); y += step
        g2.color = faint; g2.drawString("}", x, y)
    }
}

/** Step 2 — the comment format broken into labeled, colour-coded pills. */
class SyntaxVisual : OnboardingCard() {

    private data class Pill(val text: String, val color: Color, val label: String)

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = beginCard(g)
        val pillFont = JBUI.Fonts.label().deriveFont(Font.BOLD, 14f)
        g2.font = pillFont
        val fm = g2.fontMetrics
        val pillH = fm.height + 10
        val padX = 20
        val gap = 10
        val y = height / 2 - pillH / 2 - 6

        val pills = listOf(
            Pill("TODO", Config.keywordColor("TODO"), "keyword"),
            Pill("[auth]", Config.tagColor("auth"), "tag"),
            Pill("(high)", Config.priorityColor("high") ?: JBColor.GRAY, "priority"),
            Pill("refresh the token", Config.descriptionColor(), "description"),
        )
        val total = pills.sumOf { fm.stringWidth(it.text) + padX } + gap * (pills.size - 1)
        var px = (width - total) / 2
        val labelFont = JBUI.Fonts.miniFont()

        for (p in pills) {
            val w = fm.stringWidth(p.text) + padX

            // Pill body: a faint tint of the colour, with a coloured border and text.
            g2.color = OnboardingArt.tint(p.color, 28)
            g2.fillRoundRect(px, y, w, pillH, pillH, pillH)
            g2.color = p.color
            g2.drawRoundRect(px, y, w, pillH, pillH, pillH)
            g2.font = pillFont
            g2.drawString(
                p.text,
                px + (w - fm.stringWidth(p.text)) / 2,
                OnboardingArt.centeredBaseline(y, pillH, fm),
            )

            // Caption centred under the pill.
            g2.font = labelFont
            g2.color = JBColor.GRAY
            val lfm = g2.fontMetrics
            g2.drawString(p.label, px + (w - lfm.stringWidth(p.label)) / 2, y + pillH + lfm.ascent + 6)

            px += w + gap
        }
    }
}

/** Step 3 — a mock IDE window with its bottom stripe, highlighting the TODO Manager button. */
class ToolWindowVisual : OnboardingCard() {

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = beginCard(g)
        val faint = JBColor.GRAY
        val accent = OnboardingArt.accent

        // Editor lines up top (tie back to the markers from step 1).
        val plain = OnboardingArt.codeFont(12f)
        val bold = plain.deriveFont(Font.BOLD)
        g2.font = plain
        g2.color = faint; g2.drawString("function login() {", 20, 30)
        OnboardingArt.drawSegments(g2, 20, 48, plain, bold, OnboardingArt.fullComment(faint))

        // Bottom tool-window stripe.
        val barH = 30
        val barY = height - barH - 1
        g2.color = OnboardingArt.stripeBg
        g2.fillRect(1, barY, width - 2, barH)
        g2.color = OnboardingArt.cardBorder
        g2.drawLine(1, barY, width - 2, barY)

        // Highlighted "TODO Manager" button on the stripe.
        val icon = IconLoader.getIcon("/icons/todo.svg", javaClass)
        val label = "TODO Manager"
        g2.font = JBUI.Fonts.label()
        val fm = g2.fontMetrics
        val btnW = icon.iconWidth + 6 + fm.stringWidth(label) + 20
        val btnX = 12
        val btnY = barY + 4
        val btnH = barH - 8
        g2.color = OnboardingArt.tint(accent, 30)
        g2.fillRoundRect(btnX, btnY, btnW, btnH, 8, 8)
        g2.color = accent
        g2.drawRoundRect(btnX, btnY, btnW, btnH, 8, 8)
        icon.paintIcon(this, g2, btnX + 10, btnY + (btnH - icon.iconHeight) / 2)
        g2.color = JBColor.foreground()
        g2.drawString(label, btnX + 10 + icon.iconWidth + 6, OnboardingArt.centeredBaseline(btnY, btnH, fm))

        // Arrow + hint pointing up at the button.
        g2.color = accent
        val ax = btnX + btnW / 2
        g2.drawLine(ax, barY - 20, ax, barY - 3)
        g2.drawLine(ax, barY - 3, ax - 4, barY - 8)
        g2.drawLine(ax, barY - 3, ax + 4, barY - 8)
        g2.drawString("Click here to open", ax + 8, barY - 14)
    }
}
