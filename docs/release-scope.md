# First public release scope

The owner accepted the core TV watching experience on 2026-10-17 (D-006,
resolving Q-001). Full feature parity with the previous SaikouTV is not a
requirement for this release. Android TV comes first; Apple TV discovery and
Vega OS implementation remain deferred.

## Standalone app identity

The owner confirmed on 2026-10-17 that Saikou Neo is a completely new app
(D-010). It has its own application identity, local storage and release
lifecycle, and works without SaikouTV installed. It is not an in-place update
of the old app. Choose Neo's exact permanent application ID before creating
the Android project. Every user starts with fresh Neo local data (D-011).
Legacy application data migration and imports are excluded: no automatic
transfer, import wizard, or legacy backup/database/settings/progress importer.

## Required journeys

These are acceptance criteria to implement and verify, not passed test results.
Use deterministic catalogue and media fixtures for repeatable automation;
validate the selected live integrations separately once they are agreed.

| Case | Journey | Observable acceptance criteria |
| --- | --- | --- |
| TV-001 | Browse and search | Navigate lists with a TV remote, search by title, open the intended result, and return with a predictable focus position. Loading, empty, and failed results have usable states. |
| TV-002 | View details | Show the selected title and playable items accurately. Long titles and missing artwork remain legible and navigable. |
| TV-003 | Watch | Start the chosen item; pause, resume, seek, and exit using the remote. Loading, playback failure, and recovery do not trap focus or leave an unusable screen. |
| TV-004 | Subtitles | Select available subtitles and turn them off; the displayed selection matches playback and remains readable. Required formats and styling options depend on the media decision. |
| TV-005 | Resume | Save progress and offer a sensible continuation after leaving playback and reopening the app. Persistence, completion rules, and any account sync need explicit contracts. |
| TV-006 | Personal library | Without signing in, add and remove a title, find it again, and open its details using the remote. The local library persists across app restarts. |
| TV-007 | Optional AniList sync | The core journey works without sign-in. A user may connect AniList for sync; failure or cancellation of that optional connection leaves the local experience usable. Define sync fields, conflict rules, and disconnect behaviour before implementation. |
| TV-008 | Independent installation | Install and launch Neo with its own application ID and local storage on a device without SaikouTV. The core local journey works without the old app; installation does not replace other apps or their data. |
| TV-009 | Fresh local state | First launch starts with Neo defaults and an empty local library/history before any explicit account connection, even when legacy apps or their export files exist. No legacy migration/import flow is offered and no legacy app data is read or modified. |

## Account boundary

The owner accepted local use plus optional AniList sync on 2026-10-17 (D-007).
An account must not be required to enter the app or use the core local journey.
This does not promise a complete offline catalogue or offline video playback.
Catalogue availability and playback still depend on the selected integrations.
AniList authentication, sync fields, conflict resolution, and account disconnect
semantics remain part of Q-004; no authentication mechanism is selected yet.

D-011 excludes legacy application data import. The explicitly chosen AniList
connection in D-007 remains a separate opt-in integration; its sync contract
still needs agreement. Future updates of Neo must preserve Neo's own user data
through supported schema changes. A fresh first launch does not mean resetting
user data on later Neo upgrades.

## Boundaries still to agree

- Catalogue integration, playback sources, authentication/sync contracts, media
  and subtitle formats.
- Supported Android versions and physical TV/Fire TV models.
- Exact permanent application ID, licence, distribution, signing, and final artwork.
- Quantitative performance budgets and release thresholds after the initial
  device baseline and playback proof provide measurements.

Do not add legacy features automatically or treat unresolved items as excluded.
Record additions as explicit scope changes with acceptance criteria. The
regression plan in [testing](testing.md) covers remote focus, Back navigation,
failure recovery, lifecycle changes, accessibility, and physical-device checks
alongside these journeys. Gate 0 remains open until its remaining decisions
and blocking questions are resolved.
