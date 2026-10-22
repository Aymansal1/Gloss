package app.gloss.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import app.gloss.R
import app.gloss.data.InstalledIssue
import app.gloss.pdf.PdfPages
import app.gloss.ui.theme.GoldLeaf
import app.gloss.ui.theme.Pigment
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/**
 * The three printed sides of a [SpinnableBook], painted into bitmaps so they can be mapped onto
 * the book in perspective. Real art where there is some (the catalog cover, the scan's first
 * and last pages), otherwise the same generated designs the flat covers use.
 */
internal object BookFaces {
    /** Width of the front and back bitmaps; height follows the cover's shape. */
    private const val W = 480
    private val H = (W / COVER_ASPECT).toInt()

    class Set(val front: Bitmap, val back: Bitmap, val spine: Bitmap)

    suspend fun paint(
        context: Context,
        pdf: PdfPages,
        info: CoverInfo,
        installed: InstalledIssue?,
        publisher: String,
        spineFraction: Float,
    ): Set = withContext(Dispatchers.Default) {
        val front = frontArt(context, pdf, info, installed)?.fit() ?: pigmentCover(context, info)
        val back = backArt(pdf, installed)?.fit() ?: stockBack(context, info, publisher)
        val spine = spine(context, info, (W * spineFraction).toInt().coerceAtLeast(4))
        Set(front, back, spine)
    }

    private suspend fun frontArt(context: Context, pdf: PdfPages, info: CoverInfo, installed: InstalledIssue?): Bitmap? = when {
        info.coverUrl.isNotBlank() -> runCatching {
            val request = ImageRequest.Builder(context).data(info.coverUrl).allowHardware(false).build()
            (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        }.getOrNull()
        installed != null && installed.hasScan ->
            pdf.ensureCover(installed.pdfFile, File(installed.dir, "cover.jpg"))?.let { decode(it) }
        else -> null
    }

    private suspend fun backArt(pdf: PdfPages, installed: InstalledIssue?): Bitmap? {
        if (installed == null || !installed.hasScan) return null
        return pdf.ensureBackCover(installed.pdfFile, File(installed.dir, "back.jpg"))?.let { decode(it) }
    }

    private suspend fun decode(file: File): Bitmap? = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) }

    /** Crops to the cover's shape and scales to [W] x [H], like ContentScale.Crop. */
    private fun Bitmap.fit(): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val scale = maxOf(W.toFloat() / width, H.toFloat() / height)
        val dw = width * scale
        val dh = height * scale
        Canvas(out).drawBitmap(this, null, RectF((W - dw) / 2f, (H - dh) / 2f, (W + dw) / 2f, (H + dh) / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun inter(context: Context): Typeface =
        ResourcesCompat.getFont(context, R.font.inter_variable) ?: Typeface.DEFAULT

    private fun scribe(context: Context): Typeface =
        ResourcesCompat.getFont(context, R.font.instrument_serif_italic) ?: Typeface.SERIF

    private fun text(context: Context, size: Float, color: Color, weight: Int, opsz: Int = 14) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = inter(context)
        textSize = size
        this.color = color.toArgb()
        fontVariationSettings = "'wght' $weight, 'opsz' $opsz"
    }

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 0.95f)
            .setIncludePad(false)
            .build()

    private fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)

    private fun gradient(canvas: Canvas, w: Int, h: Int, from: Color, to: Color) {
        val p = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), from.toArgb(), to.toArgb(), Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    /** The generated front: the series' pigment, its masthead, the year in the scribe's hand. */
    private fun pigmentCover(context: Context, info: CoverInfo): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pigment = Pigment.forSeries(info.magazineName)
        val on = Pigment.on(pigment)
        gradient(canvas, W, H, pigment, pigment.darken(0.18f))
        val pad = W * 0.09f
        val inner = (W - pad * 2).toInt()

        val mast = layout(info.magazineName.uppercase(), text(context, W * 0.13f, on, 800, 32), inner, 2)
        canvas.save(); canvas.translate(pad, pad); mast.draw(canvas); canvas.restore()

        val title = layout(info.title, text(context, W * 0.075f, on.copy(alpha = 0.82f), 500), inner, 2)
        var y = H - pad - title.height
        canvas.save(); canvas.translate(pad, y); title.draw(canvas); canvas.restore()

        val bar = W * 0.025f
        y -= W * 0.03f + bar
        val leaf = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(pad, 0f, pad + W * 0.34f, 0f, GoldLeaf.stops.map { it.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(pad, y, pad + W * 0.34f, y + bar), bar / 2, bar / 2, leaf)

        if (info.year.isNotBlank()) {
            val year = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = scribe(context); textSize = W * 0.26f; color = on.toArgb() }
            canvas.drawText(info.year, pad, y - W * 0.05f, year)
        }
        return bmp
    }

    /**
     * A back cover for issues whose scan isn't on the phone: the series' colour, the masthead
     * small at the top, and a barcode panel like the one on every newsstand magazine.
     */
    private fun stockBack(context: Context, info: CoverInfo, publisher: String): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pigment = Pigment.forSeries(info.magazineName)
        val on = Pigment.on(pigment)
        gradient(canvas, W, H, pigment.darken(0.12f), pigment.darken(0.38f))
        val pad = W * 0.09f
        val inner = (W - pad * 2).toInt()

        val mast = layout(info.magazineName.uppercase(), text(context, W * 0.06f, on.copy(alpha = 0.75f), 800, 32), inner, 1)
        canvas.save(); canvas.translate(pad, pad); mast.draw(canvas); canvas.restore()

        // The barcode panel, bottom left.
        val panelW = W * 0.46f
        val panelH = W * 0.24f
        val top = H - pad - panelH
        canvas.drawRoundRect(RectF(pad, top, pad + panelW, top + panelH), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        val r = Random(info.id.hashCode() * 31)
        val bars = List(46) { 1 + r.nextInt(3) }
        val unit = (panelW - W * 0.06f) / (bars.sum() * 1.6f)
        var x = pad + W * 0.03f
        val ink = Paint().apply { color = android.graphics.Color.BLACK }
        bars.forEachIndexed { i, b ->
            val bw = b * unit
            if (i % 2 == 0) canvas.drawRect(x, top + W * 0.025f, x + bw, top + panelH * 0.68f, ink)
            x += bw * 1.6f
        }
        val digits = Random(info.id.hashCode()).let { d -> "4910" + (1..9).joinToString("") { d.nextInt(10).toString() } }
        canvas.drawText(digits, pad + W * 0.03f, top + panelH - W * 0.025f, text(context, W * 0.035f, Color.Black, 500).apply { letterSpacing = 0.08f })

        if (publisher.isNotBlank()) {
            val pub = layout(publisher, text(context, W * 0.05f, on.copy(alpha = 0.7f), 400), inner, 1)
            canvas.save(); canvas.translate(pad, top - W * 0.03f - pub.height); pub.draw(canvas); canvas.restore()
        }
        return bmp
    }

    /** The spine: the series' colour with the masthead and issue running down it. */
    private fun spine(context: Context, info: CoverInfo, w: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pigment = Pigment.forSeries(info.magazineName)
        canvas.drawRect(0f, 0f, w.toFloat(), H.toFloat(), Paint().apply { color = pigment.darken(0.05f).toArgb() })
        val size = (w * 0.55f).coerceIn(H * 0.012f, H * 0.03f)
        val paint = text(context, size, Pigment.on(pigment), 700, 32)
        val label = TextUtils.ellipsize("${info.magazineName.uppercase()}   ${info.title}", paint, H * 0.9f, TextUtils.TruncateAt.END).toString()
        // Turned to read top to bottom, centred across the spine.
        canvas.save()
        canvas.rotate(90f)
        val baseline = -(w / 2f) + (paint.descent() - paint.ascent()) / 2f - paint.descent()
        canvas.drawText(label, (H - paint.measureText(label)) / 2f, baseline, paint)
        canvas.restore()
        return bmp
    }
}
