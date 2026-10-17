# Development environment

## Setup sequence

1. Install stable Android Studio and its SDK Platform, Build-Tools,
   Platform-Tools, Command-line Tools, and Emulator packages.
2. Create a 1080p TV emulator using an available stable TV system image compatible
   with the Windows development machine. Record the actual image architecture.
3. Boot it to the TV home screen and verify hardware acceleration.
4. Align ANDROID_HOME and tool paths with Android Studio's actual SDK location.
5. Use the project-selected JDK and Gradle wrapper once gate 1 adds the build.
6. Verify ADB discovery, remote input, screenshots, and log capture before
   relying on emulator-driven application tests.

The local checkout is `C:\Users\Windows\Documents\SaikouTV2`, outside synced
reference folders. Local SDK paths belong in ignored local configuration.
Project build versions and portable commands will be committed during the
Android foundation.

## Initial setup evidence

Recorded on 2026-10-17:

| Item | Evidence | Status |
| --- | --- | --- |
| Git | Version 2.55.0.windows.5 available | Observed |
| Android Studio | Rabbit 1 / 2026.2.1, build AI-262.9437.185.2621.16467767 | Observed locally |
| Studio MCP | Protocol handshake and project directory read succeeded for the checkout above | Verified through the local MCP endpoint |
| TV emulator | Android 16 / API 36, Android TV image revision 4, x86; 1920x1080, density 320 | Boot complete and TV home visually verified |
| Hardware acceleration | Windows Hypervisor Platform acceleration check reports installed and usable | Verified |
| ADB | Studio SDK Platform-Tools 37.0.1-15733141 | Verified; use the SDK copy instead of the older standalone copy on PATH |
| ADB device discovery | Running emulator connected and shell properties readable | Verified |
| Remote control and UI inspection | D-pad Up changed focus to Home; Down returned to the favourites tile; UI trees readable | Verified on the launcher |
| Screenshots and logs | PNG screen capture viewed; logcat readable | Verified on the launcher |
| Android build and test execution | No app project exists yet | Not run |

The installed image package is `system-images;android-36;android-tv;x86`.
These checks establish development-tool access, not application correctness or
support for a particular physical TV. Keep endpoint/session configuration,
device identifiers, screenshots, UI trees, and raw logs outside the repository.
An emulator does not replace physical TV playback and remote tests.

ANDROID_HOME, tool-path alignment, and a compatible pinned project JDK still
need to be settled with the gate 1 build. No Gradle project or build result is
implied by opening the documentation checkout in Android Studio.

## Official references

- [SDK Manager](https://developer.android.com/studio/intro/update)
- [Virtual devices](https://developer.android.com/studio/run/managing-avds)
- [Emulator acceleration](https://developer.android.com/studio/run/emulator-acceleration)
- [SDK environment variables](https://developer.android.com/tools/variables)
- [Java versions in Android builds](https://developer.android.com/build/jdks)
