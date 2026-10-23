// Android and Kotlin plugins come in through the convention plugins in build-logic.
plugins {
    // Gives the root project a check task, so `./gradlew build` runs spotlessCheck.
    base
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()

    // Excluded folders are skipped entirely, so Spotless never walks into
    // build output that other tasks are writing at the same time.
    fun sources(pattern: String) =
        fileTree(rootDir) {
            include(pattern)
            exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", "tmp/**")
        }
    kotlin {
        target(sources("**/*.kt"))
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target(sources("**/*.gradle.kts"))
        ktlint(ktlintVersion)
    }
}

allprojects {
    configurations.configureEach {
        resolutionStrategy {
            failOnDynamicVersions()
            failOnChangingVersions()
        }
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

tasks.wrapper {
    gradleVersion = "9.7.1"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"
}
