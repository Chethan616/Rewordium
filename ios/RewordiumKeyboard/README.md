# RewordiumKeyboard target

Thin Custom Keyboard Extension that embeds AzooKey unchanged.

## What lives here

* `Info.plist` — extension manifest. Bundle ID is set by Xcode build settings
  to `com.noxquill.rewordium.keyboard`. `RequestsOpenAccess=true` is required
  so the extension can read the App Group used by AzooKey.
* `RewordiumKeyboard.entitlements` — App Group membership in
  `group.com.azooKey.keyboard` (AzooKey's hard-coded suite — preserved to keep
  upstream behavior identical).
* `PrivacyInfo.xcprivacy` — copy of AzooKey's privacy manifest.

There is **no Swift source in this folder.** The actual keyboard sources live
at `ios/Packages/azooKey/Keyboard/` (read-only upstream) and are pulled into
this target's *Compile Sources* build phase by `ios/scripts/setup_keyboard_target.rb`.

## Wiring

The pbxproj is wired by a Ruby script — it is **not** committed by Xcode UI.

Run on CI (already in `codemagic.yaml`) or manually on a Mac:

```sh
gem install xcodeproj           # one-time
cd ios
ruby scripts/setup_keyboard_target.rb
```

The script is idempotent: re-running it after a clean clone produces the same
project file. If it ever fails, delete the `RewordiumKeyboard` target from
`Runner.xcodeproj` in Xcode and re-run.

## What the extension shows

For Phase 2, the keyboard renders the **original AzooKey UI** — Japanese
candidate bar, kana/romaji input, conversion, themes. No Rewordium UI yet.
Rewordium AI actions land in Phase 4 per `AZOOKEY_IOS_INTEGRATION.md`.
