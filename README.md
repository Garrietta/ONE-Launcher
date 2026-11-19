<p align="center"><img src="docs/icon.png" width="128" alt="Marquee icon"></p>

# Marquee — a launcher for native Android game ports

*(formerly Portside)*

Marquee is a dark, cinematic game-library launcher for native Android
game ports and recompilations, built foldable-first for the Samsung Galaxy
Z Fold 8 (and any Android device). It organizes the games you already own,
matches your own legally dumped game files to the right port, and launches
the port app you already installed.

Marquee is touch-first: there are no gamepad button hints and no
navigation bar. Your games stand on a shelf as spines; tap one to pull
it forward as a full cover, tap the cover to open it, or swipe across
the title to step through the shelf. The whole screen takes on the
selected game's console color. On wide windows (tablets, unfolded
foldables, phones in landscape) a console rail sits on the left; on
narrow ones the consoles are a swipeable row of tabs.

## What Marquee does

- **Library** — a curated catalog of native ports (`app/src/main/assets/ports.json`):
  Ship of Harkinian, 2Ship2Harkinian, Starship, Zelda 64: Recompiled, Zelda3 —
  plus the Recompa catalog (recompa.app), hundreds of ports with cover art.
  The library is a shelf of covers; the selected game is shown large with
  its title, install status, whether one of your files matches it, and
  Play / Details actions.
- **Game detail** — what dump the port needs, accepted filename patterns,
  a link to the port project's GitHub Releases page, install status, and a
  Launch button when the port app is installed. Per-port preferences
  (resolution scale, FPS cap, touch-layout note) are stored on-device.
- **Locate my file** — pick one of your own files with the system file
  picker. Marquee stores the file name and matches it against the catalog. It
  never deletes, moves, or renames your files.
- **Cloud (Premiumize, own-cloud only)** — link your own Premiumize account
  with your API key and browse, search, and download files **already in your
  own Premiumize cloud** to this device. Downloads go to the app's own
  `Downloads/ONE` folder and are then matched against the catalog.

## What Marquee does not do

- Marquee contains **no game files, ROMs, or copyrighted assets**, and it never
  downloads any. You must supply your own legally dumped files.
- Marquee does **not** bundle or fetch any port APK. It links to each port
  project's GitHub Releases page and launches a port app you installed
  yourself.
- Premiumize access is **own-cloud only**: folder list, own-cloud search,
  item details, and downloading a file already in your cloud. There is no
  torrent/magnet support, no hoster-link resolver, no transfer creation, and
  no public/debrid cache search. There is no "download this game from the
  internet" button.
- Your Premiumize API key is stored only in EncryptedSharedPreferences on
  your device. It is never logged, never written to a file, and never leaves
  the device except in requests to premiumize.me.

## Legal notice

Marquee is not affiliated with Nintendo, HarbourMasters, Premiumize, or any port
author. The ports listed are the work of their own authors — see each
project's repository linked in the app. You are responsible for only using
game files you legally own and dumped yourself.

## Building

```sh
./gradlew assembleDebug
```

Requires JDK 17. CI runs the unit tests and builds the signed release APK
on every push to `main`, verifies its signature, and uploads it as the
`one-release-apk` artifact.

## Known limits

- Launch detection relies on the port app's package name. Where a package
  name is unverified, the entry is catalog + file-match only and the app says
  so instead of guessing.
- FPS cap / resolution scale are stored as preferences for the port; Marquee does
  not enforce settings inside the port app itself.
