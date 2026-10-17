# First public release scope

The owner accepted the core TV watching experience on 2026-10-17 (D-006,
resolving Q-001). Full feature parity with the previous SaikouTV is not a
requirement for this release. Android TV comes first; Apple TV discovery and
Vega OS implementation remain deferred.

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
| TV-006 | Personal library | Add and remove a title, find it again, and open its details using the remote. Decide local storage versus account-backed behaviour with Q-004. |

## Boundaries still to agree

- Catalogue, playback sources, authentication, media and subtitle formats.
- Supported Android versions and physical TV/Fire TV models.
- App name/identity, migration, licence, distribution, signing, and final artwork.
- Quantitative performance budgets and release thresholds after the initial
  device baseline and playback proof provide measurements.

Do not add legacy features automatically or treat unresolved items as excluded.
Record additions as explicit scope changes with acceptance criteria. The
regression plan in [testing](testing.md) covers remote focus, Back navigation,
failure recovery, lifecycle changes, accessibility, and physical-device checks
alongside these journeys. Gate 0 remains open until its remaining decisions
and blocking questions are resolved.
