// The interfaces extensions implement. It's a public contract, so the main
// code stays plain Kotlin with no dependencies (ADR 0003).
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("neo.kmp.library")
    id("neo.publish")
}

kotlin {
    // Sources outside this repo may use an older Kotlin, such as the one built into AGP. Kotlin reads classes up to
    // one version newer than itself, so this and the matching standard library let Kotlin 2.1 and later build
    // against the API. Apps that load sources still use their own, newer standard library.
    coreLibrariesVersion = "2.2.0"
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
    }

    android {
        namespace = "ani.saikou.neo.extension.api"
        // Apps that load sources keep this API's names in release builds.
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Sources outside this repo build against the published API. Its version is ExtensionApi.VERSION, released from a
// git tag named api-<VERSION>.
val apiVersion =
    Regex("""const val VERSION: Int = (\d+)""")
        .find(file("src/commonMain/kotlin/ani/saikou/neo/extension/api/ExtensionApi.kt").readText())
        ?.groupValues
        ?.get(1)
        ?: error("ExtensionApi.VERSION not found")

// The release workflow passes the tag, so a tag that doesn't match VERSION fails before anything is uploaded.
providers.gradleProperty("apiTag").orNull?.let { tag ->
    check(tag == "api-$apiVersion") { "Tag $tag doesn't match ExtensionApi.VERSION $apiVersion. Tag api-$apiVersion." }
}

mavenPublishing {
    coordinates("io.github.kemurixd", "saikou-neo-extension-api", apiVersion)
    pom {
        name.set("Saikou Neo extension API")
        description.set("The interfaces a Saikou Neo source implements. Sources use it as compileOnly.")
    }
}
