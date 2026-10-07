# TelePremium

TelePremium is a custom Android Telegram client built from a pinned official Telegram Android source baseline. The project keeps Telegram protocol compatibility while adding client-owned functionality such as ten-account infrastructure, per-account network routing, local appearance features, local voice transcription, a dedicated TelePremium center, and reliability fixes.

> TelePremium does **not** spoof Telegram server-side Premium entitlement. Features implemented locally stay local unless Telegram itself supports the corresponding server operation for the account.

## Current baseline

- **Application ID:** `com.telepremium.messenger`
- **Internal Java namespace:** `org.telegram.*`
- **Upstream:** official `DrKLO/Telegram`
- **Pinned upstream release:** Telegram Android **11.4.2 (5469)**
- **Pinned upstream commit:** `fb2e545101f41303f1e2712de2e7611a9335f1c3`
- **Minimum Android version:** Android 5.0 / API 21
- **Account slots:** 10 in Java and native tgnet
- **In-app APK updater:** disabled
- **Telegram protocol/config/DC updates:** preserved
- **Default TelePremium UI scale:** 92%

## Implemented features

### Ten-account infrastructure

TelePremium expands Telegram's account infrastructure to **10 independent account slots**.

- `UserConfig.MAX_ACCOUNT_DEFAULT_COUNT = 10`
- `UserConfig.MAX_ACCOUNT_COUNT = 10`
- Native tgnet `MAX_ACCOUNT_COUNT = 10`
- Independent native `ConnectionsManager` instances for slots 0–9
- Dedicated **Account Center** for account management and switching

The Java and native limits are changed together to avoid the unsafe state where Java accepts account indexes that native tgnet cannot represent.

### Per-account network routing

TelePremium adds account-scoped network profiles without removing Telegram's normal global proxy configuration.

Each account can use:

- inherited global Telegram network settings;
- MTProto proxy;
- SOCKS5 proxy.

Explicit account profiles override the global proxy only for the selected account. The Network Center provides profile editing, validation, saving and immediate application.

Main components:

- `AccountNetworkProfile`
- `AccountNetworkStore`
- `AccountNetworkController`
- `TelePremiumNetworkCenterActivity`
- `TelePremiumNetworkProfileActivity`

### Authentication

The normal phone-number login remains the primary authentication flow.

Additional login methods are available from the three-dot menu:

- Telegram QR login;
- Passkey login through Android Credential Manager.

Because TelePremium has its own package/signing identity, the official Telegram Firebase / Play Integrity SMS verification path is disabled. The client requests Telegram's normal fallback authentication path instead.

Login handling also contains stability fixes for restored/autofilled phone numbers and country-code initialization.

### Translation

TelePremium keeps Telegram's native translation transport and `TranslateController`.

- Per-message translation control is enabled by default.
- The client-side Premium gate for whole-chat translation is removed.
- Telegram server-side checks are not spoofed or bypassed.

If Telegram rejects an operation server-side, TelePremium handles that normally.

### No sponsored messages

TelePremium disables Telegram sponsored-message handling at two levels:

1. sponsored-message fetching returns no local sponsored-message data;
2. sponsored-message rendering in chats is disabled.

This does not mark the Telegram account itself as Premium.

### Local Voice-to-Text

Downloaded voice messages can be transcribed locally without depending on Telegram Premium transcription.

On supported Android versions TelePremium:

1. decodes the Telegram audio stream;
2. produces PCM audio;
3. injects the audio into Android's system speech recognizer;
4. writes the resulting transcription into the local message state.

The current implementation targets **Android 13+** for the local recognizer audio-injection path.

### Local appearance features

TelePremium provides client-owned appearance overrides without sending fake Premium account state to Telegram.

Implemented locally:

- name color;
- name background emoji;
- profile color;
- profile background emoji;
- emoji status;
- bundled launcher icons;
- animated emoji picker unlocks;
- configurable double-tap reaction.

User appearance overrides are stored by TelePremium. User emoji status does not send `account.updateEmojiStatus`.

These settings are therefore not equivalent to server-side Telegram Premium appearance and may only be visible inside TelePremium.

### TelePremium instead of Telegram Premium upsell

Official Telegram Premium preview/purchase surfaces are isolated from the TelePremium UI.

- Telegram Premium preview navigation is redirected to the TelePremium overview.
- Premium purchase buttons are hidden from the normal TelePremium flow.
- Premium lock chrome is removed from supported local-only features.
- Telegram Stars flows are left intact.
- Telegram server entitlement is never forged.

The main settings entry is presented as **TelePremium** rather than Telegram Premium.

### TelePremium Center

The project contains a dedicated client-management UI.

Current sections include:

- TelePremium overview;
- Account Center;
- Network Center;
- File Center;
- Appearance;
- interface scale;
- local feature shortcuts.

The stock Telegram Business settings row is removed from the current TelePremium settings layout.

### Interface scaling

TelePremium supports app-wide UI scaling by adjusting Android `density` and `scaledDensity`.

- Default scale: **92%**
- Changes are applied application-wide.
- The activity is recreated when the scale changes.

Implementation:

- `TelePremiumAppearanceActivity`
- `TelePremiumUiScale`

### File handling and File Center

TelePremium contains file-opening reliability fixes and a dedicated File Center.

Implemented fixes:

- FileProvider authority follows the runtime application ID.
- The hard-coded `/data/data/org.telegram.messenger/files` fallback is removed.
- Runtime paths use the TelePremium application ID.
- Unknown extensions fall back to `*/*` instead of being forced to `text/plain`.
- Android MediaStore and Downloads behavior is preserved.

The UI includes:

- `TelePremiumFileCenterActivity`
- `TelePremiumDownloadsActivity`

### APK update isolation

Telegram's built-in APK updater is intentionally frozen:

- `LaunchActivity.checkAppUpdate()` performs no APK update;
- `SharedConfig.isAppUpdateAvailable()` returns false.

This affects only Telegram's application APK updater. Telegram protocol configuration, DC configuration and normal network operation remain enabled.

## TelePremium Cloud FS

File Center now contains the first persistent TelePremium Cloud FS implementation.

- Account-scoped cloud hierarchy.
- Telegram Saved Messages is used as the durable metadata backend.
- Automatic bootstrap after login.
- Stable UUID nodes and nested virtual folders.
- Persistent rename, favorites and trash state.
- Cloud generations are recovered without relying on local application data.
- The hierarchy can be rediscovered after clearing app data or reinstalling and signing back into the same Telegram account.

Architecture and implementation status: `docs/CLOUD_FS.md`.

## Source layout

- `upstream/Telegram` — pinned official Telegram Android source.
- `scripts/apply_telepremium_patches.py` — applies the stable TelePremium source changes.
- `scripts/apply_concept5_patches.py` — applies the current TelePremium feature/UI layer.
- `scripts/verify_source.py` — verifies critical source invariants.
- `patches/concept5/java` — TelePremium Concept 5 Java sources.
- `docs/UPSTREAM.md` — upstream source baseline and version rationale.
- `docs/PASS_HISTORY.md` — Pass 1–4 history.

## Source verification

`scripts/verify_source.py` checks critical implementation details, including:

- application ID;
- 10-account Java/native agreement;
- independent native account instances;
- frozen APK updater;
- translation configuration;
- no-sponsored-message hooks;
- account network routing;
- login fallback configuration;
- local Voice-to-Text;
- local launcher icons;
- local appearance and emoji-status storage;
- double-tap reaction;
- Premium routing/isolation;
- TelePremium Center;
- File Center;
- UI scaling.

Run verification after applying patches and before producing a release build.

## Bootstrap

```bash
git clone --recurse-submodules git@github.com:mallstorm/Telegram-Premium.git
cd Telegram-Premium

python3 scripts/apply_telepremium_patches.py
python3 scripts/verify_source.py
```

The upstream submodule is deliberately pinned. Do not update it casually: TelePremium source transformations are validated against the exact upstream revision.

## Version lineage

The earlier working control APK identifies itself as **TelePremium 11.3.4**.

Telegram's public Android source history does not provide a separate 11.3.4 source tag/commit in this interval; the public history moves from 11.2.3 to 11.4.2.

For that reason the reproducible source project deliberately uses the nearest later official source baseline, **Telegram Android 11.4.2 (5469)**, rather than claiming an exact 11.3.4 source tree.

A build produced from this repository is therefore not claimed to be byte-for-byte identical to the earlier patched 11.3.4 APK.

## Update policy

Updating the Telegram upstream baseline is a controlled migration:

1. move the upstream gitlink;
2. review upstream changes affecting patched methods;
3. update TelePremium transformations where necessary;
4. apply all patches;
5. run `scripts/verify_source.py`;
6. compile the client;
7. regression-test authentication, chat sync, media, files, translation, local features, network profiles and all ten account slots.

Do not silently move the upstream submodule to a newer Telegram revision.

## Server-side limitations

TelePremium deliberately separates **local client features** from **Telegram server entitlements**.

It does not:

- set the Telegram account's Premium flag;
- forge Premium authorization;
- promise server-side Premium limits;
- bypass server-enforced Telegram restrictions.

Local appearance, local emoji status, local transcription and similar features are TelePremium client functionality. Their availability does not imply that the Telegram account has an active Telegram Premium subscription.

## License

Telegram Android is licensed under GPLv2-or-later. TelePremium modifications are maintained under the same terms.
