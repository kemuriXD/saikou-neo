plugins {
    id("neo.android.app")
}

android {
    namespace = "ani.saikou.neo.tv"
    defaultConfig {
        applicationId = "ani.saikou.neo"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
}
