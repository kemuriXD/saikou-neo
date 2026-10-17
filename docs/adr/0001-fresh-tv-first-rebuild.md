# ADR 0001: Fresh TV-first rebuild

Date: 2026-10-17

Status: Accepted by project owner

## Context

The old SaikouTV implementation needs a major overhaul. The owner chose a new
repository so the rewrite can establish maintainable boundaries, reproducible
builds, and explicit regression evidence without carrying the old structure
forward by default.

## Decision

Create a private SaikouTV2 repository. Start with Android TV and verify
Android-based Fire TV compatibility on physical hardware. Defer Apple TV
discovery and Vega OS implementation. Use the old repository as reference and
review any selected reuse with recorded provenance.

Deliver one accepted stage at a time. Define and run the relevant regression
and end-to-end checks before progressing. Record open decisions explicitly.

## Consequences

The project can choose its foundation deliberately, but a new repository does
not establish feature parity or remove migration and licensing decisions.
Application identity, content scope, supported devices, licence, and distribution
remain gate 0 decisions. Shared-code technology will be chosen when evidence
from a second platform makes its costs and benefits concrete.

Follow-up on 2026-10-17: D-011 resolves the legacy-data decision. Every user
starts fresh in Neo; legacy application data migration and imports are excluded.
Neo's own future upgrades and explicit AniList sync remain separate concerns.
