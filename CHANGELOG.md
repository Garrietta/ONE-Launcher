# Changelog

## v1.1.0 — 2026-11-15

Archive matching, refresh controls, a visible paired-files list, and a
GitHub release update manager on each port's detail page.

- Archive matching: `.zip`, `.7z`, and `.rar` files now match a port when
  their base name contains one of the port's filename hints, so
  "Legend of Zelda, The - Ocarina of Time (USA).zip" matches Ship of
  Harkinian. A downloaded `.zip` is confirmed by inspecting its entries
  locally (`java.util.zip.ZipFile`): the detail page shows which ROM it
  contains, and a single-entry "Extract" button pulls just that ROM into
  `Downloads/ONE`. `.7z`/`.rar` stay provisional (no inspection, no 7z
  library). Direct ROM matching is unchanged.
- Cloud rows show the "Matches \<port\>" chip for archives too —
  "(archive)" for a name-only match, "(contains .z64)" once the zip has
  been downloaded and confirmed — and tapping the chip opens that port's
  detail page.
- Refresh controls: a Refresh button on the Cloud screen re-runs the
  current folder browse (or the active search), and a Refresh action on
  the detail page re-runs `refreshLibrary()` (re-scanning `Downloads/ONE`
  and zip contents). The Library refresh icon already did this.
- Settings "Matched files" now lists the currently matched files per
  port (name + port + how it matched) instead of only a Clear button.
  Clear still works as before.
- GitHub release update manager (detail page only): the "Latest release"
  block fetches `releases/latest` for the port's repo when the page
  opens (metadata only, cached per port for the session, plain error +
  Retry on failure — never a crash, never blocking the rest of the page).
  It shows the installed version, the latest tag, the picked Android APK
  (.apk assets only, preferring a name containing "android"), "Update
  available" when the tag is newer than the installed version
  (numeric-core comparison, leading "v" and "-android.x" suffix stripped),
  "Up to date" otherwise, and "No Android APK in the latest release" when
  the release ships none — so Zelda 64: Recompiled and Zelda3 stay
  honest catalog/file-match entries. Download fetches the APK into
  `Downloads/ONE` and hands it to the system installer via FileProvider;
  if installs from unknown sources are not yet allowed, the page explains
  and opens the system settings screen. Nothing installs silently, and
  nothing auto-downloads on page open.
- Custom links in Settings (your own shortcuts, stored only on this device).
- Known limits: no update check on the Library list yet (detail page
  only); archive confirmation needs the zip on the device — a zip still
  only in the cloud is a provisional name match.

## v1.0.2 — 2026-11-15

Launch crash fix (second crash, different from v1.0.1).

- `OneViewModel` init-order NPE: the `init` block calls `refreshLibrary()`,
  which writes `_portStatuses`, but `_portStatuses` was declared after the
  `init` block. Kotlin initializes properties and init blocks in declaration
  order, so the backing field was still null and every launch died with
  `NullPointerException: MutableStateFlow.setValue on a null object
  reference` at `OneViewModel.kt:111` (bugreport 2026-11-15 18:39-18:40,
  five crashes on the Fold 8). All `MutableStateFlow` declarations (and
  `folderStack`) now sit above `init`, with a comment explaining why.
- CI guard: the build fails if any `MutableStateFlow` in `OneViewModel.kt`
  is declared after the `init {` line. A unit test cannot cover this — the
  project has no Robolectric and `OneViewModel` is an `AndroidViewModel` —
  so the line-order check tests the exact seam instead.

## v1.0.1 — 2026-11-15

Launch crash fix.

- Manifest activity name is now fully qualified:
  `dev.one.launcher.MainActivity`. Previously `.MainActivity` resolved
  against the `com.garrietta.one` namespace/applicationId to
  `com.garrietta.one.MainActivity`, which is not in the dex — every launch
  died with `ClassNotFoundException: Didn't find class
  "com.garrietta.one.MainActivity"` (bugreport 2026-11-15 18:20-18:21,
  three crashes on the Fold 8). The Kotlin class lives in package
  `dev.one.launcher`; the manifest now says so explicitly.
- CI guard: the build fails unless the manifest contains the fully
  qualified activity name, so this exact seam cannot regress silently.

## v1.0.0 — 2026-11-15

Initial release.

- Library of curated native Android ports (Ship of Harkinian,
  2Ship2Harkinian, Starship, Zelda 64: Recompiled, Zelda3) loaded from
  `assets/ports.json`. No game files or assets are included.
- Game detail with required-dump info, Releases link, install detection via
  PackageManager, and Launch for installed ports. Unverified package names
  are catalog + file-match only, with an honest fallback to the Releases page.
- Local file matching: pick your own file with the system file picker, or let
  ONE scan its own `Downloads/ONE` folder on refresh. Conservative matcher
  (extension + filename hint); ambiguous matches can be assigned on the
  detail screen.
- Premiumize own-cloud only: API-key connect (EncryptedSharedPreferences),
  account info, folder browse with breadcrumbs, own-cloud search, item
  details, and download of your own cloud files to `Downloads/ONE` with
  progress. No transfers, no cache search, no torrents, no hoster resolver.
- Foldable-first layout: folded is a single column regardless of
  orientation; unfolded is a two-pane library + detail layout, decided by
  window size class and folding feature.
- Per-port settings (resolution scale, FPS cap, touch-layout note) in
  DataStore.
- Known limits: debug builds only (no release signing yet); per-port
  settings are stored preferences, not enforced inside the port apps.
