// Shared setup for Kotlin Multiplatform library modules. Android is the only
// target for now; Apple targets come later.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("com.android.lint")
}

val libs = the<VersionCatalogsExtension>().named("libs")

fun sdkLevel(name: String): Int =
    libs
        .findVersion(name)
        .get()
        .requiredVersion
        .toInt()

kotlin {
    explicitApi()
    compilerOptions { allWarningsAsErrors.set(true) }

    android {
        compileSdk = sdkLevel("android-compileSdk")
        minSdk = sdkLevel("android-minSdk")
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}
    }

    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

// The KMP Android plugin only runs lint when com.android.lint is applied.
lint {
    abortOnError = true
    warningsAsErrors = true
    // New releases would fail unchanged code; Dependabot handles updates.
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
}
