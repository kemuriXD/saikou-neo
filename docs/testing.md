# Testing and release evidence

Every acceptance case needs a stable ID, preconditions, steps, expected result,
actual result, environment, tested commit, and evidence. Use **Not run**, **Pass**,
**Fail**, or **Blocked**. A plan, screenshot mockup, or successful build is not an
end-to-end test result.

## Test layers

| Layer | Intended coverage | Introduced with |
| --- | --- | --- |
| Repository checks | Documentation and workflow configuration | Repository foundation |
| Unit tests | State transitions, domain rules, mapping, retry/cancellation, and progress logic | First relevant implementation |
| Contract tests | Provider responses, malformed data, pagination, timeouts, auth expiry, and schema changes | Data integration |
| UI tests | Focus movement, remote input, Back, loading/error states, and lifecycle restoration | First TV screens |
| Playback integration | Play/pause, seek, track selection, subtitles, resume, buffering, and failures | Playback proof |
| End-to-end | Complete agreed user journeys against deterministic fixtures | Playback proof onward |
| Physical-device tests | Codecs, audio/video sync, real remotes, standby/wake, memory pressure, and Fire TV differences | Hardware validation and beta |
| Release regression | Upgrade, signing, install/uninstall, data preservation, performance, and distribution | Release candidate |

## Evidence template

```text
Case ID and linked requirement:
Tested commit / app version / build variant:
Device model / OS / API / image:
Fixtures and network conditions:
Preconditions:
Steps:
Expected result:
Actual result:
Status: Not run | Pass | Fail | Blocked
Evidence:
Defect and retest link, if applicable:
```

Keep raw diagnostics in ignored `local-evidence/` or outside the checkout.
Commit only useful, sanitised evidence with no account tokens, personal data,
device addresses, or signed media URLs.

## Regression checklist ownership

Create the detailed case register from accepted gate 0 journeys before feature
implementation. Cover happy paths, failure/recovery paths, accessibility, and
platform/lifecycle changes. Add a case for each confirmed product regression.

Source-specific cases SRC-001 through SRC-010 are proposed in
[playback sources](sources.md). Keep deterministic parser/contract checks
separate from live provider probes and actual native-player/device evidence.

Run impacted cases for each change, the complete critical journey suite before
beta builds, and the full supported-device release suite on the exact release
candidate. Re-run affected evidence when the candidate changes. Track the last
full-suite commit so repeated checks have a clear purpose.

## Gate closure

- Acceptance criteria are met with linked evidence from the tested commit.
- Blocking defects are fixed and retested.
- Any accepted non-blocking issue has an owner and an explicit recorded decision.
- Performance budgets and device coverage are defined before their gate closes.
- The owner reviews the evidence and accepts the gate before the next stage.

Repository checks alone do not close an application or market-readiness gate.
