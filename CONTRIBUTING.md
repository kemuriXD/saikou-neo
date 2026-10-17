# Contributing

The project is in its foundation stage. Agree the scope and licence before
accepting external contributions.

## Change process

1. Describe the problem and observable acceptance criteria in an issue.
2. Create a short-lived branch such as `feat/search`, `fix/player-resume`, or
   `chore/build-tools` from current `main`.
3. Make one coherent change. Document significant architectural choices in
   `docs/adr/` and update the relevant acceptance criteria.
4. Run the checks that cover the changed behaviour. Include device, build,
   steps, expected result, observed result, and evidence where relevant.
5. Open a pull request using the template. Wait for the configured checks and
   resolve review conversations before squash merging.

The owner is currently the only maintainer. No independent human approval is
claimed or required while that remains true. Revisit required approvals when a
second maintainer joins. CODEOWNERS identifies responsibility; it is not proof
that review was enforced.

## Quality expectations

- Prefer clear names, cohesive functions, explicit state, and small interfaces.
- Keep business rules testable without an emulator where practical.
- Cover changed behaviour and known regressions with appropriate tests.
- Use deterministic fixtures for ordinary automated checks; isolate live
  providers and account-dependent tests from the main regression suite.
- Handle cancellation, errors, retries, and lifecycle changes explicitly.
- Make TV controls work with a remote, including predictable focus and Back.
- Update documentation alongside any changed setup, contract, or behaviour.
- Record copied code, assets, fonts, and their licences in the provenance log.
- Never commit credentials or signing material. Redact personal diagnostics
  before attaching evidence.

## Checks available now

The repository hygiene workflow lints Markdown. Application compilation, static
analysis, unit tests, instrumentation tests, and release builds will be added
with the Android foundation. See [repository setup](docs/repository-setup.md).

For optional local documentation checks, use the same markdownlint-cli2 release
bundled by the pinned action in `.github/workflows/repository-hygiene.yml`.
GitHub runs this check without requiring a local Node.js installation.
