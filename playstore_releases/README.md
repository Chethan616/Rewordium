# Play Store Release Artifacts

This folder contains release notes and changelogs prepared for publishing to the Google Play Console.

Files:

- `release_notes_en-US.txt` — Short "What's new" text suitable for the Play Console "What’s new" field (en-US).
- `release_notes_full_en-US.md` — Longer, formatted changelog with details, highlights, and developer notes.

How to use:

1. Open `release_notes_en-US.txt` and copy its contents into the Play Console "What’s new" (short release notes) field for the corresponding app bundle / release track.
2. Optionally paste `release_notes_full_en-US.md` into the Play Console release description or your website/release blog if you want to show a fuller changelog.

Version information in these files is derived from `pubspec.yaml` at the time of generation. If you bump the version or build number, update the notes accordingly.

Generated on: 2025-10-12

----

If you want other locales added (e.g., `en-GB`, `fr-FR`, `de-DE`) I can generate localized translations as well.
