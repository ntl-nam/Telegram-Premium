#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "upstream" / "Telegram"

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

def method_bounds(data, signature):
    start = data.find(signature)
    if start < 0:
        raise SystemExit(f"method signature not found: {signature}")
    brace = data.find("{", start)
    if brace < 0:
        raise SystemExit("opening brace not found")
    depth = 0
    end = None
    for i in range(brace, len(data)):
        ch = data[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end = i
                break
    if end is None:
        raise SystemExit("closing brace not found")
    return start, brace, end

def replace_in_method(rel, signature, old, new, expected):
    p, data = read(rel)
    start, brace, end = method_bounds(data, signature)
    method = data[start:end + 1]
    count = method.count(old)
    if count == 0 and method.count(new) == expected:
        print(f"already patched: {rel} :: {signature}")
        return
    if count != expected:
        raise SystemExit(f"{rel} :: {signature}: expected {expected} matches, found {count}")
    method = method.replace(old, new)
    write(p, data[:start] + method + data[end + 1:])
    print(f"patched: {rel} :: {signature}")

def replace_method_body(rel, signature, body):
    p, data = read(rel)
    start = data.find(signature)
    if start < 0:
        raise SystemExit(f"{rel}: method signature not found: {signature}")
    brace = data.find("{", start)
    if brace < 0:
        raise SystemExit(f"{rel}: opening brace not found")
    depth = 0
    end = None
    for i in range(brace, len(data)):
        ch = data[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end = i
                break
    if end is None:
        raise SystemExit(f"{rel}: closing brace not found")
    indent = data[data.rfind("\n", 0, start) + 1:start]
    replacement = signature + " {\n" + body + "\n" + indent + "}"
    old_method = data[start:end + 1]
    if old_method == replacement:
        print(f"already patched: {rel} :: {signature}")
        return
    write(p, data[:start] + replacement + data[end + 1:])
    print(f"patched: {rel} :: {signature}")

if not SRC.exists():
    raise SystemExit(
        "upstream/Telegram is missing. Clone with --recurse-submodules "
        "or run: git submodule update --init --recursive"
    )

# Branding/package. Do not rename org.telegram.* Java namespaces.
replace_once(
    "gradle.properties",
    "APP_PACKAGE=org.telegram.messenger",
    "APP_PACKAGE=com.telepremium.messenger",
)

# FileProvider and runtime paths already derive from applicationId in upstream.
# Fix the last hard-coded fallback path.
replace_once(
    "TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java",
    'return new File("/data/data/org.telegram.messenger/files");',
    'return new File("/data/data/" + getApplicationId() + "/files");',
)

# Generic external-view fallback for unknown extensions. Scope the replacement
# strictly to openForView so unrelated text/plain fallbacks are untouched.
replace_in_method(
    "TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java",
    "public static boolean openForView(File f, String fileName, String mimeType, final Activity activity, Theme.ResourcesProvider resourcesProvider, boolean restrict)",
    'realMimeType != null ? realMimeType : "text/plain"',
    'realMimeType != null ? realMimeType : "*/*"',
    2,
)

# Freeze only the APK updater. Telegram protocol/config/DC traffic remains intact.
replace_method_body(
    "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java",
    "public void checkAppUpdate(boolean force, Browser.Progress progress)",
    "        if (progress != null) {\n            progress.end();\n        }\n        return;",
)
replace_method_body(
    "TMessagesProj/src/main/java/org/telegram/messenger/SharedConfig.java",
    "public static boolean isAppUpdateAvailable()",
    "        return false;",
)

# Translation: keep Telegram's native translation transport, expose the
# per-message control by default, and remove the client-side Premium gate from
# full-chat translation. If Telegram ever enforces this on the server, the
# request will fail normally instead of spoofing account Premium state.
replace_once(
    "TMessagesProj/src/main/java/org/telegram/messenger/TranslateController.java",
    'MessagesController.getGlobalMainSettings().getBoolean("translate_button", false)',
    'MessagesController.getGlobalMainSettings().getBoolean("translate_button", true)',
)
replace_method_body(
    "TMessagesProj/src/main/java/org/telegram/messenger/TranslateController.java",
    "public boolean isFeatureAvailable()",
    "        return isChatTranslateEnabled();",
)
replace_once(
    "TMessagesProj/src/main/java/org/telegram/ui/LanguageSelectActivity.java",
    "                        if (value && !getUserConfig().isPremium()) {\n"
    "                            showDialog(new PremiumFeatureBottomSheet(LanguageSelectActivity.this, PremiumPreviewFragment.PREMIUM_FEATURE_TRANSLATIONS, false));\n"
    "                            return;\n"
    "                        }\n",
    "",
)
replace_once(
    "TMessagesProj/src/main/java/org/telegram/ui/LanguageSelectActivity.java",
    "                        cell.setCheckBoxIcon(!getUserConfig().isPremium() ? R.drawable.permission_locked : 0);",
    "                        cell.setCheckBoxIcon(0);",
)

# TelePremium keeps the no-ads behavior of the original modified client without
# pretending that the Telegram account itself has a Premium subscription.
# Stop sponsored-message fetching at the controller and rendering at the chat.
replace_method_body(
    "TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java",
    "public SponsoredMessagesInfo getSponsoredMessages(long dialogId)",
    "        return null;",
)
replace_method_body(
    "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java",
    "private void addSponsoredMessages(boolean animated)",
    "        return;",
)

# Ten account slots at source level. Compiling from source propagates this
# constant into every Java per-account singleton/array.
replace_once(
    "TMessagesProj/src/main/java/org/telegram/messenger/UserConfig.java",
    "public final static int MAX_ACCOUNT_DEFAULT_COUNT = 3;",
    "public final static int MAX_ACCOUNT_DEFAULT_COUNT = 10;",
)
replace_once(
    "TMessagesProj/src/main/java/org/telegram/messenger/UserConfig.java",
    "public final static int MAX_ACCOUNT_COUNT = 4;",
    "public final static int MAX_ACCOUNT_COUNT = 10;",
)
replace_once(
    "TMessagesProj/src/main/java/org/telegram/messenger/UserConfig.java",
    "return hasPremiumOnAccounts() ? 5 : 3;",
    "return MAX_ACCOUNT_COUNT;",
)

# Native tgnet must agree with Java.
replace_once(
    "TMessagesProj/jni/tgnet/Defines.h",
    "#define MAX_ACCOUNT_COUNT 5",
    "#define MAX_ACCOUNT_COUNT 10",
)

old_switch = """ConnectionsManager& ConnectionsManager::getInstance(int32_t instanceNum) {
    switch (instanceNum) {
        case 0:
            static ConnectionsManager instance0(0);
            return instance0;
        case 1:
            static ConnectionsManager instance1(1);
            return instance1;
        case 2:
            static ConnectionsManager instance2(2);
            return instance2;
        case 3:
            static ConnectionsManager instance3(3);
            return instance3;
        case 4:
        default:
            static ConnectionsManager instance4(4);
            return instance4;
    }
}"""

new_switch = """ConnectionsManager& ConnectionsManager::getInstance(int32_t instanceNum) {
    switch (instanceNum) {
        case 0: { static ConnectionsManager instance0(0); return instance0; }
        case 1: { static ConnectionsManager instance1(1); return instance1; }
        case 2: { static ConnectionsManager instance2(2); return instance2; }
        case 3: { static ConnectionsManager instance3(3); return instance3; }
        case 4: { static ConnectionsManager instance4(4); return instance4; }
        case 5: { static ConnectionsManager instance5(5); return instance5; }
        case 6: { static ConnectionsManager instance6(6); return instance6; }
        case 7: { static ConnectionsManager instance7(7); return instance7; }
        case 8: { static ConnectionsManager instance8(8); return instance8; }
        case 9:
        default: { static ConnectionsManager instance9(9); return instance9; }
    }
}"""

replace_once(
    "TMessagesProj/jni/tgnet/ConnectionsManager.cpp",
    old_switch,
    new_switch,
)

print("TelePremium source patches applied successfully.")

# Concept 5 is layered on top of the stable Pass 1-4 source patch set.
import runpy
runpy.run_path(str(ROOT / "scripts" / "apply_concept5_patches.py"), run_name="__main__")
