package ani.saikou.neo.tv.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ShowTitleTest {
    @Test
    fun aWideLogoIsDrawnAsWideAsTheBox() {
        assertEquals(DpSize(360.dp, 37.dp), logoSize(3600, 370))
    }

    @Test
    fun aTallLogoIsDrawnAsTallAsTheBox() {
        assertEquals(DpSize(64.dp, 80.dp), logoSize(600, 750))
    }

    @Test
    fun aLogoWithoutASizeKeepsTheBox() {
        assertEquals(DpSize(360.dp, 80.dp), logoSize(0, 0))
    }

    // Preloading and showing ask for the same size, so the preloaded copy is in memory when the logo is shown.
    @Test
    fun logosLoadAtTheBoxsSizeOnScreen() {
        val request = logoRequest(ApplicationProvider.getApplicationContext(), "https://logos.test/logo.png")

        // An xhdpi TV has two pixels to the dp.
        assertEquals(Size(720, 160), runBlocking { request.sizeResolver.size() })
    }
}
