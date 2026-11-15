package ani.saikou.neo.tv.theme

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NeoThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun handsItsTokensToTheContent() {
        lateinit var seen: Seen
        compose.setContent {
            NeoTheme {
                seen =
                    Seen(
                        ink = NeoTheme.colors.ink,
                        background = MaterialTheme.colorScheme.background,
                        labelSize = NeoTheme.typography.label.fontSize,
                        labelWeight = NeoTheme.typography.label.fontWeight,
                        safeX = NeoTheme.dimensions.safeX,
                    )
            }
        }

        assertEquals(NeoColors().ink, seen.ink)
        assertEquals(seen.ink, seen.background)
        assertEquals(15.sp, seen.labelSize)
        assertEquals(FontWeight.Medium, seen.labelWeight)
        assertEquals(48.dp, seen.safeX)
    }

    // Slow TV mode: no glow around what's focused, and art and screens swap without fading.
    @Test
    fun theLightLookDropsTheGlowAndTheFades() {
        lateinit var light: NeoDimensions
        compose.setContent { NeoTheme(light = true) { light = NeoTheme.dimensions } }

        assertEquals(0.dp, light.glowFocus)
        assertEquals(0, light.durationBackdropMillis)
        assertEquals(0, light.durationPanelMillis)
        assertEquals(NeoDimensions().focusScale, light.focusScale, 0f)
    }

    @Test
    fun panelsSlideInQuickly() {
        assertEquals(250, NeoDimensions().durationPanelMillis)
    }

    @Test
    fun theFullLookIsTheDefault() {
        lateinit var full: NeoDimensions
        compose.setContent { NeoTheme { full = NeoTheme.dimensions } }

        assertEquals(NeoDimensions(), full)
    }

    private data class Seen(
        val ink: androidx.compose.ui.graphics.Color,
        val background: androidx.compose.ui.graphics.Color,
        val labelSize: androidx.compose.ui.unit.TextUnit,
        val labelWeight: FontWeight?,
        val safeX: androidx.compose.ui.unit.Dp,
    )
}
