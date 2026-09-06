# Extension points (this version)

## Scope

- **This version:** internal module interfaces only (`:core:*` / `:feature:*` consumed by `:app`).
- **V1.1 (planned):** optional plugin / extension protocol under a reserved package such as `com.alliehe.atrium.api` — **not shipped now**.

## Hard bans (unchanged)

- No Horologist artifacts
- No phone Material3 / Wear Material 2.5 in App or Tile UI trees
- App UI = Wear Compose Material3; Tile = ProtoLayout Material3 (separate trees)
- Pseudo-launcher only — not system Home; no OEM / power / crown assumptions

## Internal seams (current)

| Seam | Location | Notes |
|------|----------|--------|
| App catalog | `:core:data` `InstalledAppsRepository` | MAIN+LAUNCHER query; naming may later align to `AppCatalog` |
| User prefs / F2 / G3 / G5 | `:core:data` `UserPrefsRepository` | Single source of truth |
| Theme inputs | `:app` `ThemeRepository` + `ThemeSeedProvider` | Unidirectional; `:core:theme` must not depend on watchface |
| Navigation routes | `:core:navigation` `Routes` | Feature screens do not depend on each other |

## Non-goals this version

- Public SDK / third-party plugins
- Replacing System UI Tile strip ownership
- Shipping a stable `…atrium.api` artifact
