# Saikou Neo code review

Use Copilot only for advisory code review. Report actionable findings with the
trigger, user impact and relevant file location. Distinguish confirmed problems
from uncertainty and keep comments concise. Prioritise correctness, regressions,
maintainability and duplication. Do not implement fixes, delegate to an agent,
configure MCP or memory, approve or merge pull requests.

The hosted repository currently contains workflow configuration and documentation.
Review what the proposed change actually contains. Do not assume Android modules,
Gradle tasks or device results exist until their files and evidence are present.
Run the existing Repository hygiene check for this scaffold. Do not request live
account operations, device identifiers, signing material or personal diagnostics.

Reuse existing helpers and libraries where responsibilities match. Prefer clear
names and cohesive functions; comments explain non-obvious decisions or platform
constraints. Avoid speculative abstractions and unsupported performance claims.
Retain required licence notices. Dependency and workflow updates require reviewed
compatibility and actual checks; preserve pinned action SHAs and least-privilege
workflow permissions. Do not bypass a failed required check.

See CONTRIBUTING.md and docs/repository-maintenance.md for the current process.
