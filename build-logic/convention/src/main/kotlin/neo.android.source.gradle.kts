// Shared setup for source APKs built in this repo. The app loads them in-process (ADR 0003), so they build against
// :extension-api without carrying it.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

val libs = the<VersionCatalogsExtension>().named("libs")

fun sdkLevel(name: String): Int =
    libs
        .findVersion(name)
        .get()
        .requiredVersion
        .toInt()

android {
    compileSdk = sdkLevel("android-compileSdk")

    defaultConfig {
        minSdk = sdkLevel("android-minSdk")
        targetSdk = sdkLevel("android-targetSdk")
        versionCode = 1
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // The app provides the API when it loads the source.
    "compileOnly"(project(":extension-api"))
}
