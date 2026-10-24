// The interfaces extensions implement. Empty until the extension work starts.
plugins {
    id("neo.kmp.library")
}

kotlin {
    android {
        namespace = "ani.saikou.neo.extension.api"
        // Apps that use the API keep it whole when R8 runs.
        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.file("consumer-rules.pro")
        }
    }
}
