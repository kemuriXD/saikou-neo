# Working in SaikouTV2

Read README.md, CONTRIBUTING.md, docs/roadmap.md, and docs/decisions.md first.

## Scope and evidence

- Work on the current stage and its acceptance criteria. Record unresolved
  decisions explicitly; do not turn proposals into accepted architecture.
- Keep changes small and reviewable through a pull request.
- Use the old SaikouTV repository as reference. Record the original repository,
  commit, files, licence, and modifications before reusing code or assets.
- Separate UI, application rules, data access, and platform playback concerns
  when application code is introduced. Add abstractions for demonstrated needs.
- Pin build tools and dependencies. Use the checked-in Gradle wrapper once it
  exists. Do not claim a build or test passed unless it actually ran.
- Keep credentials, signing keys, pairing records, account tokens, and raw
  personal-device diagnostics outside this repository.
- Follow docs/testing.md for regression evidence. A user-reported emulator boot
  is useful setup information, but is not an application test result.

## Files and cleanup

- Move unwanted files and folders to the Windows Recycle Bin. Do not permanently
  delete them, bypass the Recycle Bin, or empty it.
- If recycling is unavailable, preserve the items and explain the limitation.
- Do not edit synced ChatGPT project reference files under sources/.

## Platform sequencing

- Android TV comes first. Physical Android-based Fire TV support needs evidence.
- Apple TV research and Vega OS implementation are deferred.
- If iOS installation is requested later on this PC, use the configured Wi-Fi
  pairing and signing setup in Ubuntu-24.04 with xtool's --network option. Keep
  device connection details outside the repository; do not switch to USB unless
  the owner asks.
