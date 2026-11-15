// A module that draws screens: Compose, and the brand resources the TV and phone draw the same way (the splash
// lockup, its loading line and their colours, ADR 0022). The app module itself draws nothing (ADR 0038).
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<com.android.build.api.dsl.CommonExtension>("android") {
    buildFeatures.compose = true
    sourceSets.named("main") { res.directories.add(rootProject.file("brand/res").path) }
}
