// The phone screens: sign in, pairing, your TVs and account. :app opens them on anything that isn't a TV (ADR 0038).
// No extensions or player here, so a phone never loads them.
import java.util.Properties

plugins {
    id("neo.android.library")
}

val localProperties: Properties? =
    providers
        .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull
        ?.let { text -> Properties().apply { load(text.reader()) } }

/**
 * The AniList client ID for a channel's app ID: [property] from local.properties, else [variable], else empty. It isn't
 * a secret (sign-in needs no secret), but each one is tied to one redirect address, and alpha and beta share theirs.
 */
fun aniListClientId(
    property: String,
    variable: String,
): String {
    val id = (localProperties?.getProperty(property) ?: providers.environmentVariable(variable).orNull ?: "").trim()
    require(id.all(Char::isDigit)) { "$property must be AniList's numeric client ID" }
    return id
}

val previewClientId = aniListClientId("anilist.previewClientId", "ANILIST_PREVIEW_CLIENT_ID")
val stableClientId = aniListClientId("anilist.stableClientId", "ANILIST_STABLE_CLIENT_ID")

android {
    namespace = "ani.saikou.neo.phone"
    productFlavors {
        named("alpha") { buildConfigField("String", "ANILIST_CLIENT_ID", "\"$previewClientId\"") }
        named("beta") { buildConfigField("String", "ANILIST_CLIENT_ID", "\"$previewClientId\"") }
        named("stable") { buildConfigField("String", "ANILIST_CLIENT_ID", "\"$stableClientId\"") }
    }
    buildFeatures { buildConfig = true }
    // Robolectric needs the merged resources, fonts included.
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    // Scanning the TV's QR code (ADR 0039). Only the phone screens use the camera.
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.compose)
    implementation(libs.zxing.core)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    // Compose's test rule brings Espresso 3.5, which breaks on the Android 17 Robolectric image.
    testImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}
