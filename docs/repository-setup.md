# Repository configuration

## Bootstrap

The private repository is `kemuriXD/SaikouTV2`, with `main` as its default branch.
The local checkout is `C:\Documents\SaikouTV2`.

Included in the foundation change:

- README, stage gates, decision register, environment notes, and testing policy.
- Contribution process, CODEOWNERS, pull request template, and structured issues.
- EditorConfig, Git line-ending rules, and Android/credential ignore rules.
- Repository hygiene workflow with read-only token permissions, a timeout,
  cancellation of superseded runs, and actions pinned to full commit SHAs.
- Weekly Dependabot checks for GitHub Actions. Updates require review and checks;
  no automatic merge is configured.
- Artwork handoff and source/licence provenance records.

## GitHub settings to verify

Record observed outcomes after configuration rather than assuming defaults:

| Setting | Intended configuration | Verified outcome |
| --- | --- | --- |
| Visibility | Private | Verified at creation |
| Default branch | main | Verified at creation |
| Merge method | Squash only; automatic merging disabled | Verified through GitHub API |
| Actions token | Read-only by default; no PR approval permission | Verified through GitHub API |
| Allowed actions | GitHub-owned actions and the approved Markdown lint action | Verified through GitHub API |
| Action references | Require full commit SHA pins | Verified: GitHub SHA-pinning policy enabled |
| Dependabot alerts and security updates | Enabled | Verified: alerts endpoint returned 204; security updates enabled and not paused |
| Main protection | Require PR, passing Repository hygiene check, resolved conversations, linear history; block force push and deletion | Blocked: GitHub returned 403 requiring GitHub Pro for private repositories |
| Independent approvals | Zero while there is one maintainer; revisit with second maintainer | Documented convention; branch enforcement unavailable |
| Secret scanning and code scanning | No paid feature assumed | Not configured; secret scanning was disabled at creation |

The owner requested a private repository, so visibility stays private. The
current plan does not enforce branch protection. Follow the PR process manually
until the owner enables a plan that supports private-repository protection.
Checks still run, but a failed or missing check does not currently block a merge
or direct push. CODEOWNERS also does not enforce review on its own.

## Bootstrap verification

- Local actionlint v1.7.12 passed; its downloaded archive matched the published
  SHA-256 checksum.
- The pinned Markdown action ran locally and linted all 12 Markdown files with
  zero issues.
- Hosted workflow results are recorded on the foundation pull request.

## Added when the Android foundation starts

- Compatible pinned JDK, Android Gradle Plugin, Gradle wrapper with checksum,
  Kotlin, SDK, and dependency versions.
- Version catalogue, dependency verification/locking strategy, and Gradle updates
  in Dependabot.
- Formatting/static analysis, unit tests, debug build, and deterministic TV tests.
- Test reports and short-lived private build artifacts with documented retention.
- Separate signed release workflow, protected credentials, versioning, and
  upgrade/recovery checks after app identity and distribution are agreed.

No signing secrets, paid services, external collaborators, or application
dependencies are needed for this documentation-only bootstrap.
