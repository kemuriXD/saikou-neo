# Third-party notices

Saikou Neo's own code is licensed under [GPL-3.0-only](LICENSE). Everything
listed here keeps its own licence.

Both apps carry these files, and the full licence texts under `licenses/`, in
their APK assets under `legal/`.

## Libraries in the apps

| Library | Authors | Licence |
| --- | --- | --- |
| AndroidX: Activity, Compose, Compose for TV, Core, Lifecycle and their AndroidX dependencies | The Android Open Source Project | [Apache-2.0](licenses/Apache-2.0.txt) |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization and atomicfu | JetBrains and contributors | [Apache-2.0](licenses/Apache-2.0.txt) |
| JetBrains annotations | JetBrains | [Apache-2.0](licenses/Apache-2.0.txt) |
| OkHttp and Okio | Square, Inc. | [Apache-2.0](licenses/Apache-2.0.txt) |
| Guava ListenableFuture | The Guava authors | [Apache-2.0](licenses/Apache-2.0.txt) |
| JSpecify annotations | The JSpecify authors | [Apache-2.0](licenses/Apache-2.0.txt) |
| Apollo Kotlin | Apollo Graph, Inc. | [MIT](licenses/Apollo-Kotlin-MIT.txt) |
| uuid | Ben Asher | [MIT](licenses/uuid-MIT.txt) |

## Fonts

| Font | Authors | Licence |
| --- | --- | --- |
| Inter | The Inter Project Authors | [SIL OFL 1.1](licenses/fonts/Inter-OFL.txt) |
| Space Grotesk | The Space Grotesk Project Authors | [SIL OFL 1.1](licenses/fonts/SpaceGrotesk-OFL.txt) |

The TV app bundles both fonts cut down to Latin characters, under their
original names. Neither licence reserves a font name.

The TV banner draws "Saikou Neo" in Space Grotesk as outlines. No font file is
added for it.

## Artwork

The Signal icon and TV banner are original Saikou Neo artwork. They are covered
by the project licence. Preview builds use the same artwork in green. No anime artwork, third-party logos or stock images are
used.

## Build tools

The Gradle wrapper (`gradlew`, `gradlew.bat` and `gradle/wrapper/`) is from the
Gradle project under Apache-2.0. Build and test tools such as JUnit and
Robolectric are not shipped in the apps.

## When dependencies change

Adding or upgrading a runtime library means checking its licence and updating
this file in the same pull request.
