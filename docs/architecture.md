# Architecture

Saikou Neo is two Android apps on one shared Kotlin Multiplatform core. Video
sources stay outside the app, in separately installed extensions.

```text
 app-companion (phone)  --pairing-->  app-tv  <--streams--  source extensions
        |                               |                    (separate APKs)
        +---------------+---------------+                          |
                        v                                          v
                 :shared (KMP core)                         :extension-api
                        |
                        v
                AniList, AniZip, ...
```

## Modules

| Module | What lives there |
| --- | --- |
| `:shared` | Everything that isn't a screen: API clients, storage, ViewModels, tracking, matching, pairing protocol, playback state. Android target now; Apple targets later. |
| `:app-tv` | Compose for TV screens, the Media3 player, the pairing receiver, extension loading. |
| `:app-companion` | Compose phone screens: AniList sign-in, QR scanning, pairing sender. |
| `:extension-api` | The interfaces extensions implement, plus their data types. Published so private extension repos can build against it. |
| `build-logic` | Shared Gradle convention plugins. |

Features are packages inside `:shared` (catalogue, accounts, tracking, sources,
pairing, playback), not separate Gradle modules. We split modules only when
build times or boundaries make it worth it.

## Dependency direction

- Apps depend on `:shared`. `:shared` never depends on an app.
- `:app-tv` depends on `:extension-api`. `:shared` doesn't, because extensions
  are an Android, TV-side concern for now (see ADR 0003).
- Extensions depend only on `:extension-api`.

## How playing an episode flows

1. The catalogue (AniList, with episode details from AniZip) provides the anime
   and its episode list. Extensions are not involved yet.
2. When you choose an episode, the TV app asks each enabled extension for
   servers for that anime and episode number.
3. You pick a server. The extension returns its streams: qualities, audio
   tracks, subtitles and the request headers they need.
4. The Media3 player plays the stream, using the extension's headers or its own
   data source for every request.
5. Progress is saved per server, because different servers can have different
   cuts. Once you're signed in, it syncs to AniList.

## Where state lives

- Screen state: a ViewModel per screen in `:shared`, exposed as `StateFlow`.
- Cache, watch history and the tracking journal: SQLDelight.
- Settings: DataStore.
- Tokens: encrypted with the Android Keystore.

## Testing

| Layer | How |
| --- | --- |
| `:shared` logic and clients | JVM unit tests with recorded API responses |
| Screens | Compose tests on the JVM with Robolectric |
| Whole app | A few emulator smoke tests, plus the owner's checks on a TV for `needs-tv-test` issues |

## Decisions

See [docs/adr](adr) for the reasons behind each of these choices.
