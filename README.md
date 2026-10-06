# Atrium（中庭）

Wear OS launcher and application drawer, built with Wear Compose Material 3.

- **applicationId:** `com.alliehe.atrium`
- **Minimum:** Android API 33 (Wear OS 4+)
- **Primary device acceptance target:** Pixel Watch 3
- **App UI:** Wear Compose Material 3; **Tile UI:** ProtoLayout Material 3
- No Horologist, phone Material3 or Wear Material 2.5 UI.

## Features

- MAIN/HOME/DEFAULT registration plus regular MAIN/LAUNCHER entry.
- System Home-role request when available; unsupported devices retain all drawer features.
- Repeated Home requests return to the drawer. Home root back stays on the drawer;
  regular app root back may exit. Physical button routing is OEM/system-dependent.
- List and two-column grid, app icons, long-press management and accessibility actions.
- Pin, favorite, hide and restore; uninstall preserves package preferences for reinstall.
- Background catalog refresh on package changes and foreground entry; launch-failure feedback.
- Search using Wear system RemoteInput; query survives saved-state recreation.
- First-run guide with awaited persistence, settings with radio selection and motion controls.
- Shortcut Tile opens the drawer. WFF and seed-color support remain explicit stubs.

## Build

Requires JDK 17+ and Android SDK platform 37; configure `sdk.dir` in local.properties.

```bash
bash gradlew :app:assembleDebug :app:assembleRelease :core:data:testDebugUnitTest :app:lintDebug
bash scripts/check-no-horologist.sh
```

PowerShell: `.\gradlew.bat :app:assembleDebug` and `.\scripts\check-no-horologist.ps1`.
Release is unsigned unless you supply a signing configuration.

## Documentation

- [SPEC](docs/SPEC.md)
- [Repair plan and review](docs/REPAIR_PLAN.md)
- [Validation status and device checklist](docs/VALIDATION.md)
- [Extensions](docs/EXTENSIONS.md)
- [WFF stub](docs/WFF.md)

These source changes have not yet been compiled in the review environment. See validation status for
which checks ran and which require a working Android build/device environment.
