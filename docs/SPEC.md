# Atrium（中庭）— Launcher specification

## Positioning

Wear OS launcher with a normal application-drawer entry. Register MAIN/HOME/DEFAULT
and MAIN/LAUNCHER on one reusable activity. Home-role availability and hardware
button routing depend on the system; no OEM privileges are assumed.
When not default Home, drawer, search, settings, onboarding and Tile remain usable.

## User flows

- List/grid display exported MAIN/LAUNCHER entries, excluding Atrium itself.
- Tap launches the exact resolved component in the target app task.
- Long press opens management; TalkBack has an equivalent custom action.
- Pin/favorite/hide are package-level preferences. Component-level list keys avoid collisions.
- Pin precedes favorite; alphabetical ordering is deterministic. Search also matches packages.
- Hidden apps are excluded from drawer/search and can be restored from settings.
- Uninstall does not remove preferences; reinstall restores them. Hidden management shows installed entries.
- Home reuses MainActivity and returns from subpages to the drawer; incomplete onboarding is retained.
- Home-root system Back does not finish the launcher; normal app entry allows root exit.
- First-run completion waits for durable DataStore write before navigation.
- Package broadcasts and foreground resume refresh asynchronously, with conflated requests.

## UI and dependencies

- App: Wear Compose Material3, foundation/TransformingLazyColumn and Wear Navigation.
- Tile: ProtoLayout Material3, a separate tree; system owns add/remove/reorder.
- No Horologist, phone Material3 or Wear Material 2.5 UI.
- Existing Hilt, DataStore and coroutines; Wear input for RemoteInput.
- Resource-based user strings, theme tokens for UI colors.
- Match transformedHeight with SurfaceTransformation. Text/groups apply container and content layers;
  grid row children are not separately transformed. EdgeButton stays in ScreenScaffold's slot.
- List uses icons/name/status; grid uses icons/name/status with a long-press hint.
- Performance modes use accessible RadioButton selection in a selectable group.
- Interactive targets at least 48dp; validate round clipping, large fonts and rotary input on devices.

## Theme/motion

- `dynamicColorScheme(context) ?: ColorScheme()`; no hand-authored UI palette.
- WFF seed remains reserved and is not currently applied to the palette.
- Performance/Balanced use expressive motion; PowerSaver uses standard motion.
- Reduce motion forces PowerSaver and locks controls; closing restores lastNonSaverMode.
- No promise of disabling every animation or measured battery savings.
- Performance modes also set Tile freshness hints; system may throttle.

## Modules

`:app` assembles `:feature:drawer/settings/tile/watchface` and `:core:model/data/theme/navigation`.
Features do not depend on each other. User preference identity remains in core:data/model.
WFF remains a documented stub; full watchface binaries and public extension SDK are deferred.

## Acceptance

See [VALIDATION](VALIDATION.md). A successful source review is not a substitute for
Gradle compilation, a Home-role authorization test or Pixel Watch 3 hardware testing.
