package app.gloss.screens

import android.graphics.RuntimeShader
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gloss.ui.components.LiquidLens
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The liquid glass shader compiles. Phones compile it at runtime and fall back to frosted glass
 * if it doesn't, so a typo would quietly lose the effect rather than crash; this catches it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LiquidLensTest {
    @Test fun shaderCompiles() {
        val error = runCatching { RuntimeShader(LiquidLens.SOURCE) }.exceptionOrNull()
        // Robolectric may not ship the shader compiler at all; only a compile error counts.
        assumeTrue("RuntimeShader isn't available here: $error", error == null || error is IllegalArgumentException)
        if (error != null) throw AssertionError("The lens shader doesn't compile: ${error.message}", error)
        println("LiquidLens shader compiled")
    }
}
