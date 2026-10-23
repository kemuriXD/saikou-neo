# 0002. Shared-core stack

Status: Accepted
Date: 2026-10-23

## Context

The legacy app hand-wrote HTTP calls with `HttpURLConnection` in four places,
parsed JSON by hand with `org.json` (about 260 call sites), stored caches and
whole libraries in SharedPreferences, and kept screen state in controllers
created inside composables with no ViewModels. That caused main-thread disk
writes and fragile lifecycle handling, and it all lived in Android-only code.

## Decision

| Concern | Choice |
| --- | --- |
| UI | Jetpack Compose; Compose for TV in the TV app |
| Screen state | AndroidX ViewModel with `StateFlow`, in `:shared` |
| AniList | Apollo Kotlin, with queries checked against the schema at build time |
| Other HTTP | Ktor client with kotlinx.serialization |
| Storage | SQLDelight for cache, history and tracking; DataStore for settings |
| Dependency wiring | Koin |
| Tokens | Android Keystore encryption (iOS Keychain later) |
| Player | Media3 ExoPlayer |
| Tests | kotlin.test and Turbine on the JVM; Robolectric for Compose; a few emulator smoke tests |
| Formatting | ktlint through Spotless; warnings as errors |

## Consequences

- All of these run in Kotlin Multiplatform code, so the core stays portable.
- Most tests run on the JVM in seconds, without a device.
- More dependencies than the legacy app. Dependabot keeps them updated.
- Apollo adds a code-generation step. In return, a broken AniList query fails
  the build instead of failing at runtime.
