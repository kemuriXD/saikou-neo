# Contributing

Thanks for helping with Saikou Neo. The repository is private while the app is
being rebuilt, so this mostly describes how the owner and Claude work together.
The same rules apply to anyone who joins later.

## The loop

1. Pick an issue from the **Ready** column of the project board, or open one.
2. Branch from `main` as `<type>/<issue>-<short-name>`, for example
   `fix/71-retry-button`.
3. Write tests for any new logic, then the code.
4. Open a pull request titled in Conventional Commits form, such as
   `fix(tv): make Retry reload the row`, with `Closes #71` in the body.
5. CI builds both apps and runs lint and tests. It also attaches debug APKs you
   can install, one per release channel: `stable`, plus `alpha` and `beta`.
   Alpha and beta are preview builds with the `.preview` app ID, so they install
   next to stable.
6. If the issue is labelled `needs-tv-test`, install the APK on a TV and comment
   on the PR with what you saw.
7. Once checks pass, the PR is squash-merged.

By contributing, you agree that your work is licensed under
[GPL-3.0-only](LICENSE).

## Labels

| Group | Labels |
| --- | --- |
| Type | `bug`, `feature`, `chore`, `docs`, `design` |
| Area | `area:tv`, `area:companion`, `area:shared`, `area:player`, `area:extensions` |
| Flags | `needs-tv-test`, `blocked`, `good first issue` |

Labels starting with `legacy:` are from before the October 2026 restructure and
aren't used for new work.

Priority is the order of the **Ready** column. Milestones are release versions.

## Keeping things tidy

Clean up as you go. Scratch files go in the ignored `tmp/` folder, merged
branches get deleted, and anything a change stops using is removed in the same
PR.

## Decisions

If a change goes against an agreed architecture decision, raise it in the
issue first rather than working around it.
