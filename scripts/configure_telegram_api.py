#!/usr/bin/env python3
"""Require the fork's own Telegram credentials before compiling an APK."""
import os
import re
from pathlib import Path


def configure(path, api_id, api_hash):
    api_id, api_hash = api_id.strip(), api_hash.strip()
    if not api_id or not api_hash:
        raise ValueError("Set TELEGRAM_API_ID and TELEGRAM_API_HASH in repository Actions secrets")
    if not re.fullmatch(r"[0-9]+", api_id) or not 0 < int(api_id) <= 2147483647:
        raise ValueError("TELEGRAM_API_ID must be a positive Java integer")
    if int(api_id) == 4:
        raise ValueError("Refusing Telegram's public test APP_ID=4: obtain your own API credentials")
    if not re.fullmatch(r"[0-9a-fA-F]{32}", api_hash):
        raise ValueError("TELEGRAM_API_HASH must contain 32 hexadecimal characters")
    data = path.read_text(encoding="utf-8")
    data, id_count = re.subn(r"public static int APP_ID\s*=\s*\d+;",
                             f"public static int APP_ID = {int(api_id)};", data)
    data, hash_count = re.subn(r'public static String APP_HASH\s*=\s*"[0-9a-fA-F]+";',
                               f'public static String APP_HASH = "{api_hash}";', data)
    if id_count != 1 or hash_count != 1:
        raise ValueError("Expected exactly one APP_ID and APP_HASH declaration in BuildVars.java")
    path.write_text(data, encoding="utf-8")


if __name__ == "__main__":
    path = Path("upstream/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java")
    try:
        configure(path, os.environ.get("TELEGRAM_API_ID", ""), os.environ.get("TELEGRAM_API_HASH", ""))
    except ValueError as error:
        raise SystemExit(f"::error::{error}") from None
    print("Telegram API credentials configured from Actions secrets; public test ID rejected")
