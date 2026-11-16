# Changelog

## v1.4.1 — 2026-11-16

Device feedback on v1.4.0: a "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup"
(6.5 GB) in the Premiumize cloud showed no match chip, and the Pokopia
detail page said "No file for this port in your Premiumize cloud yet."
Root cause: the catalog lists no file formats for 464/536 ports (Pokopia's
only asset is a manual folder requirement with an empty accepted list),
and the matcher refused ports with no formats outright.

- Name-only matching for format-less ports: when the catalog lists no
  formats for a port, a file whose name hits the port's hints (same
  whole-word rules as the archive path) now matches, labeled
  provisionally as "name only" in Cloud chips, the detail Premiumize and
  Local sections, and the Settings matched-files list. No extension is
  invented — the .nup is not hardcoded anywhere; the match uses only the
  game's name. Ports with formats keep strict extension-gated matching,
  unchanged.
- Detail page honesty: the catalog's own file requirement and
  instructions_md are shown verbatim (for Pokopia: the folder with
  base/, .nsp/.xci not supported, copy the folder, Add game, Prepare),
  and "File info not listed in the catalog." now appears only when
  there is truly no asset info. Also fixed the doubled "Your own Your…"
  wording when a catalog label already starts with "Your".
- Known limitation: matching Premiumize/local *folders* by name is not
  built here — the catalog wants a folder for Pokopia, but the .nup file
  is covered by the name-only fallback.

## v1.4.0 — 2026-11-16

An isolated, generic remote directory browser ("Remote folders" in
Settings), kept deliberately separate from the Library: it browses a
user-configured server, it never merges into the port search, and it
writes no new matching logic.

- Remote folders: add a source with a name + URL and optional HTTP
  Basic auth (username/password). Sources live on-device only
  (DataStore; credentials in EncryptedSharedPreferences), nothing is
  prefilled, and a source can be removed. Tapping a source opens its
  own browser screen with subfolder navigation and breadcrumbs.
- Two server shapes only: standard HTTP directory listings (Apache
  mod_autoindex / nginx autoindex) and a plain JSON file list (an array
  of {name, url}). Anything else — a hierarchical catalog feed, a flat
  items[] feed, a page that is not a listing — gets a plain error
  ("Couldn't read this folder…") instead of being parsed.
- Downloads go through the system DownloadManager with progress, then
  move into the configured local game-files folder when one is set
  (DownloadManager cannot write into a SAF tree directly, so the move
  is the step that puts the file there); without a local folder the
  file stays in Downloads/ONE. Once on disk, the existing local-folder
  prescan pairs the file with its port — no port matching was added
  for remote items.
- Custom links are untouched and stay browser-only. The local folder
  picker now also persists the write grant, so remote downloads can be
  moved into the folder.

## v1.3.1 — 2026-11-16

Device feedback on v1.3.0 (folded Fold 8): the Library filter ate half the
screen, Cloud cards rendered ~2x taller than their content, and the OoT
zip false-matched Daytona USA.

- Compact platform filter: the ~29 platform chips are one horizontally
  scrolling row (~48dp) instead of a wrapping FlowRow of ~6 rows. Same
  filter, same selection — the folded Library now shows ~3 port cards
  below the header instead of one.
- Cloud card layout: the match chips are a FlowRow instead of a plain
  Row. A plain Row gives every chip past the first the leftover width
  (often ~0), so a long chip label wraps to one word per line and the row
  measures many lines tall — the card ballooned with a big empty gray
  region below the visible chips and the Download button pushed out of
  sight. Chips now wrap onto real lines inside the card.
- False-positive matches fixed (two layers). Catalog hints no longer
  include region/format tags ("usa", "pal", "ntsc", …) or
  franchise-generic words ("zelda", "legend", "time", "end", …) — a match
  needs the game's distinctive tokens. And hints now match against whole
  words of the file name, not substrings: "end" can no longer match
  inside "legend", "day" inside "daytona". Long hints (5+ letters) may
  still be a word prefix ("starfox" in "starfox64"). The OoT zip now
  matches only Ship of Harkinian (was 9 ports, including Daytona USA).
  Franchise words shared by a whole series ("mario", "final", "fantasy")
  still match across that series — an ambiguous name matches more than
  one port by design and the detail screen lets the user assign it.

## v1.3.0 — 2026-11-16

The Library now lists the full Recompa catalog (500+ ports) instead of
only the 5 curated ports, with search, a platform filter, a fork picker,
catalog-powered releases, and SHA-256 verification of downloaded APKs.

- Recompa catalog: ONE fetches https://recompa.app/index.json (the same
  database the free, open-source Recompa launcher pulls), parses it with
  `ignoreUnknownKeys`, and refuses to use it when `schema_version` is
  anything other than 1 — a schema change falls back to the curated
  ports.json with a plain note, never a crash. The raw JSON is cached in
  the app cache dir; the cached index serves the Library immediately on
  a cold start and a background refresh replaces it
  (stale-while-revalidate), plus a manual refresh from the Library
  refresh button. On fetch failure with no cache, the Library shows the
  curated ports and says "Recompa catalog unavailable — showing your
  curated ports."
- Merge: the curated 5 stay canonical. A catalog port matching a curated
  port (overlapping fork repo first, else an explicit id map for
  soh/2ship2harkinian/starship — never fuzzy name matching) keeps the
  curated title, notes and packageNames and gains the catalog's fork
  list, latest-release data and cover art. Catalog ports with no curated
  match are appended. Curated ports with no catalog match are reported
  (Zelda 64: Recompiled and Zelda3 have none) rather than guessed.
  A catalog port whose `assets[]` is missing gets no file extensions:
  file matching is disabled for it and the detail page says "File info
  not listed in the catalog" instead of inventing extensions. File
  matching derives from the catalog `assets[]`: extensions from each
  accepted format plus `convert_from`, archives match by name only when
  `zip_ok` is true, filename hints from the game title tokens.
- Library: search field (filters by port title / game name) and a
  platform filter (chips built from the catalog `platforms` list, "All"
  default). Folded single-column / unfolded two-pane layout and the
  v1.2 file badges are unchanged. Library cards show the catalog cover
  art via Coil when present, with a plain placeholder otherwise; the
  list never blocks on image loading. The Library header shows the
  catalog size and its `generated_at` date (or the unavailable note).
- Detail page: ports with more than one fork show a fork selector (repo
  name + latest tag per fork); the selected fork drives the "Latest
  release" block. Release data comes from the catalog (tag + asset URL +
  size + SHA-256) in preference to the v1.1 GitHub `releases/latest`
  call, which stays only as a fallback when a fork has no release asset
  in the catalog. The detail page shows the catalog description and
  cover art, and the selected fork's repo, package and verified flag.
- SHA-256 verification: after downloading an APK via a catalog asset
  URL, ONE hashes the file and compares it to the catalog's `sha256`
  before offering Install. On a mismatch it refuses with "Download did
  not match the catalog checksum — not installing." and deletes the
  file. A verified download shows "SHA-256 verified against the
  catalog." Signer verification (the catalog's `signer_sha256`) is a
  follow-up, not this round.
- Settings → About gains: "Catalog data: Recompa (recompa.app),
  GPL-3.0 — ports are their authors' own projects; ONE lists them, it
  does not host them." (opens recompa.app in the browser).
- Tests: unit tests for the catalog mapping (extensions from `assets[]`,
  fork fields, package names), the merge with the curated 5 (curated
  wins on conflict, catalog adds the rest, unmatched curated ports
  reported), schema_version rejection, a port with no `assets[]`
  (matching disabled), and the SHA-256 helper (known vector, match +
  mismatch). No live-network tests.
- Scope unchanged: no game files in the repo or the app; Premiumize
  own-cloud only; custom links browser-only.

## v1.2.0 — 2026-11-15

Detail page now shows your files in two sections — Premiumize and Local —
a settable local game-files folder with presearch and caching, and file
badges on Library cards and the detail header.

- Detail page, "Premiumize" section: when connected, lists the files in
  your Premiumize cloud matching this port, each with a "Download to this
  device" button (reuses the existing cloud download). The list comes from
  one `/item/listall` call per connected session, cached in the ViewModel
  and matched locally with `PortMatcher`, so one call covers every port
  (Library badges use the same data). If `listAll` fails, the page falls
  back to an own-cloud search for that port's first filename hint. A file
  already in Downloads/ONE shows as "Downloaded". Not connected: "Connect
  Premiumize from the Cloud tab to see your cloud files here." plus a
  button that opens the Cloud tab. No cloud match: "No file for this port
  in your Premiumize cloud yet."
- Detail page, "Local" section (replaces "Locate my file"): lists files
  matching this port from Downloads/ONE (with Extract for confirmed
  archives, as before), from your chosen local folder ("Found in your
  folder"), and your picked file ("Picked file") — each row shows where
  the file is and how it matches ("file" / "archive" / "archive —
  contains .z64"). A folder zip offers "Copy to Downloads/ONE", after
  which the existing inspect/extract path applies. The file picker stays
  as a "Browse…" button at the bottom. Nothing local: "No file on this
  device yet — set a local folder in Settings, or browse for a file."
- Settings, "Local game files folder": pick a folder (Storage Access
  Framework tree picker, persistable read permission). ONE pre-searches
  it — top level plus one level of subfolders, max 500 files, on a
  background thread — and caches the detected file/port pairs in
  DataStore, so badges show instantly on a cold start and are replaced
  by fresh results once the background re-scan finishes. Scans run on
  launch (when a folder is set), on folder set/change, and inside every
  library refresh. If the folder becomes unreadable, Settings and the
  detail page say "Folder unavailable — pick it again" and the cached
  pairs stay visible (stale, never silently dropped). Clearing the
  folder clears detection; clearing matched files clears picked files
  only, never detection. Folder zips are name-based provisional matches
  only — SAF files are not `java.io.File`, so a zip is confirmed only
  once it is copied into Downloads/ONE.
- File badges: Library cards and the detail header now show a second
  chip beside the StatusChip — "File on device" when a local, downloaded,
  or picked file exists, "File in cloud" when a matching file exists in
  Premiumize but no file is on this device yet, nothing when neither.
  The StatusChip text and the meaning of "Ready to play" (installed +
  file) are unchanged.
- Tests: unit tests for the pure folder mapping (file-name list + ports
  → detected pairs, incl. archives and non-matches) and for the cache
  merge (cached pairs shown on start, replaced by fresh scan, stale
  cache kept when the folder is unavailable). The SAF scan itself cannot
  run on the JVM — no test fakes it.
- Premiumize scope unchanged (own cloud only); custom links stay
  browser-only.

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
