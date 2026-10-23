// Shared setup for the TV and companion apps: SDK levels, ABIs, Compose,
// and warnings as errors for both Kotlin and lint.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
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

    testOptions {
        unitTests.all {
            // Robolectric's Android 17 image reaches into jdk.internal.access.
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        // New releases would fail unchanged code; Dependabot handles updates.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

// Both APKs carry the licence, the extension permission and the third-party
// notices as assets under legal/.
abstract class CollectLegalFiles : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val legalFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun collect() {
        fileSystem.sync {
            from(legalFiles)
            into(outputDir.dir("legal"))
        }
    }
}

val collectLegalFiles =
    tasks.register<CollectLegalFiles>("collectLegalFiles") {
        legalFiles.from(
            rootProject.fileTree(rootDir) {
                include("LICENSE", "EXTENSION-LINKING-PERMISSION.md", "THIRD_PARTY_NOTICES.md", "licenses/**")
            },
        )
        outputDir.set(layout.buildDirectory.dir("generated/legal"))
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(collectLegalFiles, CollectLegalFiles::outputDir)
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
