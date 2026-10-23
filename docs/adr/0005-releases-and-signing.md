# 0005. Release channels and signing

Status: Accepted
Date: 2026-10-23

## Context

Releases go out through GitHub only. The owner wants control over signing, and
testers need preview builds that don't replace a stable install.

## Decision

- Three build flavours: `alpha` and `beta` (preview, app ID suffix `.preview`)
  and `stable`. Preview builds install alongside stable.
- Versions follow the milestones: `0.0.x` while rebuilding, `0.1.0` for the
  first public release, then semantic versioning.
- CI builds unsigned debug APKs on every PR for testing.
- Until v0.1.0, the owner signs release builds locally. The keystore never goes
  into the repo. Whether CI signs releases with a key in GitHub secrets is
  decided before v0.1.0.
- Releases are published on GitHub Releases. In-app updates come later (#78).

## Consequences

- Nothing signed leaves the owner's machine before the first release.
- Testing a PR means installing a debug build, which can't upgrade over a
  signed release. That's fine while there are no releases.
