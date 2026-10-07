package org.telegram.messenger.telepremium;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * TelePremium-only appearance overrides.
 *
 * These values are intentionally local to this client. They do not modify
 * Telegram account state and are never sent with account.updateColor.
 */
public final class TelePremiumPeerAppearanceStore {

    private static final String PREFS = "telepremium_peer_appearance";

    private TelePremiumPeerAppearanceStore() {
    }

    private static SharedPreferences prefs() {
        Context context = ApplicationLoader.applicationContext;
        return context == null ? null : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(long userId, String suffix) {
        return userId + "_" + suffix;
    }

    public static boolean hasOverride(long userId) {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean(key(userId, "enabled"), false);
    }

    public static int getNameColor(long userId, int fallback) {
        SharedPreferences p = prefs();
        return p != null && hasOverride(userId) ? p.getInt(key(userId, "name_color"), fallback) : fallback;
    }

    public static long getNameEmoji(long userId, long fallback) {
        SharedPreferences p = prefs();
        return p != null && hasOverride(userId) ? p.getLong(key(userId, "name_emoji"), fallback) : fallback;
    }

    public static int getProfileColor(long userId, int fallback) {
        SharedPreferences p = prefs();
        return p != null && hasOverride(userId) ? p.getInt(key(userId, "profile_color"), fallback) : fallback;
    }

    public static long getProfileEmoji(long userId, long fallback) {
        SharedPreferences p = prefs();
        return p != null && hasOverride(userId) ? p.getLong(key(userId, "profile_emoji"), fallback) : fallback;
    }

    public static void save(long userId, int nameColor, long nameEmoji, int profileColor, long profileEmoji) {
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        p.edit()
                .putBoolean(key(userId, "enabled"), true)
                .putInt(key(userId, "name_color"), nameColor)
                .putLong(key(userId, "name_emoji"), nameEmoji)
                .putInt(key(userId, "profile_color"), profileColor)
                .putLong(key(userId, "profile_emoji"), profileEmoji)
                .apply();
    }

    public static void clear(long userId) {
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        p.edit()
                .remove(key(userId, "enabled"))
                .remove(key(userId, "name_color"))
                .remove(key(userId, "name_emoji"))
                .remove(key(userId, "profile_color"))
                .remove(key(userId, "profile_emoji"))
                .apply();
    }
}
