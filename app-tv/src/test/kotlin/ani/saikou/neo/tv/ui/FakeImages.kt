package ani.saikou.neo.tv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import kotlinx.coroutines.awaitCancellation

/**
 * Every image loads at once as a [width] × [height] bitmap, clear unless it's filled with [color]. Interceptors answer
 * before transformations, so logos aren't trimmed. Call [forgetFakeImages] after each test.
 */
internal fun fakeImages(
    context: Context,
    width: Int,
    height: Int,
    color: Int = Color.TRANSPARENT,
) = useImages(context) { chain ->
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    SuccessResult(bitmap.asImage(), chain.request, DataSource.MEMORY)
}

/** No image ever arrives. */
internal fun fakeImagesNeverArrive(context: Context) = useImages(context) { awaitCancellation() }

@OptIn(DelicateCoilApi::class)
internal fun forgetFakeImages() = SingletonImageLoader.reset()

@OptIn(DelicateCoilApi::class)
private fun useImages(
    context: Context,
    answer: suspend (Interceptor.Chain) -> ImageResult,
) {
    val interceptor =
        object : Interceptor {
            override suspend fun intercept(chain: Interceptor.Chain) = answer(chain)
        }
    SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(interceptor) }.build())
}
