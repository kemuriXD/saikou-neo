plugins {
    id("neo.android.app")
    // Until #375 turns this into the :ui-phone module, it builds its own Compose screens.
    id("neo.android.compose")
}

android {
    namespace = "ani.saikou.neo.companion"
    defaultConfig {
        applicationId = "ani.saikou.neo.companion"
    }
    // Robolectric needs the merged resources and assets.
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
}
