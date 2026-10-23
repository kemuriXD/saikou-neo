# 0001. Two apps on one shared core

Status: Accepted
Date: 2026-10-23

## Context

The legacy app was one APK that checked at startup whether it was on a TV or a
phone. TV and phone code ended up mixed in the same files, every device shipped
the other's permissions (the TV app asked for the camera), and almost all logic
lived in the Android app, so none of it could be reused for Apple platforms.

## Decision

- Build two apps: `:app-tv` (`ani.saikou.neo`) and `:app-companion`
  (`ani.saikou.neo.companion`).
- Put everything that isn't UI in `:shared`, a Kotlin Multiplatform module with
  only an Android target for now.
- Keep minSdk 24 and include 32-bit ARM builds, since many TV sticks need them.

## Consequences

- Each app is smaller and only requests the permissions it needs.
- The two codebases can't tangle. Shared behaviour has to go through `:shared`.
- Two APKs to build and release. CI builds both on every PR.
- iPhone and Apple TV apps can later reuse `:shared`. That work is deferred.
