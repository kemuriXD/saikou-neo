import java.util.Properties

plugins {
    id("neo.android.app")
}

val localProperties: Properties? =
    providers
        .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull
        ?.let { text -> Properties().apply { load(text.reader()) } }

/** [property] from local.properties, else the [variable] that CI sets from a secret, else empty. */
fun apiKey(
    property: String,
    variable: String,
): String = (localProperties?.getProperty(property) ?: providers.environmentVariable(variable).orNull ?: "").trim()

// The fanart.tv project key (ADR 0011) and the TMDB API key (ADR 0012). Without one, the app asks that site nothing.
val fanartTvApiKey = apiKey("fanartTv.apiKey", "FANART_TV_API_KEY")
val tmdbApiKey = apiKey("tmdb.apiKey", "TMDB_API_KEY")

android {
    namespace = "ani.saikou.neo.tv"
    defaultConfig {
        applicationId = "ani.saikou.neo"
        buildConfigField("String", "FANART_TV_API_KEY", "\"$fanartTvApiKey\"")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
    }
    buildFeatures { buildConfig = true }
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
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
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
