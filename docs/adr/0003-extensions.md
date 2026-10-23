# 0003. Playback-only extensions, loaded in-process

Status: Accepted
Date: 2026-10-23

## Context

The app ships with no video sources, like Stremio. The legacy app ran each
extension as a separate-process service and talked to it with JSON over
`Messenger`. That brought a 450 KB message cap, a rebind on every request, and
extensions being disabled after an update because the trust check included the
version code.

When extensions did their own searching, results were inconsistent: each site
names and numbers shows differently. The catalogue should decide what you're
watching.

Echo ([brahmkshatriya/echo](https://github.com/brahmkshatriya/echo)) uses a
simpler model that works well.

## Decision

- **Playback only.** The app's catalogue (AniList and AniZip) picks the anime and
  episode. An extension receives that anime's IDs, titles, season, year and
  episode number. It returns servers, then streams. How it finds the right page
  on its own site is its business.
- **Echo's loading model.** `:extension-api` is a small Kotlin library of
  interfaces and data types. An extension is an APK that implements them and
  names its entry class in its manifest. The app loads it in-process with a class
  loader. Users install an APK or import a file. Each extension declares an
  update URL.
- **What the API carries:**
  - servers, each with its own edition (Sub, Dub or hard-subbed) and runtime
  - the full quality list for each stream, with no "Auto"
  - separate audio tracks and subtitle tracks
  - request headers for every playlist, segment, key and subtitle request
  - an optional extension-provided media data source
  - a way to refresh an expired stream without losing your place
  - typed errors: blocked, rate-limited, source down, link expired, not found
- **Patience.** Resolving a server can take around 25 seconds, so the chooser
  shows progress and allows cancel rather than timing out early.
- **Who builds what.** This repo contains the app, `:extension-api` and a demo
  extension that plays public test streams. Source extensions for streaming
  sites are written by the owner in separate private repos. No Aniyomi
  compatibility.

## Consequences

- Much less plumbing: no IPC, no size limits, and streams go straight to the player.
- No sandbox. An extension runs with the app's permissions and could read
  anything the app can, including the AniList token. Only install extensions
  you trust.
- Android only. iOS and tvOS don't allow loading code at runtime, so Apple apps
  will need another way to get sources. `:extension-api` stays plain Kotlin to
  keep that option open.
- `:extension-api` becomes a public contract. Breaking changes need a version
  bump, and the app has to refuse extensions built for an incompatible version.
