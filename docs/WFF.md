# Watch Face Format (WFF) — Phase 5 stub

Atrium feeds a seed color into Wear Material3 theming. Full WFF XML / watch-face
binaries are **not** required for this scaffold.

## Seed export API (`:feature:watchface`)

- [`WatchFaceSeed.currentSeedArgb()`](../feature/watchface/src/main/java/com/alliehe/feature/watchface/WatchFaceSeed.kt) — ARGB seed or `null`
- `WatchFaceSeed.publishSeedArgb(argb)` — runtime override from a future face / editor
- `:app` resolves theme seed via `ThemeSeedProvider` (WFF seed, then `UserPrefs.seedColorArgb`)
- `:core:theme` must **not** depend on `:feature:watchface`

## When designer assets are ready

1. Add WFF XML / drawable packages under `:feature:watchface` (raw / assets).
2. Implement `WatchFaceSeed.readEmbeddedWffSeedOrNull()` (or equivalent) to parse the seed.
3. Register the built-in face in the app manifest per Wear WFF packaging rules.
4. Keep open faces WFF-only going forward (see [SPEC.md](SPEC.md)).

Until then, theme falls back to dynamic color / library defaults and optional user accent in prefs.
