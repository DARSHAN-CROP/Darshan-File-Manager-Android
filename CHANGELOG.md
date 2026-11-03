# Changelog

All notable changes to **Darshan File Manager for Android** are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

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

[1.0]: https://github.com/DARSHAN-CROP/Darshan-File-Manager-Android/releases/tag/v1.0
