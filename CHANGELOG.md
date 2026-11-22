# Changelog

## v1.9.1 — 2026-11-22

A design pass after the unslop rules (quiet chrome, color spent on one
moment per screen) and film-grain gradients.

**The backdrop is the moment.** The platform's color now falls as light
from above the shelf and deepens toward the floor, with a fine film
grain over the whole screen, as on a cinema screen. The two white
"swoosh" sweeps and the white corner glow are gone; the faint blurred
wash of the cover art stays. `ui/theme/Grain.kt` holds the grain: tiled
luminance noise laid on in Overlay, so it never shifts the platform's
hue.

**Calmer type.** Titles are Archivo SemiBold instead of Black and
ExtraBold: a title reads by its size and Archivo's wide shape, and the
screen keeps one loud thing, the platform's light. The wordmark keeps
its Black. In the console rail and tabs the selected platform is
SemiBold and the rest Medium, with the color and the bar marking the
selection as before.

**Screenshots on the JVM.** `MarqueeScreenshotTest` (Robolectric and
Roborazzi, test dependencies only) draws the backdrop and type for N64,
Switch and PS2: `./gradlew :app:recordRoborazziDebug`.

Not device-tested: the grain and the light need a look on the Fold,
folded and unfolded.

## v1.9.0 — 2026-11-19

**Portside is now Marquee.** New name, new icon, new look.

**Name.** The app label, theme, wordmark, README and user-facing
strings say Marquee. The package (`com.garrietta.one`), signing key and
on-device folders (`Downloads/ONE`) are unchanged, so v1.9.0 installs
over v1.8.8 and keeps every setting, cache and download.

**Icon.** A new adaptive icon: a shelf of game spines with one cover
pulled forward, framed in white with a play mark, on a deep marquee-red
gradient. It is all vector (no PNG), and it ships a monochrome layer
for Android 13+ themed icons. `tools/make_icon.py` generates the three
vector drawables and the `docs/icon.svg`/`docs/icon.png` preview from
one shape list.

**Library redesign — touch-first, no button hints.** Modelled on the
"ONE" handheld concept video, rebuilt for phones and tablets:
- The whole screen is washed in the selected game's platform color
  (`PlatformTint`: Switch red, N64 green, PS2 blue …), with the cover
  art blurred faintly behind it and two soft light sweeps.
- Games stand on a shelf as spines with their titles running up the
  side; the selected game is pulled forward as a full cover in a white
  frame. Tap a spine to pull it forward, tap the cover to open the game.
- The selected game's title is set huge under the shelf with its
  platform, port count, install status and file source, plus a solid
  **Play** pill (when a port is installed — it launches it directly) or
  **Open**, and a translucent **Details** pill. Swipe left/right across
  the title to step through the shelf.
- "Ready to play" and "Has files" collections sit at the bottom as
  small cover strips; tapping one selects that game.
- Wide windows (tablets, unfolded foldables, phones in landscape) get
  a console rail on the left; narrow ones get a swipeable row of
  console tabs. The "Has files" filter is a pill beside the shelf
  title; search is an icon in the top bar.
- No bottom navigation bar or side rail: Cloud and Settings are icons
  in the top bar, and every other page has a back arrow (and the
  system back gesture). The unfolded grid + detail pane is replaced by
  the shelf; game and port pages open full screen on both postures.

**Game and port pages.** Same platform-tinted backdrop behind a
transparent top bar; the header is the framed cover beside a big title,
with a solid Play pill on the game page when a port is installed.

**Type and color.** Archivo (SIL OFL 1.1, bundled in `res/font`,
license in `FONT-LICENSE-Archivo.txt`) for all text — ExtraBold/Black
for titles and the wordmark. The lime accent is gone: primary actions
are off-white pills with dark text, everything else is white and greys
on the platform tint. The window background is dark from the first
frame (no white flash on launch).

Not device-tested in this round — needs a retest on the Fold (folded
and unfolded) and a phone in landscape.

## v1.8.8 — 2026-11-16

Garrett's v1.8.7 video, four reports in one round: the status-bar
icons, the Guitar Hero / F-Zero false positives, the double magnet
indicator on posters, and magnets in "Has files".

**Status-bar icons (Garrett's video).** The time and battery were
invisible — black icons on the black background. Root cause: the
theme is fixed dark (#0B0B0D, no light theme) on every device, but
MainActivity called bare `enableEdgeToEdge()`, whose
`SystemBarStyle.auto` follows the SYSTEM theme — on a light-system
device the icons are dark on the app's black background. Both bars
are now pinned to `SystemBarStyle.dark(Color.TRANSPARENT)`, always:
light icons on the dark theme. Not JVM-testable — device retest:
time/battery visible in light icons.

**Matcher: a single shared word no longer matches (the similarity
guard, corrected).** "DJ Hero (USA).zip", "Super Hero" and "World
Hero" matched the Guitar Hero Series Xbox 360 port, and "F-Zero"
matched Metroid Zero Mission: each file's title reduces to one
significant word ("hero", "zero") shared with the game title, so
the symmetric overlap coefficient is 1.0 (1/1) and a hint hits.
Both the ARCHIVE_PROVISIONAL similarity leg and the NAME_ONLY gate
now also require the guard: shared title words >= 2, OR the game's
title words are all in the file's title words, OR the file's words
are the game's minus only the port's platform word ("SimCity" vs
"SimCity SNES" on the SNES port). The game-⊆-file leg is what saves
the single-word games — SM64 ["mario"] ⊆ ["mario"], SoH ["ocarina"]
⊆ ["ocarina", "master", "quest"] (the OoT Master Quest magnet),
GoldenEye ["goldeneye"] ⊆ ["goldeneye", "007"] — while DJ Hero
["hero"] vs ["guitar", "hero", "series"] is a proper subset that
is none of the three legs, so it is blocked. A literal "shared
>= 2 OR >= 1 DISTINCTIVE hint" bar was swept and rejected: it
FAILS to block DJ Hero ("hero" IS distinctive — only the Guitar
Hero Series port has it in its hint set) and it BREAKS Super Mario
64's own archive ("mario" is shared, not distinctive, and the
distinctive hint "ghostship" is not in the file), so there is no
distinctive-hint leg in the guard. NAME_ONLY keeps its
distinctive-hint / two-hint requirement unchanged: it is essential
for Pokopia (the .nup matches ONLY through the distinctive hint
"pokopia"); the guard is an additional precondition on the
similarity gate, not a replacement.

Correction to the first v1.8.8 guard, stated plainly: that guard
(shared >= 2 OR the file's words exactly the game's words, on the
ARCHIVE_PROVISIONAL leg only) had two defects, both proven by its
own sweep. (1) It did not fix Guitar Hero at all: the live Guitar
Hero Series port lists NO file formats in the catalog (its only
asset is a manual disc asset with no accepted formats and no
zip_ok), so its DJ Hero / Super Hero / World Hero magnets arrive
through the NAME_ONLY path, which that guard did not touch — and
its unit-test fixture wrongly gave the port .xex + archiveOk=true,
so the tests passed while live behavior was unchanged. The guard
now covers NAME_ONLY too, and the fixture matches the live port.
(2) Its exact-title leg lost three true positives: "GoldenEye 007
(USA).zip" from the GoldenEye (N64) port (which lost all 10 of its
old links), "SimCity (USA).zip" from SimCity SNES, and the OoT
Master Quest magnet from Ship of Harkinian. All three are kept by
the corrected guard (legs (b) and (c) above).

Swept over the Recompa catalog (536 ports) x the OLD remote.json
(26,710 entries — the snapshot that still contains Super Hero and
World Hero, so the matcher blocking is verified independently of
the adapter's region filter): 3,649 matches under v1.8.7, 3,252
under v1.8.8 — 397 magnet pairs removed, all of them the
single-shared-word class (every removed pair is a one-word file
title sharing one word with a multi-word game title; zero removed
pairs share two words or contain the game's title), no
downloadable-file pair lost, zero new matches added. The DJ Hero
magnets are gone from Guitar Hero Series (DJ Hero, DJ Hero 2 in
six variants, Super Hero, World Hero — 9 pairs), and the F-Zero
magnets are gone from Metroid Zero Mission (F-Zero, GP Legend, GX,
X; Maximum Velocity never matched — similarity 0.33). The Guitar
Hero Series page now has only genuine Guitar Hero files: Guitar
Hero (USA), Guitar Hero 5 (two variants), Guitar Hero II. The
listed true positives are all kept (Eidos → Pokemon Black, Pokopia
→ .nup, SoH → OoT .z64 and the Master Quest magnet, PokePark Wii
USA magnet, 007 → the 007 Xbox game with its 3 magnets,
.hack//versus still 0 magnets, "Super Mario 64 (USA).zip" still
matches the SM64 N64 port, "Super Mario 64 DS" still matches it
too — and only it, down from 7 Mario ports, GoldenEye 007 (USA)
still matches project-midas and only it — not the goldeneye-recomp
port — SimCity (USA) still matches urbanrecomp). Nineteen games
lose all their old links, but none of them has its own file in the
feed at all — their old links were only other games sharing one
word (Master of Darkness lost only "Darkness, The" / "Darkness II"
magnets; Doraemon 3 only "SOS"). Two kept pairs are known
pre-existing false positives, unchanged from v1.8.7, not
regressions: "Mario & Luigi" vs Super Mario 64 (the game's words
["mario"] are in the file's) and "Guitar Hero – Smash Hits" vs
Super Smash Bros. (the game's words ["smash"] are in the file's);
the same containment keeps the GoldenEye "Rogue Agent" /
"Reloaded" and "SimCity DS" magnets on their ports, as under
v1.8.7.

**Poster: exactly one magnet indicator (Garrett's video).**
"**I only need one indicator of the magnet link.**" The v1.8.6
design showed BOTH a "Magnet matched" status AND a "json · Magnet"
chip. The status is install state only now — "Ready to play" /
"Installed" / "Not installed" — and the source chip
(`posterSourceLabel`, unchanged) is the one indicator. A
magnet-only game reads "Not installed" (or "Installed") with the
"json · Magnet" chip; a magnet is a link to get the file, not the
file itself, so it never reads "Ready to play".

**"Has files": magnets count now (Garrett's video, reversed
policy).** "**If it has a linked file, it should pop up under this
has files page, which some of them the magnets count as has
files.**" This supersedes the v1.8.3 rule ("magnets never in Has
files"). The set is now the union of the file-pairing map's keys
and the magnet-pairing map's keys (`gamesWithMatches`, in
GameLibrary.kt), taken at both sites that derive it (cache load and
the background-refresh save). "Your files" on the game page is
unchanged — magnets stay in their own Magnet links section there —
and the magnet copy action is unchanged.

Matcher rules other than the Task 1 guard, the game-centric
grouping, the star ranking, the pairing cache semantics, and the
(En) handling are unchanged.

## v1.8.7 — 2026-11-16

Performance audit (all 12 items) + the caching fix Garrett filmed
in his v1.8.6 video. The headline: game pages now show their links
immediately from cache, every time — no "No file" flash, no reset
to zero on reopen.

**The cache is the source of truth now (Garrett's video).** v1.8.6
made game pages non-blocking, but they still recomputed the match
on every open and showed "No file for this game yet" until the live
match landed — 'Splosion Man's magnet took ~10s to appear, 007
Nightfire reset to zero on reopen. Root cause: a stale-save race.
The pairing runs in a 7-flow combine whose transform took ~179s
(pre-index); the FIRST transform ran while the server listings were
still empty, but the "settled" gate checked the LIVE state, which
was settled by the time the transform finished — so the save wrote
the stale result (files populated, magnets EMPTY), overwriting a
good cache. Fixed two ways: the save is now gated on snapshot
completeness (the transform reports whether every configured remote
folder had a listing in the snapshot it computed from — a partial
result never overwrites the cache), and the pairing itself is ~35x
faster (below), so the correct save lands within seconds of launch.
The pairing runs once on first launch and re-runs whenever a source
is added; the cache is never capped. Game pages, posters, and "Has
files" all read from the same cache, instantly.

**Token Index (audit item 3).** The pairing scanned all 536 ports
for every one of the 26,710 remote entries (~14M matchKind calls,
~179s). A word-to-port index now maps each word token to the ports
that could match it, so each entry is only tested against
candidates sharing at least one word (plus the prefix rule for
5+ letter single-word hints). Every candidate is still verified by
the UNCHANGED matchKind, so results are byte-for-byte identical to
the old scan (verified by a full-catalog brute-force comparison:
the old scan every-port-against-every-magnet vs the index, over
live Recompa 536 ports x live remote.json 26,318 magnets —
identical on the cleaned-name key, including all true positives).
Measured: files 3.0s → 0.26s, magnets 176.6s → 4.8s, total
179.6s → 5.1s (~35x). The full pairing is still not under the
40 ms audit target — it runs on a background thread, and the
user-facing reads (game pages, posters) come from cache instantly,
so the 40 ms number does not gate anything the user sees. All
Regex instances are hoisted to top-level constants (no Regex(...)
inside the hot paths), and gamesWithFiles() is deleted — the "Has
files" set is derived from the pairing map's keys in O(1).

**Download progress throttle (item 1).** Progress was emitted on
every 8 KB read (~850,000 StateFlow updates for a 6.5 GB file).
Now a 64 KB buffer, at most one emission per 100 ms or per 1%
step. A simulated 6.5 GB download at 50 MB/s emits ~1,000 times
— three orders of magnitude below 850k (the 100 ms rule, not the
1% rule, dominates at that speed).

**Detail page + rows off Main (item 2).** GameDetailContent's
cloud/server matches ran directly in the composable body (no
remember) — every recomposition, including every download-progress
tick, re-scanned the 26k-entry feed on the UI thread. They now run
in produceState on Dispatchers.Default. CloudItemRow's matchKinds,
RemoteBrowserScreen's 26k-entry filter, and RemoteBrowserRow's
title formatting are wrapped in remember.

**Keystore off Main (item 4).** SecureKeyStore and
RemoteFolderCredentials created EncryptedSharedPreferences
synchronously in the ViewModel constructor. The prefs are now lazy
and every read/write runs on Dispatchers.IO.

**Streaming JSON (item 5).** remote.json and the catalog caches no
longer allocate a ~22 MB String + a full DOM tree. Parsing decodes
directly from streams into lightweight DTOs, and cache reads/writes
stream through buffered streams.

**Pairing cache is a file now (item 6).** It was a single multi-MB
JSON string inside Preferences DataStore. Now pairing-cache.json in
filesDir, written atomically (temp file + rename) so a crash
mid-write never leaves a torn cache. The v1.8.6 DataStore cache is
migrated on first load — no cached pairings are lost. Format
unchanged (version stays 1). Note: `Json.encodeToStream` /
`decodeFromStream` do not exist in kotlinx-serialization-json
1.7.3 (they arrived in 1.8.0), so the file is read/written through
a buffered reader/writer with `encodeToString`/`decodeFromString`
— same JSON, same pattern as the other cache files.

**No duplicate refresh (item 7).** LibraryScreen's LaunchedEffect
refreshLibrary() is removed — OneViewModel.init already runs it
once. Tab switches back to Library no longer re-scan the local
folder and Downloads.

**Coil cache (item 8).** A PortsideApplication ImageLoaderFactory
gives Coil a 25% memory cache, a 150 MB disk cache for cover art,
RGB_565, and a 150 ms crossfade. Detail headers reuse the poster's
memory cache key as a placeholder, so cover art appears at 0 ms
instead of a blank grey box.

**Ambient background (item 9).** The selected game's cached cover
art is drawn behind the Library and detail screens — 128x128
downsample, heavy GPU blur, dark gradient scrim, alpha 0.22. Theme
colors and content layout are unchanged.

**Folded Library is a 2-column grid (item 10).** The single
horizontal shelf of 529 games (with the bottom 60% of the cover
screen empty and a spacer just to make the top bar collapsible) is
replaced by a 2-column LazyVerticalGrid matching the unfolded grid,
with a compact "Ready to play" shelf of installed games at the top.
The top bar still collapses on scroll; poster tags ride on the
grid cards. The old GameSpine shelf card is deleted.

**Unfolded Back gesture (item 11).** A BackHandler returns the
right pane to the Game Page when a port's Details are open, instead
of exiting the app.

**R8 + version string (item 12).** Release builds now use R8
(isMinifyEnabled + isShrinkResources) with ProGuard rules keeping
the kotlinx-serialization serializers and data classes. Settings
reads BuildConfig.VERSION_NAME instead of a hardcoded "1.6.0".

Matcher rules unchanged: titleWords, ARCHIVE_PROVISIONAL,
NAME_ONLY, 90% similarity, and platform-consistency are exactly as
shipped. "Has files" still excludes magnets. The magnet copy action
is unchanged.

## v1.8.6 — 2026-11-16

Game pages load instantly, poster tags on the unfolded layout, and
magnets count for the poster tag. Three reports from Garrett's
v1.8.5 video, all in one round.

**Game pages still took ~10 seconds.** v1.8.5 made cold start faster,
but a game page still took ~10 seconds to show its files and magnets
("'Splosion Man loaded quicker, but not instant... it should almost
be just instantaneous"). Measured: the page ran
`matchServerFilesForGame` + `matchMagnetLinksForGame` in `remember`
during composition, on the main thread, over the full 26k-entry
feed — the magnet match alone is 379–837ms per run on JVM (every
magnet goes through the NAME_ONLY similarity path), ~1–4s on device,
and it re-ran on every server-listings change (disk cache load, then
fresh fetch). The live match now runs in the background
(`produceState` on `Dispatchers.Default`) and the page shows the
pairing cache immediately — cached files and magnets appear on open
with no waiting on live data, recomputation, or the stars fetch
(which was already async and never blocked the page). The live match
fills in when it lands; the merge rule is unchanged (live wins on a
name clash, cached fills gaps).

**Poster tags missing on the unfolded layout.** The file-matched /
source tag showed on the folded shelf posters but not on the
unfolded grid posters. The grid cards now carry the same status +
source tag, stacked at the bottom of the art like the shelf.

**A game with a magnet should show the poster tag.** 'Splosion
Man's only link is the Triple Pack magnet, so its poster showed no
tag. A matched magnet now counts toward the poster tag: the tag
appears when a game has a matched downloadable file OR a matched
magnet link. A magnet-only game reads "Magnet matched" (a magnet
is a copyable link, not a file, so it does not read "File
matched") and its tag shows the magnet's source with a suffix —
"json · Magnet". The "Has files" filter is unchanged — magnets
still don't count there; only the poster's display changes.

## v1.8.5 — 2026-11-16

Matching robustness and loading performance. Three reports from
Garrett, all in one round.

**`.hack//versus` matched every "versus" game.** The game page
showed "Aliens Versus Predator - Extinction", "RoboCop versus The
Terminator", "Sherlock Holmes Versus Jack the Ripper" and "Star
Versus" as magnet links, all "json · name only". The cause was in
`titleWords`: it stripped a file-path prefix with
`substringAfterLast('/')`, which also stripped the game title
".hack//versus" down to just "versus" — so every file containing
"versus" got title similarity 1.0 and matched through the
distinctive hint. A "/" in a game title is part of the title now;
only the file side strips a path prefix. The four "versus" files
are blocked at similarity 0.5, a real ".hack//Versus (USA).iso"
still matches (1.0, NAME_ONLY), and the other slash titles —
Kingdom Hearts 358/2 Days, The Void/Tension, Stronghold Crusader
HD / Extreme HD — tokenize fully. Verified over the full merged
catalog: no true positive lost (Eidos → Pokemon Black, Pokopia →
.nup, SoH → OoT .z64, PokePark Wii USA magnet, 007 → the 007 Xbox
game), and .hack//versus matches zero magnets. One known side
effect of the symmetric-overlap gate: "Kingdom Hearts (USA).zip"
and "Kingdom Hearts II (USA).zip" now match KH 358/2 Days (their
titles are subsets of the game's title, similarity 1.0) — same
franchise, and no KH1/KH2 port exists, so they have no other port
to match; the same class of ambiguity as the documented Eidos /
Pokemon Black 2 case.

**Duplicate magnet links.** The same game file name appeared 2–3
times on a game page — remote.json lists it once per system (each
system is its own torrent, so the magnet URLs differ) and the app
deduped by URL only. Magnet links are now deduped by cleaned name
per game, keeping the entry whose feed category platform matches
the game's platform (the category string encodes the system, e.g.
"Microsoft - Xbox", "Nintendo - GameCube - NKit RVZ
[zstd-19-128k]"), else the first. "007 - Agent Under Fire
(USA).zip" and "007 - Nightfire (USA).zip" each appear once, the
Xbox one. The merge also dedupes by name, so a stale v1.8.4 cache
(which carries the pre-dedup duplicates) collapses on display
before the first background refresh overwrites it. Downloadable
files were already deduped by name; magnets get the same
treatment. The pairing cache format is unchanged (version 1).

**Loading takes way too long.** The app re-fetched + re-parsed
the Recompa catalog (5.5 MB) and remote.json (11 MB, 26,710
records) on every cold start, and pairing only started once the
network landed. Both are now cached on disk and served first: the
parsed catalog (the merged port list, ready to display) and each
remote folder's parsed entries load from the app cache dir on
startup, so the Library and a game page show immediately from
cache; the catalog and remote.json still refresh in the background
and update the UI when the new data lands. A fetch that fails
leaves the cached entries in place — the list is never overwritten
with an empty list — so a game page keeps showing its cached
files and magnets while offline. The pairing cache (v1.8.2) is
unchanged. Cover art is out of scope here: Coil's disk cache
already handles repeat loads.

## v1.8.4 — 2026-11-16

Freeze/ANR fix and the ARCHIVE_PROVISIONAL tightening. The v1.8.3
game page could sit on "Checking your files…" for 13+ seconds and
then freeze the Library into an ANR: the pairing-cache save rebuilt
the cached forms and deep-compared them on the main thread, over
~24k entries, on every combine emission — and the magnet map made
that far larger than v1.8.2's file-only map. The save now runs off
Main (a cheap content fingerprint, computed on Dispatchers.Default
and compared in O(1), gates the rebuild and the store write, and
rapid emissions debounce into a single pass), and the game page
shows its cached files immediately instead of gating on the live
server listings — "Checking your files…" only appears when there
is truly nothing yet and the sources are still loading.

The archive rule is tightened too: an archive now needs a hint hit
AND (at least two of the port's hints, each 4+ chars, or a title at
least 90% explained by the game's title). A single common-word hit
no longer matches — "007 - Agent Under Fire" is gone from Fire
Emblem: The Sacred Stones, "100 Man Yen Quiz Hunter" from Mega Man
64, and "10101 - Will the Starship" from Star Fox 64, for both
magnets and downloadable files. A bare distinctive-or-two-hints bar
was swept and rejected: it lost the ports whose game title reduces
to one shared word (SoH's own ".zip", Super Mario 64's, Star Fox
64's), and it still matched "10101" through the port's id hint. The
similarity path keeps a file named after the game. Verified over
the full merged catalog: no canonical file lost (601/601 still
match), and the 3,314 dropped magnets are all single-common-word
false positives. The 90% similarity gate and the
platform-consistency rule are unchanged.

- 'Splosion Man (and every game page) shows its files — or "No
  file" — immediately from the pairing cache, with no 13-second
  "Checking your files…" hang while the remote.json loads.
- The Library stays responsive while the pairing refresh runs; no
  freeze, no ANR.
- Fire Emblem: The Sacred Stones no longer shows the 007 magnet or
  file; Eidos still shows Pokemon Black; PokePark Wii still shows
  its USA magnet.

## v1.8.3 — 2026-11-16

Magnet links on the game page. A game whose only links are magnets
no longer reads "No file for this game yet": magnet entries are now
matched to games by name through the same matcher (platform-
consistency, 90% title similarity, extension gate on the name — the
magnet: URL is never passed to the gate) and shown in their own
Magnet links section on the game page, with a Copy link action
(clipboard + "Link copied" toast). Magnets are still copy-only links,
never downloadable files: they stay out of "Your files" and out of
the "Has files" downloadable-file set, there is never a Download
action for them, and they never open in the browser. The pairing
cache carries the magnet pairings alongside the file pairings, so the
Magnet links section pre-populates on reopen the same way "Your
files" does; the remote-folder browser's Magnet chip + Copy link
behavior is unchanged. The matcher rules are unchanged
(platform-consistency, 90% similarity, and the extension gate all
still hold — verified over the full merged catalog: no downloadable
true positive lost, and the four v1.8.0 false positives stay blocked
for magnets too).

- PokéPark Wii: Pikachu's Adventure (Wii) now shows its per-game
  magnets (USA and Europe versions) on its game page, where v1.8.0
  showed nothing because magnets were excluded from matching.
- A Wii U-tagged magnet does not match a Switch game (the
  platform-consistency rule applies to magnets too).
- A magnet whose name ends in .zip still never matches as a
  downloadable file (the v1.8.0 guarantee holds).

## v1.8.2 — 2026-11-16

Library navigation, folded layout, file source tags, and a pairing
cache. The matcher rules are unchanged (platform-consistency,
90% title similarity, and the extension gate all still hold —
verified over the full merged catalog: no true positive lost).

- Navigation state: the Library's search query, platform filter, and
  "Has files" setting now live in the ViewModel (a single
  LibraryFilter flow), not in a composable's `remember` — so exiting
  a game detail page back to the Library restores the previous state
  instead of clearing the search and filter and jumping to the top.
  The list and grid scroll positions are saved too
  (`rememberSaveable` + `LazyListState.Saver` / `LazyGridState.Saver`).
- Folded layout: the selected-game preview/hero panel below the shelf
  is gone. The folded Library is a larger horizontal row of larger
  game posters (160×224dp, shelf 288dp tall). Tapping a poster opens
  the game detail page immediately — no preview step. The shelf is
  still a stickyHeader pinned under the PORTSIDE header (the top bar
  still collapses on scroll); the unfolded layout (grid + detail
  pane) is unchanged.
- File source tag: each game page file row carries a clear chip for
  where the file comes from — "Premiumize", the remote folder's
  configured name (e.g. "json"), or "Local". The shelf posters show
  the source tag too, stacked under the status ("Not installed" /
  "File matched" / "Installed" / "Ready to play"). The match kind
  (name only / direct / archive) stays as the secondary label in the
  file row subtitle.
- Pairing cache: the computed file↔game pairings are cached
  persistently on-device (DataStore, `pairing_cache`). On app open
  the cached pairings pre-populate the Library and "Has files"
  immediately; in the background the app re-runs the matching when
  the Recompa catalog, the Premiumize library, or a JSON remote
  folder changes, and overwrites the cache with the new list (which
  becomes the new default pre-populated cache). The background
  refresh never blocks the UI or the pre-populated display, and a
  partial refresh never wipes the cached pairings — the cache is
  only overwritten once every source has settled. A cache-version
  key (`PAIRING_CACHE_VERSION`) forces a re-compute when a matcher
  rule changes, so a stale cache built under old rules can never
  survive. The Premiumize download link is never cached (it is
  re-fetched on demand). Local-only, never synced.

## v1.8.1 — 2026-11-16

Platform consistency for file matching. A file whose name carries
a parenthesized platform tag for a different platform than the
game's — "(Wii U)" on a Switch port's file — never matches, on any
match kind (DIRECT, ARCHIVE_*, NAME_ONLY). The tag is read from the
file name, falling back to the URL's last path segment when the name
has none; a file with no recognizable platform tag, and a port whose
platform is unknown, are unaffected. Region tags (USA, EUR, JPN)
are not platform tags and never block, and neither do bracketed
groups ([0400...] title IDs). A parenthesized group that appears in
the game's own title is excluded — it is the title talking, not
the file: "James Pond 2: Robocod (Amiga)" is an N64 port of an
Amiga game and a file named after it still matches, while a real
file tag after the title's group ("Tyrian (DOS) (Wii U)") still
vetoes. Verified over the full merged catalog (536 ports): no
previously-matching true positive is lost, and 371 wrong-platform
files that used to match are now blocked. Upgrades in place over
the v1.8.0 release build (same signature).

- Matcher (GameExtensions, PortMatcher): PLATFORM_FILENAME_TAGS
  maps common filename platform tags to the Recompa catalog's
  platform ids (Wii U → wiiu, NDS → ds, Switch → switch, GC →
  gamecube, X360 → xbox360, and the rest of the 28 catalog ids);
  "(SAT)" is deliberately unmapped — the catalog has no saturn
  platform id. platformTagFromFileName / platformTagFromUrl /
  isPlatformConsistent are pure and unit-tested; the check is a
  precondition in matches() and matchKind(), like the v1.6.1
  game-file gate.
- Fixes: Breath of the Wild (Switch) no longer pairs with the
  "Legend of Zelda Breath of the Wild (Wii U) (USA)" .7z. Eidos
  still matches "Pokemon Black (NDS) (USA)", Pokopia still matches
  its .nup, OoT still matches its .z64.

## v1.8.0 — 2026-11-16

Magnet links, from Minerva. A remote folder's JSON can now carry
magnet entries (kind "magnet", a full magnet: URI in `url`), and the
app shows them in the remote folder browser with a Magnet chip; tap
one and the link is copied to the clipboard ("Link copied") — it
never opens in the browser and never downloads. Magnet entries are
never game files: they never match a port, never appear in a game's
"Your files", and never count toward "Has files". The remote folder
browser shows game files AND magnet entries (folders kept). The
combined adapter (json-adapter.py) keeps the r-roms direct-link
crawl exactly as-is and adds a Minerva magnet crawl: it scrapes the
Minerva browse page at ADAPTER_MINERVA_URL (a folder page gives one
magnet per system, a system page one per game; the root browse page
recurses one level into its Redump and No-Intro subfolders), rebuilds
each entry's magnet with the real name as dn plus the three trackers,
and emits one record per entry ({name, url: full magnet URI, kind:
"magnet", category, size?}) into the same combined bare JSON array.
A collection split across several torrents (IBM - PC compatible)
becomes one record per torrent, named "<name> (part N of M)". The
CDN .torrent approach is dead — cdn.minerva-archive.org/torrents/
404s — so there is no bencodepy and no .torrent decoding; the
adapter stays standard-library only. Upgrades in place over the
v1.7.0 release build (same signature).

- App (RemoteDirectory): the remote-folder JSON parser reads an
  optional "kind" on each entry ("magnet"/"file", absent means
  "file") and treats any magnet: URL as a magnet entry (scheme
  fallback); a magnet: URI is used as-is, never resolved against
  the feed URL.
- App (browser): a magnet entry shows a Magnet chip instead of an
  extension chip and a "Copy link" button (clipboard + "Link
  copied" toast, "Link unavailable" when there is nothing to
  copy); no Download button for magnets.
- App (matching): the v1.6.1 gate excludes a magnet: URL before
  checking name/extension, and the remote call sites skip
  entry.isMagnet — a magnet entry whose name ends in .zip still
  matches nothing, in any source.
- App (display filter): isGameFileRemoteEntry keeps folders, game
  files, and magnet entries.
- Adapter (json-adapter.py): new Minerva browse-page scraper
  (--minerva, or ADAPTER_MINERVA_URL); the r-roms crawl, dedup,
  and sorting are unchanged.
- Workflow (adapter.yml): ADAPTER_MINERVA_URL is passed through
  as --minerva when set; without it the run is r-roms only.
- Matcher (NAME_ONLY, 90% title-similarity gate): a name-only
  match now also requires the file's title to be at least 90%
  explained by the game's title (the overlap coefficient —
  |intersection| / min(|file|, |game|) — on significant words:
  extension and (…)/[…] tags stripped, diacritics folded, words
  of 3+ letters minus stopwords and generic tokens). Garrett's
  four single-word false positives are blocked ("Pokemon Blue"
  vs Blue Dragon, "Pokemon Gold" vs Marble Blast Gold, "Mario
  Kart Double Dash" vs Splinter Cell: Double Agent, "Sega -
  Master System - Mark III" vs System Shock — 0.5, 0.5, 0.25,
  0.5), while Eidos ("Pokemon Black", "Pokemon Black 2") and
  Pokopia still match at 1.0. The gate is symmetric, so it keeps
  both the file-subset case (Eidos) and the game-subset case
  (Pokopia, where the game title is shorter than the file title);
  a one-directional file→game containment would have broken
  Pokopia. DIRECT matches are unchanged — the gate applies only
  to NAME_ONLY. Verified over the live Recompa catalog (536
  ports, 464 format-less): a file named after its own game still
  matches under the new rule for all 822 canonical cases, zero
  lost. The sweep caught a real bug: stripping the extension at
  the last dot truncated dotted titles ("S.T.A.L.K.E.R.",
  "Dr. Robotnik's..."), leaving no significant words (0.0) —
  the strip now only fires when the suffix looks like an
  extension (short, alphanumeric, has a letter).

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

