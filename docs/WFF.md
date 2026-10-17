# Watch Face Format (WFF) — Phase 5 stub

Atrium feeds a seed color into Wear Material3 theming. Full WFF XML / watch-face
binaries are **not** required for this scaffold.

## Seed export API (`:feature:watchface`)

- [`WatchFaceSeed.seed`](../feature/watchface/src/main/java/com/alliehe/feature/watchface/WatchFaceSeed.kt)
  — `StateFlow<Int?>` of the current ARGB seed (`null` = none); `currentSeedArgb()` reads it once.
- `WatchFaceSeed.publishSeedArgb(argb)` — runtime override from a face / editor; `clearPublishedSeed()`.
- `WatchFaceSeed.publishFromBitmap(bitmap, generator)` — extracts the dominant colour of a face
  preview with the native extractor (`:core:color-native`: `AndroidBitmap_lockPixels`, ≤64×64
  sampling, HCT hue buckets scored by proportion × chroma, grays/near-black/near-white filtered,
  NEON on arm) and publishes it. Images with no usable colour publish `null`.
- `:app` `ThemeRepository` combines `WatchFaceSeed.seed` with prefs; `ThemeResolver` decides
  whether it applies (System fallback, or Seed source without a user seed).
- The seed is in-process only; the Tile does not observe it.
- `:core:theme` must **not** depend on `:feature:watchface`

## When designer assets are ready

1. Add WFF XML / drawable packages under `:feature:watchface` (raw / assets).
2. Implement `WatchFaceSeed.readEmbeddedWffSeedOrNull()` (or equivalent) to parse the seed.
3. Register the built-in face in the app manifest per Wear WFF packaging rules.
4. Keep open faces WFF-only going forward (see [SPEC.md](SPEC.md)).

Until then, the theme falls back to dynamic color → user seed → brand seed (see SPEC.md Theme).
