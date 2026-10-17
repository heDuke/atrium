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
- Pin precedes favorite; alphabetical ordering is deterministic (Collator `Locale.CHINA` on the
  pinyin romanization, then label, then component). Search matches label, package, full pinyin
  (spaces ignored) and pinyin initials (e.g. `wx` → 微信).
- Recents: the last 5 launched components appear above Pinned in List/Grid (not duplicated in
  Honeycomb); clearable in Settings.
- Honeycomb: long-press + drag reorders (edge auto-pan), order persisted per component and
  resettable; panning has rubber-band edges; TalkBack has move earlier/later actions.
- List rows use SwipeToReveal (More → actions, Pin) while keeping the custom a11y actions.
- App actions show launcher shortcuts only while Atrium holds HOME (shortcut host permission).
- When Atrium is default Home, the drawer offers "Back to watch face".
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

- Theme source (Settings → Theme): **System** (default), **Seed** (8 presets), **Brand** (`BRAND_SEED`).
  Resolution (`:core:color` `ThemeResolver`, truth-table tested):
  System → `dynamicColorScheme(context)` when the platform offers it (SDK ≥ 35 and
  `dynamic_color_theme_enabled`), observed live via ContentObserver; otherwise falls back to
  watch-face seed → user seed → brand, and the Theme page says system colours are unavailable.
  Seed → user seed (or watch-face seed when none) → brand. Brand → brand.
- Generated schemes come from Atrium's own clean-room HCT (CAM16 + CIE L*) in `:core:color-native`
  (C++17/JNI, one call returns all 29 Wear M3 roles) with a bit-compatible pure-Kotlin fallback in
  `:core:color`. Background is always pure black (also forced on the dynamic scheme);
  every on-colour pair is ≥ 4.5:1 (≥ 7:1 at contrast 1.0); outline ≥ 3:1 against black.
  No Material Color Utilities / material-kolor (banned by `scripts/check-no-horologist.sh`).
- The WFF seed (`WatchFaceSeed.seed` StateFlow) feeds the app palette when available; the Tile
  uses the persisted user seed / brand (`materialScope(allowDynamicTheme = source == System)`).
- Themed icons (default on): monochrome adaptive layers tinted onPrimaryContainer on
  primaryContainer, round-masked; Honeycomb cells tinted from each icon's extracted dominant colour.
- Performance/Balanced use expressive motion; PowerSaver uses standard motion.
- Reduce motion forces PowerSaver and locks controls; closing restores lastNonSaverMode.
- No promise of disabling every animation or measured battery savings.
- Performance modes also set Tile freshness hints; system may throttle.

## Modules

`:app` assembles `:feature:drawer/settings/tile/watchface` and
`:core:model/data/theme/navigation/ui/color/color-native`. `:core:color` is pure Kotlin/JVM
(interfaces, resolver, Kotlin HCT); `:core:color-native` implements `SchemeGenerator` via JNI and is
bound in `:core:data`'s Hilt module. Shared composables (`AppIcon`, messages, headers) live in `:core:ui`.
Features do not depend on each other. User preference identity remains in core:data/model.
WFF remains a documented stub; full watchface binaries and public extension SDK are deferred.

## Acceptance

See [VALIDATION](VALIDATION.md). A successful source review is not a substitute for
Gradle compilation, a Home-role authorization test or Pixel Watch 3 hardware testing.
