package app.gloss.screens

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
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
     * The lens drawn by the GPU pipeline over coloured stripes and big type, with the tint and
     * rim on top, so the bend at the rim and the colour split show.
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
        val root = RenderNode("root").apply { setPosition(0, 0, w, h) }
        val canvas = root.beginRecording()
        canvas.drawBitmap(scene, 0f, 0f, null)
        fun pane(left: Int, top: Int, pw: Int, ph: Int) {
            val r = minOf(pw, ph) / 2f
            val shader = RuntimeShader(source)
            shader.setFloatUniform("size", pw.toFloat(), ph.toFloat())
            shader.setFloatUniform("radius", r)
            shader.setFloatUniform("depth", minOf(48f, r))
            shader.setFloatUniform("bend", 42f)
            shader.setFloatUniform("spread", 0.12f)
            // Robolectric can't run a shader as a RenderEffect, so the lens paints the scene
            // directly here, with the same saturation lift and no blur.
            shader.setInputShader("content", BitmapShader(scene, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setTranslate(-left.toFloat(), -top.toFloat()) })
            })
            val lens = Paint().apply {
                this.shader = shader
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) })
            }
            val clip = Path().apply { addRoundRect(RectF(left.toFloat(), top.toFloat(), (left + pw).toFloat(), (top + ph).toFloat()), r, r, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            canvas.save()
            canvas.translate(left.toFloat(), top.toFloat())
            canvas.drawRect(0f, 0f, pw.toFloat(), ph.toFloat(), lens)
            canvas.restore()
            canvas.drawPath(clip, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 255, 255, 255) })
            canvas.drawPath(clip, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.argb(220, 255, 255, 255) })
            canvas.restore()
        }
        pane(40, 40, 760, 186)
        pane(840, 40, 186, 186)
        pane(40, 260, 1000, 130)
        root.endRecording()

        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 1, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        val renderer = HardwareRenderer().apply { setSurface(reader.surface); setContentRoot(root) }
        renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        val image = reader.acquireNextImage()
        val out = Bitmap.wrapHardwareBuffer(image.hardwareBuffer!!, ColorSpace.get(ColorSpace.Named.SRGB))!!.copy(Bitmap.Config.ARGB_8888, false)
        image.close(); renderer.destroy(); reader.close()
        File("build/outputs/roborazzi").apply { mkdirs() }.resolve("liquid_lens.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
