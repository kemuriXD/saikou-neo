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
        assertEquals(14.sp, seen.labelSize)
        assertEquals(FontWeight.Medium, seen.labelWeight)
        assertEquals(48.dp, seen.safeX)
    }

    private data class Seen(
        val ink: androidx.compose.ui.graphics.Color,
        val background: androidx.compose.ui.graphics.Color,
        val labelSize: androidx.compose.ui.unit.TextUnit,
        val labelWeight: FontWeight?,
        val safeX: androidx.compose.ui.unit.Dp,
    )
}
