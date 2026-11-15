# Changelog

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
