package ani.saikou.neo.shared.sources

import com.apollographql.apollo.network.http.DefaultHttpEngine
import com.apollographql.apollo.network.http.HttpEngine
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal actual fun noRedirectsEngine(timeoutMillis: Long): HttpEngine =
    DefaultHttpEngine {
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .build()
    }
