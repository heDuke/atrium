# Atrium（中庭）

Wear OS wrist app drawer / pseudo-launcher. Not a system Home replacement.

- **applicationId:** `com.alliehe.atrium`
- **UI:** Wear Compose Material3 only (App) + ProtoLayout Material3 (Tile)
- **Forbidden:** Horologist, phone Material3, Wear Material 2.5
- Spec: [docs/SPEC.md](docs/SPEC.md)
- WFF seed stub (Phase 5): [docs/WFF.md](docs/WFF.md) — full face XML when designer assets ready

## Build

```powershell
.\gradlew.bat :app:assembleDebug
.\scripts\check-no-horologist.ps1
```

Requires JDK 17+ and Android SDK (`local.properties` → `sdk.dir`).
