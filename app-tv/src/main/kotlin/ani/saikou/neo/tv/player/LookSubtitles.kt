package ani.saikou.neo.tv.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withTranslation
import ani.saikou.neo.shared.settings.Edge
import ani.saikou.neo.shared.settings.Placement
import ani.saikou.neo.shared.settings.SubtitleFont
import ani.saikou.neo.shared.settings.SubtitlePaint
import ani.saikou.neo.tv.R

// Padding around the text, as a share of its size, from the design: the box is 0.08 tall and 0.3 wide around each
// line, and the band reaches 0.4 above and below the lines.
private const val BOX_PAD_Y = 0.08f
private const val BOX_PAD_X = 0.3f
private const val BAND_PAD = 0.4f
private const val HIGHLIGHT = 0x80FFFFFF.toInt()
private const val RELIEF_EDGE_STEP = 1.5f
private const val RELIEF_SOFT_STEP = 2.5f
private const val RELIEF_SOFT_ALPHA = 0xB3
private const val RGB = 0xFFFFFF
private const val ALPHA_SHIFT = 24

// The Sample label, from the design: 12 px text in a 22 px pill on 26 px lines, 24 px above them.
private const val LABEL_TEXT = 0.46f
private const val LABEL_HEIGHT = 0.85f
private const val LABEL_PAD_X = 0.4f
private const val LABEL_GAP = 0.9f
private const val LABEL_FILL = 0xC711121C.toInt()
private const val LABEL_INK = 0xD9F6F5F2.toInt()

/**
 * Plain subtitle lines drawn in a look, in place of Media3's painter, so every edge, thickness, box and band shows
 * as its preview does. The lines are laid out once each time they or the look change, and only drawn on each frame.
 */
internal class LookSubtitles(
    context: Context,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var paint: SubtitlePaint? = null
    private var textFraction = 0f
    private var placement = Placement(fromTop = false, fraction = 0f)
    private var typeface: Typeface = Typeface.DEFAULT
    private var lines: List<CharSequence> = emptyList()
    private var label: String? = null
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var labelBox: RectF? = null
    private var layout: StaticLayout? = null
    private var layoutLeft = 0f
    private var layoutTop = 0f

    /** How tall text is now, in pixels. */
    var textPx = 0f
        private set

    /** Lines centre in the left half, which the side panel leaves in view. */
    var aside = false
        set(value) {
            if (field == value) return
            field = value
            lay()
        }

    init {
        isFocusable = false
    }

    /** Draws in [paint], with text [textFraction] of the height tall, at [placement], in [tvTypeface] when the TV's. */
    fun look(
        paint: SubtitlePaint,
        textFraction: Float,
        placement: Placement,
        tvTypeface: Typeface? = null,
    ) {
        val typeface = typeface(paint, tvTypeface)
        if (paint == this.paint && textFraction == this.textFraction && placement == this.placement && typeface == this.typeface) return
        this.paint = paint
        this.textFraction = textFraction
        this.placement = placement
        this.typeface = typeface
        lay()
    }

    /** Draws [lines], with a small [label] above them, such as the one that marks a sample. */
    fun show(
        lines: List<CharSequence>,
        label: String? = null,
    ) {
        if (lines.map { it.toString() } == this.lines.map { it.toString() } && label == this.label) return
        this.lines = lines
        this.label = label
        lay()
    }

    /** Where the label's pill sits, or null with no label. */
    fun labelBounds(): RectF? = labelBox?.let(::RectF)

    /** Where the text sits, without its edge, box or band, or null with nothing to draw. */
    fun textBounds(): RectF? {
        val layout = layout ?: return null
        val left = (0 until layout.lineCount).minOf { layout.getLineLeft(it) }
        val right = (0 until layout.lineCount).maxOf { layout.getLineRight(it) }
        return RectF(layoutLeft + left, layoutTop, layoutLeft + right, layoutTop + layout.height)
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) = lay()

    private fun lay() {
        layout = null
        labelBox = null
        invalidate()
        val paint = paint ?: return
        if (lines.isEmpty() || width == 0 || height == 0) return
        textPx = height * textFraction
        textPaint.textSize = textPx
        textPaint.typeface = typeface
        val padX = textPx * BOX_PAD_X
        val area = if (aside) width / 2 else width
        val available = (area - 2 * padX).toInt()
        if (available <= 0) return
        val text = SpannableStringBuilder()
        lines.forEachIndexed { index, line -> text.apply { if (index > 0) append('\n') }.append(line) }
        val laid =
            StaticLayout.Builder
                .obtain(text, 0, text.length, textPaint, available)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
        val gap = height * placement.fraction
        val top = if (placement.fromTop) gap else height - gap - laid.height
        val pad = textPx * if (paint.bandArgb != null) BAND_PAD else BOX_PAD_Y
        layoutLeft = padX
        layoutTop = top.coerceIn(pad, (height - laid.height - pad).coerceAtLeast(pad))
        layout = laid
        label?.let { layLabel(it, laid, area) }
    }

    private fun layLabel(
        label: String,
        laid: StaticLayout,
        area: Int,
    ) {
        labelPaint.textSize = textPx * LABEL_TEXT
        labelPaint.typeface = atkinson(bold = true)
        val pillHeight = textPx * LABEL_HEIGHT
        val pillWidth = labelPaint.measureText(label) + 2 * textPx * LABEL_PAD_X
        val centre = area / 2f
        // Above the lines, or below them when there's no room above.
        val above = layoutTop - textPx * LABEL_GAP
        val top = if (above - pillHeight >= 0) above - pillHeight else layoutTop + laid.height + textPx * LABEL_GAP
        labelBox = RectF(centre - pillWidth / 2, top, centre + pillWidth / 2, top + pillHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val layout = layout ?: return
        val paint = paint ?: return
        drawLabel(canvas)
        paint.bandArgb?.let {
            fill.color = it
            val band = textPx * BAND_PAD
            canvas.drawRect(0f, layoutTop - band, width.toFloat(), layoutTop + layout.height + band, fill)
        }
        canvas.withTranslation(layoutLeft, layoutTop) {
            paint.boxArgb?.let { drawBoxes(this, layout, it) }
            drawEdge(this, layout, paint)
            textPaint.color = paint.textArgb
            textPaint.style = Paint.Style.FILL
            layout.draw(this)
            textPaint.clearShadowLayer()
        }
    }

    private fun drawLabel(canvas: Canvas) {
        val box = labelBox ?: return
        val label = label ?: return
        fill.color = LABEL_FILL
        canvas.drawRoundRect(box, box.height() / 2, box.height() / 2, fill)
        labelPaint.color = LABEL_INK
        val baseline = box.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2
        canvas.drawText(label, box.left + textPx * LABEL_PAD_X, baseline, labelPaint)
    }

    // One box a line. Only the first and last reach past the text above and below, so boxes never overlap.
    private fun drawBoxes(
        canvas: Canvas,
        layout: StaticLayout,
        argb: Int,
    ) {
        fill.color = argb
        val padX = textPx * BOX_PAD_X
        val padY = textPx * BOX_PAD_Y
        val last = layout.lineCount - 1
        for (line in 0..last) {
            canvas.drawRect(
                layout.getLineLeft(line) - padX,
                layout.getLineTop(line) - if (line == 0) padY else 0f,
                layout.getLineRight(line) + padX,
                layout.getLineBottom(line) + if (line == last) padY else 0f,
                fill,
            )
        }
    }

    // Draws what goes under the letters, or sets the shadow the letters cast.
    private fun drawEdge(
        canvas: Canvas,
        layout: StaticLayout,
        paint: SubtitlePaint,
    ) {
        when (paint.edge) {
            Edge.NONE -> {}

            Edge.OUTLINE -> {
                textPaint.color = paint.edgeArgb
                textPaint.style = Paint.Style.FILL_AND_STROKE
                textPaint.strokeJoin = Paint.Join.ROUND
                textPaint.strokeWidth = paint.outlineWidth(textPx, density)
                layout.draw(canvas)
                textPaint.strokeWidth = 0f
            }

            Edge.SHADOW -> {
                val shadow = paint.shadow(textPx, density)
                textPaint.setShadowLayer(shadow.radius, shadow.dx, shadow.dy, paint.edgeArgb)
            }

            Edge.RAISED, Edge.SUNKEN -> {
                // Light from the top left for Raised, from the bottom right for Sunken.
                val step = paint.reliefUnit(textPx, density) * if (paint.edge == Edge.RAISED) 1 else -1
                textPaint.style = Paint.Style.FILL
                drawMoved(canvas, layout, -step, HIGHLIGHT)
                drawMoved(canvas, layout, step * RELIEF_EDGE_STEP, paint.edgeArgb)
                val soft = (RELIEF_SOFT_ALPHA shl ALPHA_SHIFT) or (paint.edgeArgb and RGB)
                textPaint.setShadowLayer(step.coerceAtLeast(-step), step * RELIEF_SOFT_STEP, step * RELIEF_SOFT_STEP, soft)
            }
        }
    }

    private fun drawMoved(
        canvas: Canvas,
        layout: StaticLayout,
        by: Float,
        argb: Int,
    ) {
        textPaint.color = argb
        canvas.withTranslation(by, by) { layout.draw(this) }
    }

    private fun typeface(
        paint: SubtitlePaint,
        tv: Typeface?,
    ): Typeface {
        val style = if (paint.bold) Typeface.BOLD else Typeface.NORMAL
        return when (paint.font) {
            null -> tv ?: Typeface.DEFAULT
            SubtitleFont.ATKINSON_HYPERLEGIBLE -> atkinson(paint.bold)
            SubtitleFont.SANS_SERIF -> Typeface.create(Typeface.SANS_SERIF, style)
            SubtitleFont.SERIF -> Typeface.create(Typeface.SERIF, style)
            SubtitleFont.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, style)
            SubtitleFont.CASUAL -> Typeface.create("casual", style)
        }
    }

    private fun atkinson(bold: Boolean): Typeface {
        val font = if (bold) R.font.atkinson_hyperlegible_next_bold else R.font.atkinson_hyperlegible_next_regular
        return ResourcesCompat.getFont(context, font) ?: Typeface.DEFAULT
    }
}
