// The interfaces extensions implement. It's a public contract, so the main
// code stays plain Kotlin with no dependencies (ADR 0003).
plugins {
    id("neo.kmp.library")
}

kotlin {
    android {
        namespace = "ani.saikou.neo.extension.api"
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
