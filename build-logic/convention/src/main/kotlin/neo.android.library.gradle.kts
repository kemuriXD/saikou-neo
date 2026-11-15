// A UI module the app is built from, such as :ui-tv. It shares the app's setup (NeoAndroid.kt) and channels.
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.HostTestBuilder

plugins {
    id("com.android.library")
    id("neo.android.compose")
}

configureNeoAndroid(extensions.getByType<LibraryExtension>())

androidComponents {
    // JVM tests run on debug builds only, as in the app.
    beforeVariants { variant ->
        if (variant.buildType != "debug") {
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
        }
    }
}
