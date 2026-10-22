package app.gloss.screens

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gloss.ui.components.LiquidLens
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The liquid glass shader compiles. Phones compile it at runtime and fall back to frosted glass
 * if it doesn't, so a typo would quietly lose the effect rather than crash; this catches it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LiquidLensTest {
    /**
     * Robolectric's shader compiler is an older Skia that spells `content.eval(p)` as
     * `sample(content, p)`; phones take the AGSL as written.
     */
    private val source = LiquidLens.SOURCE.replace(Regex("""(\w+)\.eval\("""), "sample($1, ")

    @Test fun shaderCompiles() {
        val error = runCatching { RuntimeShader(source) }.exceptionOrNull()
        // Robolectric may not ship the shader compiler at all; only a compile error counts.
        assumeTrue("RuntimeShader isn't available here: $error", error == null || error is IllegalArgumentException)
        if (error != null) throw AssertionError("The lens shader doesn't compile: ${error.message}", error)
        println("LiquidLens shader compiled")
    }

    /**
     * The lens on its own (no blur or tint from the real pipeline): a capsule and a circle over
     * coloured stripes and big type, so the bend at the rim and the colour split are visible.
     */
    @Test fun lensBendsTheRim() {
        val w = 1080
        val h = 420
        val scene = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(scene).apply {
            val stripes = intArrayOf(0xFF2338C2.toInt(), 0xFFE1A91A.toInt(), 0xFFC23152.toInt(), 0xFF1E8F5E.toInt(), 0xFFF2F3F8.toInt())
            var x = 0f
            var i = 0
            while (x < w) { drawRect(x, 0f, x + 36f, h.toFloat(), Paint().apply { color = stripes[i++ % stripes.size] }); x += 36f }
            drawText("GLOSS 1994", 40f, 300f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 210f; isFakeBoldText = true })
        }
        val out = scene.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        fun pane(left: Float, top: Float, pw: Float, ph: Float) {
            val shader = RuntimeShader(source)
            shader.setFloatUniform("size", pw, ph)
            shader.setFloatUniform("radius", minOf(pw, ph) / 2f)
            shader.setFloatUniform("depth", minOf(48f, minOf(pw, ph) / 2f))
            shader.setFloatUniform("bend", 42f)
            shader.setFloatUniform("spread", 0.12f)
            shader.setInputShader("content", BitmapShader(scene, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setTranslate(-left, -top) })
            })
            val clip = Path().apply { addRoundRect(RectF(0f, 0f, pw, ph), minOf(pw, ph) / 2f, minOf(pw, ph) / 2f, Path.Direction.CW) }
            canvas.save()
            canvas.translate(left, top)
            canvas.clipPath(clip)
            canvas.drawRect(0f, 0f, pw, ph, Paint().apply { this.shader = shader })
            canvas.drawPath(clip, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) })
            canvas.drawPath(clip, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.argb(200, 255, 255, 255) })
            canvas.restore()
        }
        pane(40f, 40f, 760f, 186f)
        pane(840f, 40f, 186f, 186f)
        pane(40f, 260f, 1000f, 130f)
        File("build/outputs/roborazzi").apply { mkdirs() }.resolve("liquid_lens.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
