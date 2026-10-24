plugins {
    id("neo.android.app")
}

android {
    namespace = "ani.saikou.neo.tv"
    defaultConfig {
        applicationId = "ani.saikou.neo"
    }
    // Robolectric needs the merged resources, fonts included.
    testOptions { unitTests.isIncludeAndroidResources = true }
    // The PNG icons are for TVs on Android 7 (Fire OS 6), where a square TV
    // icon is expected. Phone launcher shapes don't apply.
    lint { disable += "IconLauncherShape" }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    // Uses the OkHttp library that Apollo already brings.
    implementation(libs.coil.network.okhttp)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    // Compose's test rule brings Espresso 3.5, which breaks on the Android 17 Robolectric image.
    testImplementation(libs.espresso.core)
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
