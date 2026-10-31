// "Demo source": plays freely licensed test streams, so playback can be built and tested without any other source.
// It's temporary and never released (ADR 0026). Install it from a local link in Settings > Sources.
plugins {
    id("neo.android.source")
}

// Built against this repo's API, so it declares the API's current version.
val apiVersion =
    Regex("""const val VERSION: Int = (\d+)""")
        .find(rootProject.file("extension-api/src/commonMain/kotlin/ani/saikou/neo/extension/api/ExtensionApi.kt").readText())
        ?.groupValues
        ?.get(1)
        ?: error("ExtensionApi.VERSION not found")

android {
    namespace = "ani.saikou.neo.demo"

    defaultConfig {
        applicationId = "ani.saikou.neo.demo"
        versionName = "1.0.0"
        manifestPlaceholders["apiVersion"] = apiVersion.toInt()
    }
}

dependencies {
    testImplementation(project(":extension-api"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
