# Pass history

## Pass 1 / 1C — package and file opening stability

- Separate Android application ID: `com.telepremium.messenger`.
- Keep internal Java classes under `org.telegram.*`.
- FileProvider authority follows the runtime application ID.
- Freeze the internal APK updater without disabling Telegram network/config updates.
- Preserve DEX instruction integrity in the legacy patched APK line.

## Pass 2 — file reliability

- Unknown file types use a generic MIME fallback rather than being forced to plain text.
- Remove dependency on a hard-coded `/data/data/org.telegram.messenger/files` fallback.
- Keep Android MediaStore/Downloads behavior.
- Clean package-renaming artifacts in the patched APK line.

## Pass 3 — translation

- Enable Telegram's message translation control by default.
- Keep whole-chat translation on the native `TranslateController`.
- Do not bypass Telegram Premium checks.

## Pass 4 — source repository and ten accounts

Source implementation moves to this repository.

The account infrastructure is expanded to ten slots at compile time:
- Java: `UserConfig.MAX_ACCOUNT_DEFAULT_COUNT = 10`
- Java: `UserConfig.MAX_ACCOUNT_COUNT = 10`
- Native: `MAX_ACCOUNT_COUNT = 10`
- Native: `ConnectionsManager::getInstance()` receives independent instances 0..9.

This avoids the unsafe binary-only approach where Java arrays and native tgnet disagree about the valid account range.
