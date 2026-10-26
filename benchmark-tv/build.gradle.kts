// Generates the TV app's baseline profile and measures its startup and row
// scrolling on a connected TV or emulator.
plugins {
    id("neo.android.benchmark")
}

android {
    namespace = "ani.saikou.neo.tv.benchmark"
    targetProjectPath = ":app-tv"

    // Measures the alpha channel, which is what the test TV has installed.
    flavorDimensions += "channel"
    productFlavors { create("alpha") { dimension = "channel" } }
}
