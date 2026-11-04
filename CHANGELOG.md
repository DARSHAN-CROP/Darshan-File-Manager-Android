# Changelog

All notable changes to **Darshan File Manager for Android** are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [2.0] - 2026-11-04
### Added
- Theme: Light, Dark or System (follows the phone's night mode), saved.
- App color: Maroon (default), Gold, Blue, Green, Purple, Orange. Buttons, headers and highlights follow it; text contrast is checked for every combination (dark text on Gold and Orange).
- Duplicate file finder: groups files by size, then MD5; shows wasted space; chosen copies move to a `Duplicates` folder (never deleted, undo-able).
- Custom rules: "extension(s) → folder" or "name contains → folder", applied before the built-in type map; add, edit, delete.
- Old files cleaner: files not modified for 30 / 60 / 90 / custom days move to `Old Files`, with a preview first; undo-able.
- Sort by Date: Year/Month folders by modified date (`2026/11-November`); undo-able.
- Storage usage: count and size per category with a bar chart.
- Batch rename: filter by name / extension; prefix, suffix, find → replace, numbering (`Photo_001`); preview first; undo-able.
- Language: English / ગુજરાતી for every screen, switches instantly.
- Notifications after Auto mode ("12 sorted, 3 duplicates moved, 5 old files moved"), optional after a manual sort; asks for the notification permission on Android 13+.
- Auto mode: choose what runs automatically (custom rules, type/extension sort, date sort, old files, duplicates). Default: custom rules + type sort.
- 57 new unit tests (72 in total) and screenshots of every section in Light and Dark.

### Changed
- New layout with four sections: Sort, Tools, Stats, Settings.
- "Undo last sort" is now "Undo last action" and also covers duplicates, old files and renames.
- versionCode 3, versionName 2.0. Signed with the same key as 1.0, so it installs over it.

## [1.0] - 2026-11-03
### Added
- Sort by Type (14 categories, 400+ extensions) and Sort by Extension (keeps `.tar.gz` together).
- Date Folder (`DD-MM-YYYY`).
- Undo last sort.
- Auto mode every 15 minutes with JobScheduler, keeps running after a restart.
- Choose any folder in internal storage; activity log.
- All files access on Android 11+, normal storage permission on Android 7 to 10.
- Unit tests with JUnit 4 and Robolectric.

### Security
- Release signing reads from a local `keystore.properties` that is never committed.

[2.0]: https://github.com/DARSHAN-CROP/Darshan-File-Manager-Android/releases/tag/v2.0
[1.0]: https://github.com/DARSHAN-CROP/Darshan-File-Manager-Android/releases/tag/v1.0
