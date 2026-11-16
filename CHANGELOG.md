# Changelog

## v1.7.0 — 2026-11-16

Garrett's two narrated videos on the v1.6.1 Library layout, plus one
revert. Folded: the horizontal game shelf is fine as-is, but the top
bar (console chips, "Has files", search) now collapses on scroll and
the shelf pins directly under the PORTSIDE header, so the selected
game below gets the freed room; the selected-game view gets an X to
close just that pop-up view, and tapping anywhere on it (the art, the
title, the empty space) opens the game page, not only the "View game"
button. Unfolded: the long vertical console rail is gone — the
consoles are a horizontal chip row at the top, and the games are a
vertically scrolling grid, 3 wide with the detail pane open and 5
wide when it is closed (the pane keeps the game/port page, with an X
to close it). Revert: platform id "other" shows the catalog's own
name "Otra" again — the v1.6.1 "Other" display mapping was never
requested. Upgrades in place over the v1.6.1 release build (same
signature).

- Folded (GameList): the Library scrolls as one LazyColumn — the
  search row, the console chips, and the catalog status scroll away,
  and the shelf is a stickyHeader that pins under the PORTSIDE header.
  The selected-game hero is tappable anywhere (opens the game page)
  and carries a close X in its corner; picking another game, or a new
  search/filter selection, opens it again. The "Has files" chip,
  search, and console filter are unchanged.
- Unfolded (new UnfoldedLibrary): the console rail is removed; the
  consoles are the same horizontally scrolling chip row at the top,
  above a LazyVerticalGrid of games. The grid is 3 columns while the
  detail pane is open and 5 when it is closed (pure function
  unfoldedGridColumns in data/GameLibrary.kt — unit-tested; the grid
  rendering, the collapse/pin behavior, the tap targets, and the X
  buttons are Compose UI and are not JVM-testable). The detail pane
  keeps the game page / port detail, with an X to close it; selecting
  a game in the grid reopens it. Search, "Has files", and the
  catalog status stay at the top.
- Revert: displayPlatformName is removed; groupPortsByGame uses the
  catalog's platform name as-is, so "The Dark Knight Rises" reads
  "Otra" again everywhere (shelf, game page, filter, grid). Catalog
  data untouched. The v1.6.1 "Other" unit test now asserts "Otra".
- Unchanged: the game-centric grouping, star ranking, "Has files"
  filter, the v1.6.1 matching gate / extension dictionary / JSON
  display filter, and the port detail page content.

## v1.6.1 — 2026-11-16

Garrett's v1.6.0 device report: movie files were matching to games
("The Last Airbender (2010).mkv" on The Last of Us Remastered, a 2008
Dark Knight .mkv on The Dark Knight Rises) and the JSON remote folder
showed hundreds of non-game entries (Redump/TOSEC indexes, collection
pages, firmware links). The fix is a game-file gate, not a matcher-rule
change: before a file can match any port, in any source, it must be a
game file — its extension is in the dictionary for the port's platform
(or the union across platforms when the platform is "other" or
unknown), or it is an archive. Upgrades in place over the v1.6.0
release build (same signature).

- New platform → game-extensions dictionary (data/GameExtensions.kt):
  seeded from the live Recompa catalog's accepted formats per platform
  (32x, ds, gamecube, gb, gba, gbc, n64, pc, ps2, snes, wii, xbox,
  xbox360) with the standard formats filled in for the 13 platforms
  the catalog lists none for (3ds, amiga, dos, genesis, nes, ps1, ps3,
  ps4, psp, switch, switch2, wiiu, and "other" — explicit empty),
  plus Mastersystem and Dreamcast. Archives (.zip/.7z/.rar) are
  platform-agnostic.
- Matching gate: a file must be a game file before it can match a
  port/game in ANY source (Premiumize cloud, local folder, remote
  folders) — a precondition on every match kind (DIRECT, ARCHIVE_*,
  NAME_ONLY). The extension comes from the file name, else from the
  URL (a remote entry's name is often extension-less while its URL
  ends in ".zip/"). A file with no extension in either place can never
  match. The distinctive-hint and two-hint rules are unchanged, and
  the port's own catalog formats are always accepted, so a future
  catalog format the dictionary does not list yet is never blocked.
  The two reported .mkv false positives are gone; Eidos still matches
  "Pokemon Black (NDS) (USA)" (its .zip URL), Pokopia still matches
  its .nup only, the OoT zip still matches Ship of Harkinian only,
  and the v1.5.0 false positives stay clean. A Redump entry (no
  extension in name or URL) matches nothing.
- Remote folder browser: only game files are shown now (folders are
  always kept, for navigation). Against the live remote.json (376
  entries) the browser shows 73 game files (the archives) and hides
  303 (301 with no URL extension — Redump, TOSEC, collections,
  firmware — plus 2 .html). The Pokémon and Metroid Prime entries
  still show. The filter is display only; the Server library matching
  still runs on the full listing through the matcher gate.
- Platform display: the catalog calls platform id "other" "Otra" —
  the app now shows "Other". Catalog data untouched.

## v1.6.0 — 2026-11-16

The Library is game-centric now: it lists games, not ports. "Ocarina
of Time (N64)" is one entry; tapping it opens a game page with your
files for that game and the recomps / decomps for it, ranked by GitHub
stars. Emulators are excluded from the game view — this app is
recomps/decomps, not simple emulation. The port detail page is
unchanged (install, launch, files, releases, Server library section),
and the v1.5.0 dark Portside theme, console rail, and icon are
unchanged. Upgrades in place over the v1.5.0 release build (same
signature).

- Games are grouped by game title + platform (529 games from the live
  catalog: 525 with one port, 4 with two — Majora's Mask, A Link to
  the Past, Donkey Kong 64, Castlevania: Symphony of the Night; 5
  emulators excluded). A port with no game title falls back to its own
  title. The shelf shows game covers (the top-ranked port's cover)
  and the selected game shows large below it, folded; unfolded, the
  detail pane shows the selected game page. Search matches game
  titles and port titles inside a game.
- The game page's Your files section aggregates all three owned
  sources — local folder, Premiumize cloud, and remote folders
  (JSON/site) — matched at the game level with the existing matcher
  (a file matches the game if it matches any of its ports, distinctive
  hints included), deduped by file name with on-device first, then
  cloud, then server. Actions match the per-port sections: Copy /
  Extract for local files, Download for cloud files, Open in browser
  for server files.
- Recomps / decomps on a game page are ranked by GitHub stars,
  fetched from the GitHub API when a game page opens and cached on
  this device for about 7 days (so opening games does not hammer the
  unauthenticated 60-requests/hour limit). When stars cannot be
  loaded, the catalog's download counts decide the order instead;
  ports with neither go last. Tapping a port still opens its detail
  page.
- Matcher fix (Eidos): diacritics are folded before tokenizing, on
  both sides — "Pokémon" and "pokemon" are the same token now, so
  "Pokemon Black (NDS) (USA)" matches Eidos (Pokémon Black & White)
  by name only. A format-less port also matches by name when the file
  name hits at least two of its filename hints, each at least 4
  characters long ("pokemon" + "black"); one shared hint is still
  never enough, so the v1.5.0 false positives stay out (Arceus still
  does not show the Pokopia .nup, ACNH still does not show the "NEW
  upcoming releases" .txt, Army Men still does not show the X-Men '97
  .mkv files), the Pokopia .nup still matches Pokopia only, and the
  OoT zip still matches Ship of Harkinian only. Known ambiguity:
  "Pokemon Black 2" also matches Eidos by the same two-hint rule
  (no Black 2 port exists in the catalog; the match is labeled
  "name only").
- Library filter: a "Has files" chip beside the search box shows
  only games with at least one matching file (local, cloud, or
  server), using the same game-level matching as the game page.
  Off by default; it composes with the platform filter and search.

## v1.5.0 — 2026-11-16

Portside. ONE is renamed Portside (same app, same `com.garrietta.one`
package — it upgrades in place over the v1.4.3 release build) and restyled
to the dark, cinematic game-library look from the concept video: a fixed
dark theme with a single lime accent, the Library as a shelf of cover-art
spines with the selected game shown large (Play / Details), and a
console/platform rail on the left when unfolded. The detail page keeps
exactly the same content (install status, Premiumize / Local / Server file
sections, releases, file matching), restyled. New icon: the locked
container-yard photo, containers forming a P, scaled into the adaptive
icon's safe zone over a dark yard tone sampled from the image. No new
features in this round — no versions list, no tuning slider, no
auto-benchmark (separate epic, later).

- Name-only matching is tightened to distinctive hints: a file matches a
  format-less port by name only when it contains a hint no other port in
  the catalog shares, at least 4 characters long. The Pokopia .nup still
  matches Pokopia only; the three v1.4.3 false positives are gone (Arceus
  no longer shows the Pokopia .nup, ACNH no longer shows the "NEW upcoming
  releases" .txt, Army Men no longer shows the X-Men '97 .mkv files).
  458 of the 464 format-less ports keep at least one distinctive hint;
  6 lose name matching entirely (carmageddon, gt2, p3p, p4g, unreal, zzt)
  and are assigned by hand instead.
- The remote-folder listing parser now validates that a fetched page is
  actually a standard directory listing (Apache/nginx "Index of" heading,
  Apache sort links, or an nginx `<pre>` listing) before extracting any
  entries. A JS web app's HTML shell — nav/sidebar chrome — gets the plain
  "Couldn't read this folder" error and zero entries instead of showing
  HTML fragments as filenames. Standard listings and plain JSON file lists
  parse exactly as before.

## v1.4.3 — 2026-11-16

First release build (signed) — the repo is now private, and CI builds
a signed release APK (`one-release-apk`) from a keystore generated for
this round (alias `one-release`; the keystore file is backed up outside
the repo and the passwords live only in the repo's signing secrets).
Uninstall the debug APK before installing this one: a release build
cannot upgrade over a debug build (different signature).

The port detail page gains a third file section, Server library, next
to Premiumize and Local: files from the remote folders you configured
that match this port, matched with the same matcher the Cloud tab
uses. Name-only matches are labeled "name only", same as Cloud. The
action is Open in browser — it opens the file's own URL in the
external browser; nothing downloads in ONE from this section. The
detail page is the only place remote entries are matched to ports:
Library cards, search, and the home page never see remote files, and
the remote browser screen itself is unchanged (its display format and
DownloadManager download still apply there).

- Server section reads each configured remote folder's listing (root
  plus one level of subfolders — home servers usually group files in
  per-game folders), fetched once per session with the existing
  listing parser. No new parsing, no new server shapes, no catalog
  schema. Folders are never listed as files.
- Empty states mirror Premiumize: "No file for this port in your
  server library yet" when nothing matches, and a pointer to Settings
  when no remote folder is configured.

## v1.4.2 — 2026-11-16

Garrett's remote folders showed raw file names ("Pokémon Pokopia
[0400A3A01EE4C000][v655360].nup") — an arbitrary string plus a download
link, with no way to make the list legible. This adds a per-source
display format for remote folders. Display only: the parser reads the
server exactly as before (standard directory listing / plain
[{name, url}] JSON — no new parsing, no HTML/CSS handling), the
download and any matching still use the real file name, and the
original name stays reachable by long-pressing a row.

- Per-source display format (Settings → Remote folders → tune icon on
  a source): strip "[...]" segments, strip short "(...)" tags (longer
  parentheticals are kept as part of the title), underscores → spaces,
  literal find → replace pairs applied in order, whitespace collapse
  and trim. The file extension is shown as its own small chip after
  the clean title, never as part of it.
- Hide file types: a comma-separated extension list (e.g. "txt, nfo,
  md") hides those files from the browser. Display filtering only —
  the parser still reads them, and folders are never hidden.
- Show file size toggle (default on): the size shows under the title
  when the server's file list includes one (the plain JSON shape may
  carry an optional numeric `size` field); standard directory listings
  do not provide one through the current parser, so nothing shows
  there. A row whose rules strip the whole title falls back to the
  original name, so it never renders blank.
- Long-press any browser row to see the real file name, with a Copy
  button. The format is stored per source with the source itself, so
  different folders can have different naming conventions.

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

