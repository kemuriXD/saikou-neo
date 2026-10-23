// Shared setup for the TV and companion apps: SDK levels, ABIs, Compose,
// and warnings as errors for both Kotlin and lint.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = the<VersionCatalogsExtension>().named("libs")

fun sdkLevel(name: String): Int = libs.findVersion(name).get().requiredVersion.toInt()

android {
    compileSdk = sdkLevel("android-compileSdk")

    defaultConfig {
        minSdk = sdkLevel("android-minSdk")
        targetSdk = sdkLevel("android-targetSdk")
        versionCode = 1
        versionName = "0.0.1"
    }

    buildTypes {
        release {
            // Many TV sticks are still 32-bit ARM. Debug builds keep every ABI
            // so they also install on 32-bit x86 emulators.
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
