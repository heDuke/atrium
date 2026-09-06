# Atrium（中庭）

Wear OS wrist app drawer / pseudo-launcher. Not a system Home replacement.

- **applicationId:** `com.alliehe.atrium`
- **UI:** Wear Compose Material3 only (App) + ProtoLayout Material3 (Tile)
- **Forbidden:** Horologist, phone Material3, Wear Material 2.5
- Docs: [SPEC](docs/SPEC.md) · [EXTENSIONS](docs/EXTENSIONS.md) · [WFF](docs/WFF.md)

## Build

```powershell
.\gradlew.bat :app:assembleDebug
.\scripts\check-no-horologist.ps1
```

Linux/CI: `bash scripts/check-no-horologist.sh` (also runs on GitHub Actions via `.github/workflows/horologist-ban.yml`).

Requires JDK 17+ and Android SDK (`local.properties` → `sdk.dir`).
