# Repository maintenance

## Protected main branch

Verified on 2026-10-20 after the private repository moved to the owner's personal
account. The earlier private-plan enforcement limitation is resolved.

| Requirement | Verified configuration |
| --- | --- |
| Change path | Pull request required; no direct updates, including administrators. |
| Required check | `Repository hygiene`, supplied by GitHub Actions, with the branch up to date. |
| Discussions | Review conversations must be resolved. |
| History | Linear history and squash merges. |
| Destructive updates | Force pushes and branch deletion blocked on `main`. |
| Independent approvals | Zero while there is one maintainer; revisit with a second maintainer. |

The stored policy was copied to a disposable branch at the existing hosted
`main` commit. An administrator attempted a normal fast-forward update to a child
commit with the identical file tree. GitHub rejected it with HTTP 422, stating
that changes must use a pull request and the `Repository hygiene` check was
expected. The test branch stayed unchanged; `main` and its protection were not
modified by the test. Only the disposable branch and its rule were removed
after verification. This completes the enforcement test recorded in issue #3.

The existing check lints repository Markdown using the pinned workflow action.
Add required Android build, lint and test checks after application workflows are
published and their hosted runs pass. Local app checks are not hosted check
results. This increment contains no app source, binaries, device evidence or keys.

## Advisory review

Copilot Student code review is available for the owner's account. An active
ruleset requests Copilot review for ready pull requests targeting `main`; draft
reviews and automatic re-review on every push are off. The effort level is
Balanced. Copilot approvals and automatic merging are off; a maintainer decides
how to address findings and when to merge after checks pass.

Cloud-agent repository access is set to No repositories. Partner agents,
repository Memory, agent automations and MCP tools during review are off. No
custom MCP servers or paid additional usage are enabled. Review-only guidance
is in `.github/copilot-instructions.md`; review instructions come from the PR's
head branch. Reviews use the author's available Copilot allowance.

## Dependency updates and cleanup

Dependabot proposes GitHub Actions updates weekly on Monday at 09:00,
Europe/London time. At most three version-update PRs are open at once.
Compatible minor/patch updates are grouped; majors remain separate proposals.
Each update still needs review and passing checks; no automatic merging or
fixing is enabled. Keep workflow actions pinned to reviewed full commit SHAs.

Gradle/version-catalogue update configuration is prepared locally. Enable it when
the corresponding build files reach the default branch. Do not add empty Gradle
manifests or publish app history just to activate a dependency bot. Dependency
verification inventory and toolchain compatibility need review with each future
app dependency update.

Merged PR branches are cleaned up automatically and can be restored on GitHub.
Squash messages use the PR title and description. For local disk cleanup, retain
useful tools, evidence and recovery baselines; recycle only proven obsolete items.

References:

- [Protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- [Copilot automatic review](https://docs.github.com/en/copilot/how-tos/copilot-on-github/set-up-copilot/configure-code-review)
- [Review instructions](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/add-custom-instructions/add-repository-instructions)
- [Dependabot options](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference)
