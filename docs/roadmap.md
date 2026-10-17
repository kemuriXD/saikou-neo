# Roadmap and stage gates

The owner chose a fresh private repository named SaikouTV2. Android TV comes
first. Apple TV discovery follows a working Android implementation; Vega OS
depends on the owner's Fire TV investigation.

Close the current gate before beginning the next. Record unresolved decisions
and defects instead of silently treating them as complete.

| Gate | Outcome | Exit evidence | Current status |
| --- | --- | --- | --- |
| 0: Scope | Agreed first release, device baseline, app identity, and content/account boundaries | Accepted decisions and observable acceptance criteria | Open |
| 1: Foundation | Reproducible Android project, build checks, and contributor setup | Clean checkout builds locally and in CI; fixture screen runs on TV | Repository setup in progress; Android work waits for gate 0 |
| 2: Playback proof | One complete TV journey using deterministic media | Browse, detail, play, pause, seek, subtitles, resume, and error recovery evidence | Not started |
| 3: Product features | Approved catalogue, providers, account, and progress features | Contract tests, failure handling, Neo data lifecycle checks, and relevant end-to-end tests | Not started |
| 4: Beta | Stable behaviour on the supported physical hardware | Android TV and confirmed Android-based Fire TV results; pilot feedback and defect closure | Not started |
| 5: Release | Repeatable signed release with support and recovery processes | Full regression evidence, performance budgets, upgrade checks, distribution checks, and release checklist | Not started |

## First Android implementation proposal

Kotlin, Compose for TV, and Media3 are candidates for gate 1. Confirm compatible
stable versions, minimum supported Android version, and playback requirements
before locking them in. Keep UI, domain rules, data access, and player integration
separate enough to test and replace independently. Add modules as boundaries
become useful; avoid creating a large module graph before there is an app.

Cross-platform readiness initially means explicit contracts, recorded behaviour,
portable fixtures, and platform-independent domain rules. The shared-code
framework and Apple TV UI architecture remain future decisions.
