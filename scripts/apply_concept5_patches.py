#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "upstream" / "Telegram"
PATCH_ROOT = ROOT / "patches" / "concept5" / "java"


def read(rel):
    p = SRC / rel
    if not p.exists():
        raise SystemExit(f"missing upstream file: {rel}")
    return p, p.read_text(encoding="utf-8")


def write(p, data):
    p.write_text(data, encoding="utf-8")


def replace_once(rel, old, new):
    p, data = read(rel)
    if new in data and old not in data:
        print(f"already patched: {rel}")
        return
    count = data.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected exactly one source match, found {count}")
    write(p, data.replace(old, new, 1))
    print(f"patched: {rel}")


def replace_exact(rel, old, new, expected):
    p, data = read(rel)
    if new in data and old not in data:
        print(f"already patched: {rel}")
        return
    count = data.count(old)
    if count != expected:
        raise SystemExit(f"{rel}: expected {expected} source matches, found {count}")
    write(p, data.replace(old, new))
    print(f"patched: {rel}")


def method_bounds(data, signature):
    start = data.find(signature)
    if start < 0:
        raise SystemExit(f"method signature not found: {signature}")
    brace = data.find("{", start)
    if brace < 0:
        raise SystemExit("opening brace not found")
    depth = 0
    for i in range(brace, len(data)):
        if data[i] == "{":
            depth += 1
        elif data[i] == "}":
            depth -= 1
            if depth == 0:
                return start, brace, i
    raise SystemExit("closing brace not found")


def replace_in_method(rel, signature, old, new, expected=1):
    p, data = read(rel)
    start, brace, end = method_bounds(data, signature)
    method = data[start:end + 1]
    if new in method and old not in method:
        print(f"already patched: {rel} :: {signature}")
        return
    count = method.count(old)
    if count != expected:
        raise SystemExit(
            f"{rel} :: {signature}: expected {expected} matches, found {count}"
        )
    method = method.replace(old, new, expected)
    write(p, data[:start] + method + data[end + 1:])
    print(f"patched: {rel} :: {signature}")


def copy_concept_sources():
    if not PATCH_ROOT.exists():
        raise SystemExit(f"Concept 5 source tree missing: {PATCH_ROOT}")
    destination = SRC / "TMessagesProj" / "src" / "main" / "java"
    for source in PATCH_ROOT.rglob("*.java"):
        rel = source.relative_to(PATCH_ROOT)
        target = destination / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        print(f"copied: {rel}")


if not SRC.exists():
    raise SystemExit(
        "upstream/Telegram is missing. Clone with --recurse-submodules "
        "or run: git submodule update --init --recursive"
    )

copy_concept_sources()

# Login face: keep phone number as the primary screen and expose the two
# alternative methods only from the agreed three-dot menu.
replace_once(
    "TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java",
    "import org.telegram.messenger.Utilities;\n",
    "import org.telegram.messenger.Utilities;\nimport org.telegram.messenger.telepremium.TelePremiumPasskeyController;\n",
)

replace_once(
    "TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java",
    """            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);

            titleView = new TextView(context);
""",
    """            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);

            if (activityMode == MODE_LOGIN) {
                TextView alternativeLoginMenu = new TextView(context);
                alternativeLoginMenu.setText("⋯");
                alternativeLoginMenu.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
                alternativeLoginMenu.setGravity(Gravity.CENTER);
                alternativeLoginMenu.setContentDescription("Другие способы входа");
                alternativeLoginMenu.setOnClickListener(v -> showAlternativeLoginMenu());
                LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(52), AndroidUtilities.dp(48));
                menuParams.gravity = Gravity.START;
                addView(alternativeLoginMenu, menuParams);
            }

            titleView = new TextView(context);
""",
)

replace_once(
    "TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java",
    """        @Override
        public String getHeaderName() {
            return getString("YourPhone", R.string.YourPhone);
        }
""",
    """        private void showAlternativeLoginMenu() {
            new AlertDialog.Builder(getParentActivity())
                    .setItems(new CharSequence[]{"Войти по QR-коду", "Войти с Passkey"}, (dialog, which) -> {
                        if (which == 0) {
                            new TelePremiumQrLoginDialog(getParentActivity(), currentAccount, (auth, error) -> {
                                if (error != null) {
                                    needShowAlert("QR-код", error);
                                } else if (auth != null) {
                                    onAuthSuccess(auth);
                                }
                            }).show();
                        } else {
                            requestTelePremiumPasskey();
                        }
                    })
                    .show();
        }

        private Runnable cancelTelePremiumPasskey;
        private void requestTelePremiumPasskey() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                needShowAlert("Passkey", "Для входа с Passkey требуется Android 9 или новее.");
                return;
            }
            if (cancelTelePremiumPasskey != null) return;
            cancelTelePremiumPasskey = TelePremiumPasskeyController.login(getParentActivity(), currentAccount, (userId, auth, error) -> {
                cancelTelePremiumPasskey = null;
                if ("CANCELLED".equals(error) || "EMPTY".equals(error)) return;
                if (error != null && error.contains("SESSION_PASSWORD_NEEDED")) {
                    TLRPC.TL_account_getPassword req = new TLRPC.TL_account_getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, requestError) -> AndroidUtilities.runOnUIThread(() -> {
                        if (requestError == null) {
                            TLRPC.account_Password password = (TLRPC.account_Password) response;
                            if (!TwoStepVerificationActivity.canHandleCurrentPassword(password, true)) {
                                AlertsCreator.showUpdateAppAlert(getParentActivity(), getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                                return;
                            }
                            Bundle bundle = new Bundle();
                            SerializedData data = new SerializedData(password.getObjectSize());
                            password.serializeToStream(data);
                            bundle.putString("password", Utilities.bytesToHex(data.toByteArray()));
                            setPage(VIEW_PASSWORD, true, bundle, false);
                        } else {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), requestError.text);
                        }
                    }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                } else if (error != null) {
                    needShowAlert("Passkey", error);
                } else if (auth instanceof TLRPC.TL_auth_authorization) {
                    onAuthSuccess((TLRPC.TL_auth_authorization) auth);
                }
            });
        }

        @Override
        public String getHeaderName() {
            return getString("YourPhone", R.string.YourPhone);
        }
""",
)

# Isolated authentication backport dependencies: Android Credential Manager for
# native passkeys and ZXing core for the dynamic Telegram QR token.
replace_once(
    "TMessagesProj/build.gradle",
    "dependencies {\n",
    "dependencies {\n    implementation 'androidx.credentials:credentials:1.3.0'\n    implementation 'androidx.credentials:credentials-play-services-auth:1.3.0'\n",
)

replace_once(
    "TMessagesProj/build.gradle",
    "        minSdkVersion 19\n",
    "        minSdkVersion 21\n",
)

replace_once(
    "TMessagesProj_App/build.gradle",
    "        minSdkVersion 19\n",
    "        minSdkVersion 21\n",
)

# Per-account network routing. Stock Telegram still owns the global proxy
# settings, but an explicit TelePremium profile overrides it only for its
# account slot.
connections = "TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java"

replace_once(
    connections,
    "import org.telegram.messenger.UserConfig;\n",
    "import org.telegram.messenger.UserConfig;\n"
    "import org.telegram.messenger.telepremium.AccountNetworkController;\n",
)

replace_in_method(
    connections,
    "public void init(int version, int layer, int apiId, String deviceModel, String systemVersion, String appVersion, String langCode, String systemLangCode, String configPath, String logPath, String regId, String cFingerprint, int timezoneOffset, long userId, boolean userPremium, boolean enablePushConnection)",
    "        checkConnection();",
    "        AccountNetworkController.getInstance().applyProfile(currentAccount);\n"
    "        checkConnection();",
)

old_proxy_loop = """        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (enabled && !TextUtils.isEmpty(address)) {
                native_setProxySettings(a, address, port, username, password, secret);
            } else {
                native_setProxySettings(a, "", 1080, "", "", "");
            }
            AccountInstance accountInstance = AccountInstance.getInstance(a);
            if (accountInstance.getUserConfig().isClientActivated()) {
                accountInstance.getMessagesController().checkPromoInfo(true);
            }
        }"""

new_proxy_loop = """        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (AccountNetworkController.getInstance().hasExplicitProfile(a)) {
                AccountNetworkController.getInstance().applyProfile(a);
            } else if (enabled && !TextUtils.isEmpty(address)) {
                native_setProxySettings(a, address, port, username, password, secret);
            } else {
                native_setProxySettings(a, "", 1080, "", "", "");
            }
            AccountInstance accountInstance = AccountInstance.getInstance(a);
            if (accountInstance.getUserConfig().isClientActivated()) {
                accountInstance.getMessagesController().checkPromoInfo(true);
            }
        }"""

replace_in_method(
    connections,
    "public static void setProxySettings(boolean enabled, String address, int port, String username, String password, String secret)",
    """        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (enabled && !TextUtils.isEmpty(address)) {
                native_setProxySettings(a, address, port, username, password, secret);
            } else {
                native_setProxySettings(a, "", 1080, "", "", "");
            }
            AccountInstance accountInstance = AccountInstance.getInstance(a);
            if (accountInstance.getUserConfig().isClientActivated()) {
                accountInstance.getMessagesController().checkPromoInfo(true);
            }
        }""",
    """        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (AccountNetworkController.getInstance().hasExplicitProfile(a)) {
                AccountNetworkController.getInstance().applyProfile(a);
            } else if (enabled && !TextUtils.isEmpty(address)) {
                native_setProxySettings(a, address, port, username, password, secret);
            } else {
                native_setProxySettings(a, "", 1080, "", "", "");
            }
            AccountInstance accountInstance = AccountInstance.getInstance(a);
            if (accountInstance.getUserConfig().isClientActivated()) {
                accountInstance.getMessagesController().checkPromoInfo(true);
            }
        }""",
)

# TelePremium is signed and packaged independently from the official Telegram
# Play Store app. The official Firebase SMS / Play Integrity path is bound to
# Telegram's production package/signing identity, so a fork must request the
# normal Telegram login-code fallback instead of advertising Firebase/app-hash
# verification that it cannot satisfy.
login = "TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java"

replace_once(
    login,
    "            settings.allow_app_hash = settings.allow_firebase = PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices();\n",
    "            settings.allow_app_hash = false;\n"
    "            settings.allow_firebase = false;\n",
)

# Login stability: a restored/autofilled phone number can be present before
# the country-code maps have finished initializing. Revalidate the country code
# immediately before send, reset confirmation whenever the visible number
# changes, and never treat a SIM-autofilled number as implicitly confirmed.
login = "TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java"

replace_in_method(
    login,
    "public PhoneView(Context context)",
    "                public void afterTextChanged(Editable editable) {\n"
    "                    if (ignoreOnTextChange) {\n"
    "                        return;\n"
    "                    }",
    "                public void afterTextChanged(Editable editable) {\n"
    "                    if (ignoreOnTextChange) {\n"
    "                        return;\n"
    "                    }\n"
    "                    confirmedNumber = false;",
)

replace_in_method(
    login,
    "public PhoneView(Context context)",
    "                public void afterTextChanged(Editable s) {\n"
    "                    if (ignoreOnPhoneChange) {\n"
    "                        return;\n"
    "                    }",
    "                public void afterTextChanged(Editable s) {\n"
    "                    if (ignoreOnPhoneChange) {\n"
    "                        return;\n"
    "                    }\n"
    "                    confirmedNumber = false;",
)

replace_in_method(
    login,
    "public void fillNumber()",
    "                            confirmedNumber = true;",
    "                            confirmedNumber = false;",
)

replace_in_method(
    login,
    "public void onNextPressed(String code)",
    "            if (countryState == COUNTRY_STATE_EMPTY) {",
    "            String normalizedCountryCode = PhoneFormat.stripExceptNumbers(codeField.getText().toString());\n"
    "            List<CountrySelectActivity.Country> currentCodeCountries = codesMap.get(normalizedCountryCode);\n"
    "            if (currentCodeCountries != null && !currentCodeCountries.isEmpty()) {\n"
    "                countryState = COUNTRY_STATE_NOT_SET_OR_VALID;\n"
    "                if (currentCountry == null || !TextUtils.equals(currentCountry.code, normalizedCountryCode)) {\n"
    "                    currentCountry = currentCodeCountries.get(0);\n"
    "                }\n"
    "            }\n"
    "            if (countryState == COUNTRY_STATE_EMPTY) {",
)

# Local-only TelePremium feature: all bundled launcher icons are selectable.
# This does not spoof Telegram Premium status and does not affect server entitlements.
app_icons = "TMessagesProj/src/main/java/org/telegram/ui/Cells/AppIconsSelectorCell.java"

replace_once(
    app_icons,
    "    public final static float ICONS_ROUND_RADIUS = 18;\n",
    "    public final static float ICONS_ROUND_RADIUS = 18;\n"
    "    private static final boolean TELEPREMIUM_LOCAL_ICONS_UNLOCKED = true;\n",
)

replace_in_method(
    app_icons,
    "public AppIconsSelectorCell(Context context, BaseFragment fragment, int currentAccount)",
    "            if (icon.premium && !UserConfig.hasPremiumOnAccounts()) {",
    "            if (!TELEPREMIUM_LOCAL_ICONS_UNLOCKED && icon.premium && !UserConfig.hasPremiumOnAccounts()) {",
)

replace_in_method(
    app_icons,
    "private void updateIconsVisibility()",
    "        if (MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {",
    "        if (!TELEPREMIUM_LOCAL_ICONS_UNLOCKED && MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {",
)

replace_in_method(
    app_icons,
    "private void bind(LauncherIconController.LauncherIcon icon)",
    "            if (icon.premium && !UserConfig.hasPremiumOnAccounts()) {",
    "            if (!TELEPREMIUM_LOCAL_ICONS_UNLOCKED && icon.premium && !UserConfig.hasPremiumOnAccounts()) {",
)

# Local voice-to-text for downloaded voice messages on Android 13+.
# It decodes the Telegram audio to PCM and injects it into the system speech
# recognizer, so it does not depend on Telegram Premium transcription.
transcribe = "TMessagesProj/src/main/java/org/telegram/ui/Components/TranscribeButton.java"

replace_once(
    transcribe,
    "import org.telegram.messenger.Utilities;\n",
    "import org.telegram.messenger.Utilities;\n"
    "import org.telegram.messenger.telepremium.TelePremiumLocalTranscriber;\n",
)

replace_once(
    transcribe,
    "        premium = parent.getMessageObject() != null && UserConfig.getInstance(parent.getMessageObject().currentAccount).isPremium();",
    "        premium = parent.getMessageObject() != null && (UserConfig.getInstance(parent.getMessageObject().currentAccount).isPremium() || TelePremiumLocalTranscriber.isSupported());",
)

replace_in_method(
    transcribe,
    "private static void transcribePressed(MessageObject messageObject, boolean open, ChatMessageCell.ChatMessageCellDelegate delegate)",
    "        int messageId = messageObject.messageOwner.id;\n"
    "        if (open) {",
    "        int messageId = messageObject.messageOwner.id;\n"
    "        if (open && TelePremiumLocalTranscriber.isSupported() && (TextUtils.isEmpty(messageObject.messageOwner.voiceTranscription) || !messageObject.messageOwner.voiceTranscriptionFinal)) {\n"
    "            if (transcribeOperationsByDialogPosition == null) {\n"
    "                transcribeOperationsByDialogPosition = new HashMap<>();\n"
    "            }\n"
    "            transcribeOperationsByDialogPosition.put((Integer) reqInfoHash(messageObject), messageObject);\n"
    "            TelePremiumLocalTranscriber.transcribe(messageObject, new TelePremiumLocalTranscriber.Callback() {\n"
    "                @Override\n"
    "                public void onSuccess(String text) {\n"
    "                    String finalText = text == null ? \"\" : text.trim();\n"
    "                    messageObject.messageOwner.voiceTranscription = finalText;\n"
    "                    messageObject.messageOwner.voiceTranscriptionOpen = true;\n"
    "                    messageObject.messageOwner.voiceTranscriptionFinal = true;\n"
    "                    TranscribeButton.openVideoTranscription(messageObject);\n"
    "                    TranscribeButton.finishTranscription(messageObject, 0, finalText);\n"
    "                }\n"
    "                @Override\n"
    "                public void onError(String reason) {\n"
    "                    if (transcribeOperationsByDialogPosition != null) {\n"
    "                        transcribeOperationsByDialogPosition.remove((Integer) reqInfoHash(messageObject));\n"
    "                    }\n"
    "                    messageObject.messageOwner.voiceTranscriptionOpen = false;\n"
    "                    messageObject.messageOwner.voiceTranscriptionFinal = false;\n"
    "                    MessagesStorage.getInstance(account).updateMessageVoiceTranscriptionOpen(dialogId, messageId, messageObject.messageOwner);\n"
    "                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.voiceTranscriptionUpdate, messageObject);\n"
    "                    android.widget.Toast.makeText(org.telegram.messenger.ApplicationLoader.applicationContext, reason, android.widget.Toast.LENGTH_LONG).show();\n"
    "                }\n"
    "            });\n"
    "            return;\n"
    "        }\n"
    "        if (open) {",
)

replace_in_method(
    transcribe,
    "public static boolean showTranscribeLock(MessageObject messageObject)",
    "        if (isFreeTranscribeInChat(messageObject)) {",
    "        if (TelePremiumLocalTranscriber.isSupported()) {\n"
    "            return false;\n"
    "        }\n"
    "        if (isFreeTranscribeInChat(messageObject)) {",
)

# Integrate TelePremium into the stock Telegram Premium entry, remove the
# Telegram Business row, and apply the app-wide UI scale.
profile = "TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java"

replace_in_method(
    profile,
    "private void updateRowsIds()",
    "                if (!getMessagesController().premiumFeaturesBlocked()) {\n"
    "                    businessRow = rowCount++;\n"
    "                }\n",
    "",
)

replace_in_method(
    profile,
    "public View createView(Context context)",
    '            } else if (position == premiumRow) {\n'
    '                presentFragment(new PremiumPreviewFragment("settings"));\n',
    '            } else if (position == premiumRow) {\n'
    '                presentFragment(new TelePremiumOverviewActivity());\n',
)

application_loader = "TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java"

replace_once(
    application_loader,
    "import org.telegram.messenger.voip.VideoCapturerDevice;\n",
    "import org.telegram.messenger.voip.VideoCapturerDevice;\n"
    "import org.telegram.messenger.telepremium.TelePremiumUiScale;\n",
)

replace_in_method(
    application_loader,
    "public void onCreate()",
    "        super.onCreate();",
    "        super.onCreate();\n"
    "        TelePremiumUiScale.apply(applicationContext);",
)

replace_in_method(
    application_loader,
    "public void onConfigurationChanged(Configuration newConfig)",
    "        super.onConfigurationChanged(newConfig);",
    "        super.onConfigurationChanged(newConfig);\n"
    "        TelePremiumUiScale.apply(applicationContext);",
)

launch_activity = "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"

replace_once(
    launch_activity,
    "import org.telegram.messenger.ApplicationLoader;\n",
    "import org.telegram.messenger.ApplicationLoader;\n"
    "import org.telegram.messenger.telepremium.TelePremiumUiScale;\n",
)

replace_in_method(
    launch_activity,
    "protected void onCreate(Bundle savedInstanceState)",
    "        ApplicationLoader.postInitApplication();\n"
    "        AndroidUtilities.checkDisplaySize(this, getResources().getConfiguration());",
    "        ApplicationLoader.postInitApplication();\n"
    "        TelePremiumUiScale.apply(this);\n"
    "        AndroidUtilities.checkDisplaySize(this, getResources().getConfiguration());",
)

replace_in_method(
    launch_activity,
    "public void onConfigurationChanged(Configuration newConfig)",
    "        AndroidUtilities.checkDisplaySize(this, newConfig);",
    "        TelePremiumUiScale.apply(this);\n"
    "        AndroidUtilities.checkDisplaySize(this, newConfig);",
)


# TelePremium Local Premium Core.
# User appearance is a client-side override and is never sent to Telegram.
user_object = "TMessagesProj/src/main/java/org/telegram/messenger/UserObject.java"

replace_once(
    user_object,
    "import org.telegram.tgnet.TLRPC;\n",
    "import org.telegram.tgnet.TLRPC;\n"
    "import org.telegram.messenger.telepremium.TelePremiumPeerAppearanceStore;\n",
)

replace_once(
    user_object,
    """    public static int getColorId(TLRPC.User user) {
        if (user == null) return 0;
        if (user.color != null && (user.color.flags & 1) != 0) return user.color.color;
        return (int) (user.id % 7);
    }""",
    """    public static int getColorId(TLRPC.User user) {
        if (user == null) return 0;
        int fallback = user.color != null && (user.color.flags & 1) != 0 ? user.color.color : (int) (user.id % 7);
        return TelePremiumPeerAppearanceStore.getNameColor(user.id, fallback);
    }""",
)

replace_once(
    user_object,
    """    public static long getEmojiId(TLRPC.User user) {
        if (user != null && user.color != null && (user.color.flags & 2) != 0) return user.color.background_emoji_id;
        return 0;
    }""",
    """    public static long getEmojiId(TLRPC.User user) {
        if (user == null) return 0;
        long fallback = user.color != null && (user.color.flags & 2) != 0 ? user.color.background_emoji_id : 0;
        return TelePremiumPeerAppearanceStore.getNameEmoji(user.id, fallback);
    }""",
)

replace_once(
    user_object,
    """    public static int getProfileColorId(TLRPC.User user) {
        if (user == null) return 0;
        if (user.profile_color != null && (user.profile_color.flags & 1) != 0) return user.profile_color.color;
        return -1;
    }""",
    """    public static int getProfileColorId(TLRPC.User user) {
        if (user == null) return 0;
        int fallback = user.profile_color != null && (user.profile_color.flags & 1) != 0 ? user.profile_color.color : -1;
        return TelePremiumPeerAppearanceStore.getProfileColor(user.id, fallback);
    }""",
)

replace_once(
    user_object,
    """    public static long getProfileEmojiId(TLRPC.User user) {
        if (user != null && user.profile_color != null && (user.profile_color.flags & 2) != 0) return user.profile_color.background_emoji_id;
        return 0;
    }""",
    """    public static long getProfileEmojiId(TLRPC.User user) {
        if (user == null) return 0;
        long fallback = user.profile_color != null && (user.profile_color.flags & 2) != 0 ? user.profile_color.background_emoji_id : 0;
        return TelePremiumPeerAppearanceStore.getProfileEmoji(user.id, fallback);
    }""",
)

# Name/profile colors and background emoji are local TelePremium appearance.
peer_color = "TMessagesProj/src/main/java/org/telegram/ui/PeerColorActivity.java"

replace_once(
    peer_color,
    "import org.telegram.messenger.UserObject;\n",
    "import org.telegram.messenger.UserObject;\n"
    "import org.telegram.messenger.telepremium.TelePremiumPeerAppearanceStore;\n",
)

replace_once(
    peer_color,
    "            button.setText(isChannel ? buttonUnlocked : (!getUserConfig().isPremium() ? buttonLocked : buttonUnlocked), false);\n",
    "            button.setText(buttonUnlocked, false);\n",
)

replace_once(
    peer_color,
    "                button.setText(!getUserConfig().isPremium() ? buttonLocked : buttonUnlocked, true);\n",
    "                button.setText(buttonUnlocked, true);\n",
)

replace_once(
    peer_color,
    """        if (isChannel) {
            finishFragment();
        } else {
            if (!getUserConfig().isPremium()) {
                showDialog(new PremiumFeatureBottomSheet(PeerColorActivity.this, PremiumPreviewFragment.PREMIUM_FEATURE_NAME_COLOR, true));
                return;
            }
        }

        apply();""",
    """        if (isChannel) {
            finishFragment();
        }

        apply();""",
)

replace_once(
    peer_color,
    "        if (applying || !isChannel && !getUserConfig().isPremium()) {\n            return;\n        }\n",
    "        if (applying) {\n            return;\n        }\n",
)

replace_once(
    peer_color,
    "        if (!isChannel && hasUnsavedChanged() && getUserConfig().isPremium()) {\n            showUnsavedAlert();\n            return false;\n        }\n",
    "        if (!isChannel && hasUnsavedChanged()) {\n            showUnsavedAlert();\n            return false;\n        }\n",
)

replace_once(
    peer_color,
    "        if (!isChannel && hasUnsavedChanged() && getUserConfig().isPremium()) {\n            return false;\n        }\n",
    "        if (!isChannel && hasUnsavedChanged()) {\n            return false;\n        }\n",
)

replace_in_method(
    peer_color,
    "private void apply()",
    """        if (isChannel) {
            finishFragment();
        } else {
            final TLRPC.User me = getUserConfig().getCurrentUser();
            if (me.color == null) {
                me.color = new TLRPC.TL_peerColor();
                me.color.color = (int) (me.id % 7);
            }
            if (namePage.selectedColor != UserObject.getColorId(me) || namePage.selectedEmoji != UserObject.getEmojiId(me)) {
                applyingName = true;
                TLRPC.TL_account_updateColor req = new TLRPC.TL_account_updateColor();
                me.flags2 |= 256;
                me.color.flags |= 1;
                req.flags |= 4;
                req.color = me.color.color = namePage.selectedColor;
                if (namePage.selectedEmoji != 0) {
                    req.flags |= 1;
                    me.color.flags |= 2;
                    req.background_emoji_id = me.color.background_emoji_id = namePage.selectedEmoji;
                } else {
                    me.color.flags &=~ 2;
                    me.color.background_emoji_id = 0;
                }
                getConnectionsManager().sendRequest(req, null);
            }
            if (profilePage.selectedColor != UserObject.getProfileColorId(me) || profilePage.selectedEmoji != UserObject.getProfileEmojiId(me)) {
                applyingProfile = true;
                if (me.profile_color == null) {
                    me.profile_color = new TLRPC.TL_peerColor();
                }
                TLRPC.TL_account_updateColor req = new TLRPC.TL_account_updateColor();
                req.for_profile = true;
                me.flags2 |= 512;
                if (profilePage.selectedColor < 0) {
                    me.profile_color.flags &=~ 1;
                } else {
                    me.profile_color.flags |= 1;
                    req.flags |= 4;
                    req.color = me.profile_color.color = profilePage.selectedColor;
                }
                if (profilePage.selectedEmoji != 0) {
                    req.flags |= 1;
                    me.profile_color.flags |= 2;
                    req.background_emoji_id = me.profile_color.background_emoji_id = profilePage.selectedEmoji;
                } else {
                    me.profile_color.flags &=~ 2;
                    me.profile_color.background_emoji_id = 0;
                }
                getConnectionsManager().sendRequest(req, null);
            }
            getMessagesController().putUser(me, false);
            getUserConfig().saveConfig(true);
            finishFragment();
            showBulletin();
        }""",
    """        if (isChannel) {
            finishFragment();
        } else {
            final TLRPC.User me = getUserConfig().getCurrentUser();
            if (me == null) {
                return;
            }

            applyingName = namePage.selectedColor != UserObject.getColorId(me)
                    || namePage.selectedEmoji != UserObject.getEmojiId(me);
            applyingProfile = profilePage.selectedColor != UserObject.getProfileColorId(me)
                    || profilePage.selectedEmoji != UserObject.getProfileEmojiId(me);

            TelePremiumPeerAppearanceStore.save(
                    me.id,
                    namePage.selectedColor,
                    namePage.selectedEmoji,
                    profilePage.selectedColor,
                    profilePage.selectedEmoji
            );

            finishFragment();
            showBulletin();
        }""",
)


# TelePremium local emoji status + local double-tap reaction.
# These are client-owned settings. User emoji status never calls
# account.updateEmojiStatus; double-tap reaction already persists locally in
# MediaDataController's emoji settings.
replace_once(
    user_object,
    "import org.telegram.messenger.telepremium.TelePremiumPeerAppearanceStore;\n",
    "import org.telegram.messenger.telepremium.TelePremiumPeerAppearanceStore;\n"
    "import org.telegram.messenger.telepremium.TelePremiumEmojiStatusStore;\n",
)

replace_once(
    user_object,
    """    public static Long getEmojiStatusDocumentId(TLRPC.User user) {
        if (user == null) {
            return null;
        }
        return getEmojiStatusDocumentId(user.emoji_status);
    }""",
    """    public static Long getEmojiStatusDocumentId(TLRPC.User user) {
        if (user == null) {
            return null;
        }
        Long fallback = getEmojiStatusDocumentId(user.emoji_status);
        return TelePremiumEmojiStatusStore.getDocumentId(UserConfig.selectedAccount, user.id, fallback);
    }""",
)

replace_once(
    profile,
    "import org.telegram.messenger.UserObject;\n",
    "import org.telegram.messenger.UserObject;\n"
    "import org.telegram.messenger.telepremium.TelePremiumEmojiStatusStore;\n",
)

replace_in_method(
    profile,
    "public void showStatusSelect()",
    """                TLObject request;
                if (currentChat == null) {
                    TLRPC.TL_account_updateEmojiStatus req = new TLRPC.TL_account_updateEmojiStatus();
                    if (documentId == null) {
                        req.emoji_status = new TLRPC.TL_emojiStatusEmpty();
                    } else if (until != null) {
                        req.emoji_status = new TLRPC.TL_emojiStatusUntil();
                        ((TLRPC.TL_emojiStatusUntil) req.emoji_status).document_id = documentId;
                        ((TLRPC.TL_emojiStatusUntil) req.emoji_status).until = until;
                    } else {
                        req.emoji_status = new TLRPC.TL_emojiStatus();
                        ((TLRPC.TL_emojiStatus) req.emoji_status).document_id = documentId;
                    }
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
                    if (user != null) {
                        user.emoji_status = req.emoji_status;
                        MessagesController.getInstance(currentAccount).updateEmojiStatusUntilUpdate(user.id, user.emoji_status);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userEmojiStatusUpdated, user);
                    }
                    request = req;
                } else {""",
    """                TLObject request = null;
                if (currentChat == null) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
                    if (user != null) {
                        TelePremiumEmojiStatusStore.save(currentAccount, user.id, documentId, until);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userEmojiStatusUpdated, user);
                    }
                } else {""",
)

replace_in_method(
    profile,
    "public void showStatusSelect()",
    "                ConnectionsManager.getInstance(currentAccount).sendRequest(request, null);",
    """                if (request != null) {
                    ConnectionsManager.getInstance(currentAccount).sendRequest(request, null);
                }""",
)

replace_in_method(
    profile,
    "public void showStatusSelect()",
    """        TLRPC.User user = getMessagesController().getUser(userId);
        if (user != null) {
            popupLayout.setExpireDateHint(DialogObject.getEmojiStatusUntil(user.emoji_status));
        }""",
    """        TLRPC.User user = getMessagesController().getUser(userId);
        if (user != null) {
            if (user.self && TelePremiumEmojiStatusStore.hasOverride(currentAccount, user.id)) {
                popupLayout.setExpireDateHint(TelePremiumEmojiStatusStore.getUntil(currentAccount, user.id, 0));
            } else {
                popupLayout.setExpireDateHint(DialogObject.getEmojiStatusUntil(user.emoji_status));
            }
        }""",
)

replace_once(
    profile,
    """    private Drawable getEmojiStatusDrawable(TLRPC.EmojiStatus emojiStatus, boolean switchable, boolean animated, int a) {
        if (emojiStatusDrawable[a] == null) {
            emojiStatusDrawable[a] = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameTextView[a], AndroidUtilities.dp(24), a == 0 ? AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS : AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD);
            if (fragmentViewAttached) {
                emojiStatusDrawable[a].attach();
            }
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatus) {
            emojiStatusDrawable[a].set(((TLRPC.TL_emojiStatus) emojiStatus).document_id, animated);
        } else if (emojiStatus instanceof TLRPC.TL_emojiStatusUntil && ((TLRPC.TL_emojiStatusUntil) emojiStatus).until > (int) (System.currentTimeMillis() / 1000) && !reportSpam) {
            emojiStatusDrawable[a].set(((TLRPC.TL_emojiStatusUntil) emojiStatus).document_id, animated);
        } else {
            emojiStatusDrawable[a].set(getPremiumCrossfadeDrawable(a), animated);
        }
        updateEmojiStatusDrawableColor();
        return emojiStatusDrawable[a];
    }""",
    """    private Drawable getEmojiStatusDrawable(TLRPC.EmojiStatus emojiStatus, boolean switchable, boolean animated, int a) {
        final boolean selfProfile = userId == UserConfig.getInstance(currentAccount).getClientUserId();
        final boolean localOverride = selfProfile && TelePremiumEmojiStatusStore.hasOverride(currentAccount, userId);
        if (localOverride) {
            emojiStatus = TelePremiumEmojiStatusStore.getStatus(currentAccount, userId, null);
        }
        if (emojiStatusDrawable[a] == null) {
            emojiStatusDrawable[a] = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameTextView[a], AndroidUtilities.dp(24), a == 0 ? AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS : AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD);
            if (fragmentViewAttached) {
                emojiStatusDrawable[a].attach();
            }
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatus) {
            emojiStatusDrawable[a].set(((TLRPC.TL_emojiStatus) emojiStatus).document_id, animated);
        } else if (emojiStatus instanceof TLRPC.TL_emojiStatusUntil && ((TLRPC.TL_emojiStatusUntil) emojiStatus).until > (int) (System.currentTimeMillis() / 1000) && !reportSpam) {
            emojiStatusDrawable[a].set(((TLRPC.TL_emojiStatusUntil) emojiStatus).document_id, animated);
        } else if (selfProfile && (localOverride || !getMessagesController().isPremiumUser(getMessagesController().getUser(userId)))) {
            Drawable statusIcon = ContextCompat.getDrawable(getParentActivity(), R.drawable.msg_status_set).mutate();
            emojiStatusDrawable[a].set(statusIcon, animated);
        } else {
            emojiStatusDrawable[a].set(getPremiumCrossfadeDrawable(a), animated);
        }
        updateEmojiStatusDrawableColor();
        return emojiStatusDrawable[a];
    }""",
)

replace_in_method(
    profile,
    "private void updateProfileData(boolean reload)",
    "} else if (getMessagesController().isPremiumUser(user)) {",
    "} else if (user.self || getMessagesController().isPremiumUser(user)) {",
    expected=2,
)

replace_in_method(
    profile,
    "private void updateProfileData(boolean reload)",
    "                if (user.self && getMessagesController().isPremiumUser(user)) {",
    "                if (user.self) {",
)

double_tap = "TMessagesProj/src/main/java/org/telegram/ui/ReactionsDoubleTapManageActivity.java"

replace_in_method(
    double_tap,
    "private void updateRows()",
    """        if (UserConfig.getInstance(currentAccount).isPremium()) {
            reactionsStartRow = -1;
            premiumReactionRow = rowCount++;
        } else {
            premiumReactionRow = -1;
            reactionsStartRow = rowCount;
        }""",
    """        reactionsStartRow = -1;
        premiumReactionRow = rowCount++;""",
)

# TelePremium global Premium-upsell isolation.
# Never spoof Telegram server entitlement. Instead, route official Telegram
# subscription preview surfaces to TelePremium and remove local animated-emoji
# lock chrome from the client-owned picker.
base_fragment = "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/BaseFragment.java"

replace_once(
    base_fragment,
    "import org.telegram.ui.LaunchActivity;\n",
    "import org.telegram.ui.LaunchActivity;\n"
    "import org.telegram.ui.TelePremiumPremiumRouter;\n",
)

replace_in_method(
    base_fragment,
    "public boolean presentFragment(BaseFragment fragment)",
    "        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(fragment);",
    "        fragment = TelePremiumPremiumRouter.route(fragment);\n"
    "        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(fragment);",
)

replace_in_method(
    base_fragment,
    "public boolean presentFragment(BaseFragment fragment, boolean removeLast)",
    "        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(fragment, removeLast);",
    "        fragment = TelePremiumPremiumRouter.route(fragment);\n"
    "        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(fragment, removeLast);",
)

replace_in_method(
    base_fragment,
    "public boolean presentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation)",
    "        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true, false, null);",
    "        fragment = TelePremiumPremiumRouter.route(fragment);\n"
    "        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true, false, null);",
)

replace_in_method(
    base_fragment,
    "public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params)",
    "        if (getParentActivity() == null) {",
    "        final BaseFragment routedFragment = TelePremiumPremiumRouter.route(fragment);\n"
    "        if (getParentActivity() == null) {",
)

replace_in_method(
    base_fragment,
    "public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params)",
    "        fragment.onTransitionAnimationStart(true, false);",
    "        routedFragment.onTransitionAnimationStart(true, false);",
)

replace_in_method(
    base_fragment,
    "public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params)",
    "        bottomSheet[0] = new BottomSheet(getParentActivity(), true, fragment.getResourceProvider()) {",
    "        bottomSheet[0] = new BottomSheet(getParentActivity(), true, routedFragment.getResourceProvider()) {",
)

replace_in_method(
    base_fragment,
    "public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params)",
    "                actionBarLayout[0].addFragmentToStack(fragment);",
    "                actionBarLayout[0].addFragmentToStack(routedFragment);",
)

replace_in_method(
    base_fragment,
    "public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params)",
    "                    fragment.onPause();\n                    fragment.onFragmentDestroy();",
    "                    routedFragment.onPause();\n                    routedFragment.onFragmentDestroy();",
)

replace_in_method(
    base_fragment,
    "public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params)",
    "                    fixNavigationBar(Theme.getColor(Theme.key_dialogBackgroundGray, fragment.getResourceProvider()));",
    "                    fixNavigationBar(Theme.getColor(Theme.key_dialogBackgroundGray, routedFragment.getResourceProvider()));",
)

replace_in_method(
    base_fragment,
    "public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params)",
    "                AndroidUtilities.setLightStatusBar(getWindow(), fragment.isLightStatusBar());",
    "                AndroidUtilities.setLightStatusBar(getWindow(), routedFragment.isLightStatusBar());",
)

replace_in_method(
    base_fragment,
    "public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params)",
    "                fragment.onBottomSheetCreated();",
    "                routedFragment.onBottomSheetCreated();",
)

replace_in_method(
    base_fragment,
    "public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params)",
    "                fragment.onTransitionAnimationEnd(true, false);",
    "                routedFragment.onTransitionAnimationEnd(true, false);",
)

replace_in_method(
    base_fragment,
    "public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params)",
    "        fragment.setParentDialog(bottomSheet[0]);",
    "        routedFragment.setParentDialog(bottomSheet[0]);",
)

launch_activity = "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"

replace_in_method(
    launch_activity,
    "public void presentFragment(BaseFragment fragment)",
    "        getActionBarLayout().presentFragment(fragment);",
    "        getActionBarLayout().presentFragment(TelePremiumPremiumRouter.route(fragment));",
)

replace_in_method(
    launch_activity,
    "public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation)",
    "        return getActionBarLayout().presentFragment(fragment, removeLast, forceWithoutAnimation, true, false);",
    "        return getActionBarLayout().presentFragment(TelePremiumPremiumRouter.route(fragment), removeLast, forceWithoutAnimation, true, false);",
)

premium_sheet = "TMessagesProj/src/main/java/org/telegram/ui/Components/Premium/PremiumFeatureBottomSheet.java"

replace_once(
    premium_sheet,
    "import org.telegram.ui.PremiumPreviewFragment;\n",
    "import org.telegram.ui.PremiumPreviewFragment;\n"
    "import org.telegram.ui.TelePremiumPremiumRouter;\n",
)

replace_in_method(
    premium_sheet,
    "public void show()",
    "        super.show();",
    """        if (TelePremiumPremiumRouter.hideTelegramPremiumUpsell()) {
            TelePremiumPremiumRouter.openOverview(baseFragment);
            return;
        }
        super.show();""",
)

premium_preview_sheet = "TMessagesProj/src/main/java/org/telegram/ui/Components/Premium/PremiumPreviewBottomSheet.java"

replace_once(
    premium_preview_sheet,
    "import org.telegram.ui.PremiumPreviewFragment;\n",
    "import org.telegram.ui.PremiumPreviewFragment;\n"
    "import org.telegram.ui.TelePremiumPremiumRouter;\n",
)

replace_in_method(
    premium_preview_sheet,
    "public void show()",
    "        super.show();",
    """        if (TelePremiumPremiumRouter.hideTelegramPremiumUpsell()) {
            TelePremiumPremiumRouter.openOverview(fragment);
            return;
        }
        super.show();""",
)

premium_preview = "TMessagesProj/src/main/java/org/telegram/ui/PremiumPreviewFragment.java"

replace_in_method(
    premium_preview,
    "public static void buyPremium(BaseFragment fragment, SubscriptionTier tier, String source, boolean forcePremium, BillingFlowParams.SubscriptionUpdateParams updateParams)",
    "        if (BuildVars.IS_BILLING_UNAVAILABLE) {",
    """        if (TelePremiumPremiumRouter.hideTelegramPremiumUpsell()) {
            TelePremiumPremiumRouter.openOverview(fragment);
            return;
        }
        if (BuildVars.IS_BILLING_UNAVAILABLE) {""",
)

# Rename the stock settings entry so the main profile settings no longer
# advertises Telegram Premium.
replace_once(
    profile,
    "                        textCell.setTextAndIcon(LocaleController.getString(R.string.TelegramPremium), new AnimatedEmojiDrawable.WrapSizeDrawable(PremiumGradient.getInstance().premiumStarMenuDrawable, dp(24), dp(24)), true);",
    "                        textCell.setTextAndIcon(\"TelePremium\", new AnimatedEmojiDrawable.WrapSizeDrawable(PremiumGradient.getInstance().premiumStarMenuDrawable, dp(24), dp(24)), true);",
)

emoji_dialog = "TMessagesProj/src/main/java/org/telegram/ui/SelectAnimatedEmojiDialog.java"

replace_once(
    emoji_dialog,
    """                @Override
                protected ColorFilter getEmojiColorFilter() {
                    return premiumStarColorFilter;
                }

                @Override
                protected boolean onTabClick(int index) {""",
    """                @Override
                protected ColorFilter getEmojiColorFilter() {
                    return premiumStarColorFilter;
                }

                @Override
                protected boolean allowEmojisForNonPremium() {
                    return TelePremiumPremiumRouter.localAnimatedEmojiUnlocked();
                }

                @Override
                protected boolean onTabClick(int index) {""",
)

replace_once(
    emoji_dialog,
    "                    header.setText(pack.set.title, !pack.free && !UserConfig.getInstance(currentAccount).isPremium() && type != TYPE_AVATAR_CONSTRUCTOR && type != TYPE_SET_REPLY_ICON && type != TYPE_SET_REPLY_ICON_BOTTOM && type != TYPE_CHAT_REACTIONS);",
    "                    header.setText(pack.set.title, false);",
)

replace_once(
    emoji_dialog,
    """                        button.set(pack.set.title, !pack.free && !UserConfig.getInstance(currentAccount).isPremium(), pack.installed, e -> {
                            if (!pack.free && !UserConfig.getInstance(currentAccount).isPremium()) {
                                BaseFragment fragment = LaunchActivity.getLastFragment();
                                if (fragment != null) {
                                    fragment.showDialog(new PremiumFeatureBottomSheet(baseFragment, getContext(), currentAccount, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false));
                                }
                                return;
                            }
                            Integer p = null;""",
    """                        button.set(pack.set.title, false, pack.installed, e -> {
                            Integer p = null;""",
)

# TelePremium hard isolation from Telegram Premium purchase UI.
# Stars are deliberately untouched: Stars live in org.telegram.ui.Stars and
# continue to use Telegram's official Stars flows.
action_bar_layout = "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarLayout.java"

replace_once(
    action_bar_layout,
    "import org.telegram.ui.LaunchActivity;\n",
    "import org.telegram.ui.LaunchActivity;\n"
    "import org.telegram.ui.TelePremiumPremiumRouter;\n",
)

replace_in_method(
    action_bar_layout,
    "public boolean presentFragment(NavigationParams params)",
    "        BaseFragment fragment = params.fragment;",
    """        BaseFragment fragment = TelePremiumPremiumRouter.route(params.fragment);
        if (fragment != params.fragment) {
            params.fragment = fragment;
        }""",
)

premium_lock = "TMessagesProj/src/main/java/org/telegram/ui/Components/Premium/PremiumLockIconView.java"

replace_in_method(
    premium_lock,
    "protected void onDraw(Canvas canvas)",
    "        if (waitingImage) {",
    """        if (type != TYPE_GIFT_LOCK) {
            return;
        }
        if (waitingImage) {""",
)

premium_button = "TMessagesProj/src/main/java/org/telegram/ui/Components/Premium/PremiumButtonView.java"

replace_in_method(
    premium_button,
    "public void setButton(String text, View.OnClickListener clickListener, boolean animated)",
    "        if (!isButtonTextSet && animated) {",
    """        setVisibility(View.GONE);
        clickListener = null;
        if (!isButtonTextSet && animated) {""",
)

emoji_tabs = "TMessagesProj/src/main/java/org/telegram/ui/Components/EmojiTabsStrip.java"

replace_once(
    emoji_tabs,
    """                if (currentType == SelectAnimatedEmojiDialog.TYPE_AVATAR_CONSTRUCTOR || currentType == SelectAnimatedEmojiDialog.TYPE_CHAT_REACTIONS || currentType == SelectAnimatedEmojiDialog.TYPE_SET_REPLY_ICON || currentType == SelectAnimatedEmojiDialog.TYPE_SET_REPLY_ICON_BOTTOM) {
                    currentPackButton.setLock(null, false);
                } else if (!isPremium && !free) {
                    currentPackButton.setLock(true, false);
                } else if (!this.isInstalled(newPack)) {
                    currentPackButton.setLock(false, false);
                } else {
                    currentPackButton.setLock(null, false);
                }""",
    """                currentPackButton.setLock(null, false);""",
)

replace_in_method(
    emoji_dialog,
    "public boolean onItemClick(View view, int position, float x, float y)",
    "                if (view instanceof ImageViewEmoji && (type == TYPE_REACTIONS || type == TYPE_EXPANDABLE_REACTIONS)) {",
    "                if (view instanceof ImageViewEmoji && (type == TYPE_REACTIONS || type == TYPE_EXPANDABLE_REACTIONS)) {",
)

replace_once(
    emoji_dialog,
    "                    if (!imageViewEmoji.isDefaultReaction && !UserConfig.getInstance(currentAccount).isPremium()) {",
    "                    if (!imageViewEmoji.isDefaultReaction && !TelePremiumPremiumRouter.localAnimatedEmojiUnlocked() && !UserConfig.getInstance(currentAccount).isPremium()) {",
)

replace_exact(
    emoji_dialog,
    "                if (!UserConfig.getInstance(currentAccount).isPremium() && type == TYPE_EFFECTS && currentReaction.isEffect && currentReaction.premium) {",
    "                if (!TelePremiumPremiumRouter.localAnimatedEmojiUnlocked() && !UserConfig.getInstance(currentAccount).isPremium() && type == TYPE_EFFECTS && currentReaction.isEffect && currentReaction.premium) {",
    expected=2,
)

# Experimental voice-message privacy unlock. Remove only the local Premium
# gates for Contacts/Nobody and exception rows; normal privacy TL request remains.
privacy_control = "TMessagesProj/src/main/java/org/telegram/ui/PrivacyControlActivity.java"

replace_once(
    privacy_control,
    """            } else if (rulesType == PRIVACY_RULES_TYPE_VOICE_MESSAGES && (position == myContactsRow || position == nobodyRow) && !getUserConfig().isPremium()) {
                BulletinFactory.of(this).createSimpleBulletin(
                        R.raw.star_premium_2,
                        LocaleController.getString(R.string.OptionPremiumRequiredTitle),
                        AndroidUtilities.replaceTags(LocaleController.getString(R.string.OptionPremiumRequiredMessage)),
                        LocaleController.getString(R.string.OptionPremiumRequiredButton),
                        () -> {
                            presentFragment(new PremiumPreviewFragment("settings"));
                        }
                ).show();
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                AndroidUtilities.shakeViewSpring(view, shakeDp = -shakeDp);
""",
    "",
)

replace_once(
    privacy_control,
    "        if (rulesType != PRIVACY_RULES_TYPE_MESSAGES && (rulesType != PRIVACY_RULES_TYPE_VOICE_MESSAGES || getUserConfig().isPremium())) {\n",
    "        if (rulesType != PRIVACY_RULES_TYPE_MESSAGES) {\n",
)

replace_once(
    privacy_control,
    """                        if (getUserConfig().isPremium()) {
                            backgroundResId = R.drawable.greydivider;
                        } else {
                            backgroundResId = R.drawable.greydivider_bottom;
                        }
""",
    "                        backgroundResId = R.drawable.greydivider;\n",
)

p, src = read(privacy_control)
old_voice_lock = "rulesType == PRIVACY_RULES_TYPE_VOICE_MESSAGES && !getUserConfig().isPremium() || rulesType == PRIVACY_RULES_TYPE_MESSAGES && !getMessagesController().newNoncontactPeersRequirePremiumWithoutOwnpremium && !getUserConfig().isPremium()"
new_voice_lock = "rulesType == PRIVACY_RULES_TYPE_MESSAGES && !getMessagesController().newNoncontactPeersRequirePremiumWithoutOwnpremium && !getUserConfig().isPremium()"
count = src.count(old_voice_lock)
if count != 2:
    raise SystemExit(f"{privacy_control}: expected exactly two voice lock matches, found {count}")
p.write_text(src.replace(old_voice_lock, new_voice_lock), encoding="utf-8")
print(f"patched: {privacy_control} :: removed {count} voice privacy lock icons")

print("TelePremium Concept 5 patches applied successfully.")
