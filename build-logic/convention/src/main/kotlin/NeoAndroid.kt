import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** The version catalogue's [name] SDK level. */
internal fun Project.sdkLevel(name: String): Int =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion(name)
        .get()
        .requiredVersion
        .toInt()

/**
 * What the app and its UI modules share: SDK levels, the release channels, and warnings as errors for
 * both Kotlin and lint. Each module gets the same channels, so the app picks the matching UI variant.
 */
internal fun Project.configureNeoAndroid(android: CommonExtension) {
    android.compileSdk = sdkLevel("android-compileSdk")
    android.defaultConfig.minSdk = sdkLevel("android-minSdk")

    // Release channels (ADR 0005).
    android.flavorDimensions += "channel"
    listOf("alpha", "beta", "stable").forEach { channel ->
        android.productFlavors.create(channel) { dimension = "channel" }
    }
    // Preview resources (the app name) and tests are shared by alpha and beta.
    listOf("alpha", "beta").forEach { channel ->
        android.sourceSets.named(channel) { res.directories.add("src/preview/res") }
        android.sourceSets.named("test${channel.replaceFirstChar(Char::uppercase)}") {
            kotlin.directories.add("src/testPreview/kotlin")
        }
    }

    android.testOptions.unitTests.all {
        // Robolectric's Android 17 image reaches into jdk.internal.access.
        it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
    }

    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    android.compileOptions.targetCompatibility = JavaVersion.VERSION_17

    android.lint.abortOnError = true
    android.lint.warningsAsErrors = true
    // New releases would fail unchanged code; Dependabot handles updates.
    android.lint.disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")

    extensions.getByType<KotlinAndroidProjectExtension>().compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
