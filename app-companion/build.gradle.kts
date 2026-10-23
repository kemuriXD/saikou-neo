plugins {
    id("neo.android.app")
}

android {
    namespace = "ani.saikou.neo.companion"
    defaultConfig {
        applicationId = "ani.saikou.neo.companion"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
}
