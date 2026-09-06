# Atrium（中庭）— SPEC

> Locked for grok build. Brand + Wear M3 only + zero Horologist.

## Brand

| Item | Value |
|------|--------|
| EN | Atrium |
| ZH display | 中庭 |
| applicationId | `com.alliehe.atrium` |
| Store copy | Wrist app atrium / drawer; **never** “replace system launcher”; avoid Launcher in main title |
| Packages | Root + applicationId use atrium; feature modules use `com.alliehe.feature.*` (no brand in feature packages) |

## Positioning

- Pseudo-launcher (`MAIN`+`LAUNCHER`), **not** system Home
- No OEM privileges; no power-button / crown assumptions
- When not default Home, F1–F9 + WFF + G1–G3 + G5 must still work fully
- F11 optional bonus only

## Must ship

- F1 drawer list/grid (Wear M3 Scaffold + TLC + Button family)
- F2 pin / favorite / hide (`:core:data` UserPrefs)
- F4 Tile multi-density (ProtoLayout M3; system owns add/remove/reorder)
- F7 MAIN+LAUNCHER + correct Recents (no trampoline)
- F9 SwipeDismissableNavHost + system swipe-to-dismiss
- WFF built-in face; feed color into theme; future open faces WFF-only

## This version also

- G1 in-app search
- G2 first-run (include how to add Tile)
- G3 a11y (system type scale; reduce motion)
- G5 one-tap power saver + tiers (Performance / Balanced / PowerSaver; same store as G3)
- F11 optional: runtime probe for “set as default”; hide if unsupported; never block

## Deferred / out

- Later: F3 folders, F5 Glance, F6/F8, F10, G4 Auto Backup; then G4 export fallback
- Not: G6 phone companion; system Home; rewriting system app list / notifications / QS

## Dependency rules

- App: `androidx.wear.compose` + `wear.compose.material3` (+ foundation / TransformingLazyColumn)
- Tile: `protolayout-material3` only (separate UI tree)
- **No Horologist artifacts at all**
- No phone Material3, no Wear M2.5
- Prefer stock M3 widgets; no custom components unless thin wrappers on M3 tokens
- **No hardcoded user-facing strings** — use `res/values/strings.xml`
- **No hardcoded UI colors** — Wear `MaterialTheme` / `dynamicColorScheme` only

## Theme / motion

- Colors: Wear Material3 only — **no hand-authored palettes / hardcoded UI colors**
- **Interim acceptance (Issue #3):** never-null = `dynamicColorScheme(context) ?: ColorScheme()` (library default). Seed/HCT middle tier is tracked; apply only via official Wear/Material APIs (never a custom ColorScheme builder). `:app` may still pass seed from WFF/`ThemeSeedProvider` for future use.
- Default `MotionScheme.expressive()`; PowerSaver → `standard()`
- G3 reduce motion forces PowerSaver motion + locks tier; off restores `lastNonSaverMode`
- Exit power saver restores last non-saver tier (not default Performance)

See also [EXTENSIONS.md](EXTENSIONS.md).

## Modules

```
:app
:feature:drawer | settings | tile | watchface
:core:theme | data | model | navigation
```

- Features do not depend on each other
- F2 only in `:core:data` UserPrefs
- WFF seed injected via ThemeRepository / `:app`; `:core:theme` does not depend on watchface
- No `:core:horologist-*`

## Acceptance

1. Not default Home → F1–F9 + WFF + G1–G3 + G5 still complete  
2. G5 exit power saver → previous non-saver tier  
3. F4 content only; add Tile via G2; never claim strip reorder ownership  
