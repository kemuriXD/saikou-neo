// The app everyone installs: what the UI modules share (NeoAndroid.kt), plus the app's identity, release builds and the
// legal files it carries.
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.HostTestBuilder

plugins {
    id("com.android.application")
}

configureNeoAndroid(extensions.getByType<ApplicationExtension>())

android {
    defaultConfig {
        targetSdk = sdkLevel("android-targetSdk")
        versionCode = 1
        versionName = "0.0.1"
    }

    buildTypes {
        release {
            // Many TV sticks are still 32-bit ARM. Debug builds keep every ABI
            // so they also install on 32-bit x86 emulators.
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
            // R8 drops unused library code and resources, and optimises what's left. On a Fire TV Stick, Compose
            // spends about a third less time a frame with it (#157).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    // Alpha and beta share the .preview ID, so a preview build installs next to stable instead of replacing it.
    productFlavors {
        named("alpha") {
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-alpha"
        }
        named("beta") {
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-beta"
        }
    }
}

// The APK carries the licence, the disclaimer, the privacy policy and the
// third-party notices as assets under legal/.
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
                include("LICENSE", "DISCLAIMER.md", "PRIVACY.md", "THIRD_PARTY_NOTICES.md", "licenses/**")
            },
        )
        outputDir.set(layout.buildDirectory.dir("generated/legal"))
    }

androidComponents {
    // JVM tests run on debug builds only. CI runs them for the active
    // channel (ADR 0008), so every channel keeps its test task.
    beforeVariants { variant ->
        if (variant.buildType != "debug") {
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
        }
    }
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(collectLegalFiles, CollectLegalFiles::outputDir)
    }
}
