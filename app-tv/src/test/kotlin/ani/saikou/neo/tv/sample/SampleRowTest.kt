package ani.saikou.neo.tv.sample

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
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
}
