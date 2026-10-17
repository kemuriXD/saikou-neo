# Decision register

## Accepted direction

| ID | Decision | Evidence |
| --- | --- | --- |
| D-001 | Fresh private repository: SaikouTV2 | Owner's rebuild and repository setup requests |
| D-002 | Android TV first; verify Android-based Fire TV on hardware | Owner's platform priority |
| D-003 | Defer Apple TV discovery and Vega OS implementation | Owner's sequencing and Firestick investigation |
| D-004 | Finish each stage with test evidence and resolved blockers | Owner's step-by-step and market-readiness requirements |
| D-005 | Create new artwork separately and integrate after handoff review | Owner's OpenDesign work in progress |
| D-006 | First public release focuses on the core TV watching experience: browse/search, details, playback, subtitles, resume, and a personal library | Owner selected core watching experience on 2026-10-17; see [release scope](release-scope.md) |
| D-007 | Support use without sign-in, a local personal library, and optional AniList sync | Owner selected local use plus optional AniList sync on 2026-10-17 |
| D-008 | Research scraped anime streaming entries from Wotaku and EverythingMoe for native playback; create a dedicated source research chat and target about ten qualified candidates | Owner's 2026-10-17 request; provider selection and checker architecture remain open |
| D-009 | User-facing app name: Saikou Neo. Keep the repository and checkout named SaikouTV2; application identity is recorded separately | Owner chose Saikou Neo on 2026-10-17 |
| D-010 | Saikou Neo is a standalone new app with its own application identity, local storage and release lifecycle; it does not depend on an old SaikouTV installation or install as its in-place upgrade | Owner clarified on 2026-10-17 that this is a completely new app; exact application ID remains to be chosen |

## Gate 0 decisions still open

| ID | Decision needed | Why it must be settled |
| --- | --- | --- |
| Q-002 | Supported Android versions and representative TV/Fire TV models | Sets minimum SDK, fixtures, and test matrix |
| Q-003 | Exact permanent Android application ID; D-009 settles the name and D-010 settles standalone installation | Affects Neo's installation identity, future upgrades, accounts, and signing |
| Q-004 | Playback sources, AniList authentication/sync contracts, and supported media formats | Determines data contracts, playback proof, and distribution feasibility; D-007 settles the account requirement only |
| Q-005 | Whether an optional explicit legacy-data import is needed | Separate from standalone installation; determines any import boundaries without assuming access to the old app's storage |
| Q-006 | Licence for new code and rules for upstream reuse | Must precede external contributions and copied implementation |
| Q-007 | Initial distribution route and owner of signing credentials | Defines release and upgrade acceptance criteria |
| Q-008 | Accepted artwork and brand rules | Enables consistent assets and accessible focus states |

Record the owner's decisions here and create an ADR when the reasoning affects
architecture or long-term maintenance. Do not infer final product scope from
the old app's entire feature set.

Q-001 is resolved by D-006. Provider/account boundaries and the other open
decisions must still be settled before gate 0 can close.

The [source research and health proposal](sources.md) records the handoff,
legacy observations, proposed contracts and acceptance cases. The checker,
hosting, probe cadence, update model and provider shortlist remain proposals
pending evidence and agreement.
