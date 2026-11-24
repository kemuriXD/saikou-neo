// Firebase goes into the app only when app/google-services.json exists. The file is never committed: CI writes it from
// a secret, and builds without it (forks, fresh clones) carry no Firebase code at all. Either way the app compiles one
// newCrashReporter: src/firebase/kotlin or src/noFirebase/kotlin.
import com.android.build.api.dsl.ApplicationExtension

val libs = the<VersionCatalogsExtension>().named("libs")
val hasFirebase = project.file("google-services.json").exists()

pluginManager.withPlugin("com.android.application") {
    extensions.getByType<ApplicationExtension>().sourceSets.named("main") {
        kotlin.directories.add(if (hasFirebase) "src/firebase/kotlin" else "src/noFirebase/kotlin")
    }
}

if (hasFirebase) {
    pluginManager.apply("com.google.gms.google-services")
    pluginManager.apply("com.google.firebase.crashlytics")

    dependencies {
        "implementation"(platform(libs.findLibrary("firebase-bom").get()))
        "implementation"(libs.findLibrary("firebase-crashlytics").get())
        // Reports native crashes as well.
        "implementation"(libs.findLibrary("firebase-crashlytics-ndk").get())
        // Other Firebase libraries (Firestore, Auth) go here, so they're also left out without the file.
    }
}
