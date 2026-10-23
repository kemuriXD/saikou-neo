# Architecture decision records

One short note per decision: what we decided, why, and what it costs. If a
decision changes, write a new ADR that replaces the old one and mark the old one
as replaced. Don't rewrite history.

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](0001-two-apps-one-shared-core.md) | Two apps on one shared core | Accepted |
| [0002](0002-shared-core-stack.md) | Shared-core stack | Accepted |
| [0003](0003-extensions.md) | Playback-only extensions, loaded in-process | Accepted |
| [0004](0004-pairing.md) | Pairing with a confirmation code | Accepted |
| [0005](0005-releases-and-signing.md) | Release channels and signing | Accepted |

New ADRs copy this shape:

```markdown
# NNNN. Title

Status: Proposed | Accepted | Replaced by NNNN
Date: YYYY-MM-DD

## Context
## Decision
## Consequences
```
