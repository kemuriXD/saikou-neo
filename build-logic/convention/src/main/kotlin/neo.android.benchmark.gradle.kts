// Shared setup for Macrobenchmark modules, which generate baseline profiles
// and measure an app on a connected device. They never run in CI.
import androidx.baselineprofile.gradle.producer.BaselineProfileProducerExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
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
        // Macrobenchmark needs Android 9, which is what Fire OS 7 is built on.
        minSdk = 28
        targetSdk = sdkLevel("android-targetSdk")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

configure<BaselineProfileProducerExtension> { useConnectedDevices = true }

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    "implementation"(libs.findLibrary("androidx-test-junit").get())
    "implementation"(libs.findLibrary("uiautomator").get())
    "implementation"(libs.findLibrary("benchmark-macro-junit4").get())
}
