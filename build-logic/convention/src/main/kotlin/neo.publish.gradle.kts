// Publishing to Maven Central, for modules that code outside this repo builds against. The release workflow provides
// the Central login and signing key as secrets; none are in the repo.
plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()
    // Only CI has the key. Without it, publishToMavenLocal still works for testing.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()

    pom {
        inceptionYear.set("2026")
        url.set("https://github.com/kemuriXD/saikou-neo")
        licenses {
            license {
                name.set("GPL-3.0-only")
                url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("kemuriXD")
                name.set("kemuri")
                url.set("https://github.com/kemuriXD")
            }
        }
        scm {
            url.set("https://github.com/kemuriXD/saikou-neo")
            connection.set("scm:git:https://github.com/kemuriXD/saikou-neo.git")
            developerConnection.set("scm:git:ssh://git@github.com/kemuriXD/saikou-neo.git")
        }
    }
}
