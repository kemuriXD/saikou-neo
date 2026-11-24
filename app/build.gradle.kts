// The one app for TV and phone (ADR 0038). It holds the app's identity and launcher entry; the screens live in the UI
// modules.
import androidx.baselineprofile.gradle.consumer.BaselineProfileConsumerExtension

plugins {
    id("neo.android.app")
    id("neo.baselineprofile")
    // Crashlytics, only when app/google-services.json exists.
    id("neo.firebase")
}

android {
    namespace = "ani.saikou.neo"
    defaultConfig {
        applicationId = "ani.saikou.neo"
    }
    // The debug-only switch that forces TV or phone screens reads BuildConfig.DEBUG.
    buildFeatures { buildConfig = true }
    // Robolectric needs the merged resources and assets.
    testOptions { unitTests.isIncludeAndroidResources = true }
    // The PNG icons are for TVs on Android 7 (Fire OS 6), where a square TV
    // icon is expected. Phone launcher shapes don't apply.
    lint { disable += "IconLauncherShape" }
}

// :benchmark-tv records the profile on the alpha build. It goes into src/main, so beta and stable ship the same one.
configure<BaselineProfileConsumerExtension> {
    variants.create("alpha") { from(project(":benchmark-tv")) }
}

dependencies {
    // The two UIs. They never depend on each other; LaunchActivity picks one at launch.
    implementation(project(":ui-tv"))
    implementation(project(":ui-phone"))
    // The crash reporter interface.
    implementation(project(":shared"))

    // LaunchActivityTest opens the phone screens, which are a ComponentActivity.
    testImplementation(libs.activity.compose)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    // Watches debug builds for memory leaks; Fire TV sticks have little memory (#244).
    debugImplementation(libs.leakcanary)
}
