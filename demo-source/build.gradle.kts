// "Demo source": plays freely licensed test streams, so playback can be built and tested without any other source
// (ADR 0026). Its signed APK is released from the Actions tab as source.apk.
plugins {
    id("neo.android.source")
}

val demoVersion = providers.gradleProperty("demoSource.version").get()

// Android also needs a whole number that goes up with each release: 1.2.3 becomes 10203.
val versionNumber =
    Regex("""(\d{1,2})\.(\d{1,2})\.(\d{1,2})""")
        .matchEntire(demoVersion)
        ?.destructured
        ?.let { (major, minor, patch) -> major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt() }
        ?: error("demoSource.version must be three numbers below 100, such as 1.0.0, not \"$demoVersion\"")

// Built against this repo's API, so it declares the API's current version.
val apiVersion =
    Regex("""const val VERSION: Int = (\d+)""")
        .find(rootProject.file("extension-api/src/commonMain/kotlin/ani/saikou/neo/extension/api/ExtensionApi.kt").readText())
        ?.groupValues
        ?.get(1)
        ?: error("ExtensionApi.VERSION not found")

// The release key, when one is set. The release workflow sets these from repository secrets.
fun secret(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

val keystore = secret("SIGNING_STORE_FILE")

android {
    namespace = "ani.saikou.neo.demo"

    defaultConfig {
        applicationId = "ani.saikou.neo.demo"
        versionCode = versionNumber
        versionName = demoVersion
        manifestPlaceholders["apiVersion"] = apiVersion.toInt()
    }

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = secret("SIGNING_STORE_PASSWORD")
                // keytool's default store keeps the key under the same password.
                keyAlias = secret("SIGNING_KEY_ALIAS") ?: "source"
                keyPassword = secret("SIGNING_KEY_PASSWORD") ?: storePassword
            }
        }
    }

    buildTypes {
        release {
            // The app finds the source's class by name, so R8 would need a keep rule for it.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    testImplementation(project(":extension-api"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
