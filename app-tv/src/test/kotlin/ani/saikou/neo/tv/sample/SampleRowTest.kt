package ani.saikou.neo.tv.sample

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
// A 1080p TV is 960 x 540 dp.
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SampleRowTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun showRow() {
        compose.setContent { NeoTheme { SampleRow() } }
    }

    @Test
    fun startsOnTheFirstCard() {
        compose.onNodeWithTag(sampleCardTag(0)).assertIsFocused()
    }

    @Test
    fun rightMovesToTheNextCard() {
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }

        compose.onNodeWithTag(sampleCardTag(1)).assertIsFocused()
    }

    @Test
    fun leftOnTheFirstCardStaysPut() {
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }

        compose.onNodeWithTag(sampleCardTag(0)).assertIsFocused()
    }

    @Test
    fun focusedCardRestsOnTheLeftKeyline() {
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
        }

        // Card 2 starts at the 48 dp safe margin, so card 1 ends one 16 dp gap before it.
        // Card 1 is unfocused, so its bounds aren't scaled.
        compose.onNodeWithTag(sampleCardTag(2)).assertIsFocused()
        compose.onNodeWithTag(sampleCardTag(1)).assertLeftPositionInRootIsEqualTo((48 - 16 - 208).dp)
    }
}
