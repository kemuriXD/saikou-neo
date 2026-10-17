# Development environment

## Setup sequence

1. Install stable Android Studio and its SDK Platform, Build-Tools,
   Platform-Tools, Command-line Tools, and Emulator packages.
2. Create a 1080p TV emulator with a stable x86_64 TV system image on an Intel
   Windows development machine.
3. Boot it to the TV home screen and verify hardware acceleration.
4. Align ANDROID_HOME and tool paths with Android Studio's actual SDK location.
5. Use the project-selected JDK and Gradle wrapper once gate 1 adds the build.
6. Verify ADB discovery, remote input, screenshots, and log capture before
   relying on emulator-driven application tests.

Use a normal local checkout, such as `C:\Documents\SaikouTV2`, outside synced reference
folders. Local SDK paths belong in ignored local configuration. Project build
versions and portable commands will be committed during the Android foundation.

## Initial setup evidence

Recorded on 2026-10-17:

| Item | Evidence | Status |
| --- | --- | --- |
| Git | Version 2.55.0.windows.5 available | Observed |
| Android Studio | Owner installing current release | Exact installed version not yet recorded |
| TV emulator | Owner reports successful boot to TV home | Reported; image/API and agent control still to verify |
| ADB | Standalone Platform-Tools 36.0.2 available | Observed; align with Studio-managed SDK |
| ADB device discovery | No device listed during repository setup | Needs recheck with emulator running |
| Android build and test execution | No app project exists yet | Not run |

When the emulator is available, record its image package, API level, architecture,
and acceleration result. Keep pairing details and network addresses outside the
repository. An emulator does not replace physical TV playback and remote tests.

## Official references

- [SDK Manager](https://developer.android.com/studio/intro/update)
- [Virtual devices](https://developer.android.com/studio/run/managing-avds)
- [Emulator acceleration](https://developer.android.com/studio/run/emulator-acceleration)
- [SDK environment variables](https://developer.android.com/tools/variables)
- [Java versions in Android builds](https://developer.android.com/build/jdks)
