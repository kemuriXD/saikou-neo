// The phone screens: sign in, pairing, your TVs and account. :app opens them on anything that isn't a TV (ADR 0038).
// No extensions or player here, so a phone never loads them.
plugins {
    id("neo.android.library")
}

android {
    namespace = "ani.saikou.neo.phone"
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
