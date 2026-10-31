// "Example source": a neutral test source that has no servers. It's for testing how the app adds, refuses and removes
// sources, and is never published. Each flavour says it was built for a different API version, so both refusals can be
// checked: api0 is too old for the app, api2 too new, and api1 works.
plugins {
    id("neo.android.source")
}

android {
    namespace = "ani.saikou.neo.example"
    defaultConfig { applicationId = "ani.saikou.neo.example" }

    flavorDimensions += "api"
    productFlavors {
        listOf(0 to "0.9.0", 1 to "1.4.0", 2 to "2.0.0").forEach { (api, version) ->
            create("api$api") {
                dimension = "api"
                versionName = version
                manifestPlaceholders["apiVersion"] = api
            }
        }
    }
}
