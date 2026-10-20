# Contributing

Use a short-lived branch and a reviewable pull request for a coherent change.
Describe the problem, resulting behaviour, actual validation and any limitations.
The protected `main` branch requires a PR, the passing GitHub Actions
`Repository hygiene` check, an up-to-date branch and resolved conversations.
Linear history is required; direct updates, force pushes and deletion are blocked,
including for administrators. With one maintainer, zero independent approvals are
required; revisit that choice when a second maintainer joins. Human review remains
part of the process.

Reuse established helpers and libraries where responsibilities match. Prefer
clear names and cohesive functions; comments explain non-obvious reasons or
constraints. Review duplication, lifecycle/state ownership, accessibility and
performance where relevant. Keep the change focused and record checks that were
not run. Never claim fixture coverage proves a live device or provider result.

Copilot is used only for advisory code review. Human judgement and required checks
remain necessary. Do not delegate implementation to cloud agents, add MCP/memory
features, approve with Copilot or merge automatically as part of a review.

Keep credentials, signing material, pairing/device details and personal diagnostics
out of Git. Review provenance and licence rights for reused material. Private
owner plans and evidence remain local. Move obsolete local files to the Windows
Recycle Bin; do not permanently delete them or empty the bin during cleanup.

See [repository maintenance](docs/repository-maintenance.md) for current checks,
review configuration and the verified protection result. Require Android build,
lint and test checks once those hosted jobs exist and have passed; this repository
setup change does not publish application code or establish native test results.
