plugins {
    id("neo.kmp.library")
    id("neo.apollo")
}

kotlin {
    android {
        namespace = "ani.saikou.neo.shared"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.apollo.runtime)
            implementation(libs.datastore.preferences.core)
            api(libs.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

apollo {
    service("anilist") {
        packageName.set("ani.saikou.neo.shared.catalogue.anilist")
        // Only the catalogue's own models are public; the generated ones stay inside :shared.
        generateAsInternal.set(true)

        // Refresh the schema with ./gradlew :shared:downloadAnilistApolloSchemaFromIntrospection
        introspection {
            endpointUrl.set("https://graphql.anilist.co")
            schemaFile.set(file("src/commonMain/graphql/schema.graphqls"))
        }
    }
}
