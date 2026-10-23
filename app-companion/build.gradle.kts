plugins {
    id("neo.android.app")
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
