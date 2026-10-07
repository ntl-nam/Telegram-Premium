package org.telegram.messenger.telepremium;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.TLRPC;

/**
 * Local-only emoji status for TelePremium.
 *
 * The status is intentionally not synchronized with Telegram and is never sent
 * through account.updateEmojiStatus. It is keyed by account slot and user id.
 */
public final class TelePremiumEmojiStatusStore {

    private static final String PREFS = "telepremium_emoji_status";

    private TelePremiumEmojiStatusStore() {
    }

    private static SharedPreferences prefs() {
        Context context = ApplicationLoader.applicationContext;
        return context == null ? null : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(int account, long userId, String suffix) {
        return account + "_" + userId + "_" + suffix;
    }

    public static boolean hasOverride(int account, long userId) {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean(key(account, userId, "enabled"), false);
    }

    public static Long getDocumentId(int account, long userId, Long fallback) {
        if (!hasOverride(account, userId)) {
            return fallback;
        }
        SharedPreferences p = prefs();
        if (p == null) {
            return fallback;
        }
        long documentId = p.getLong(key(account, userId, "document_id"), 0L);
        int until = p.getInt(key(account, userId, "until"), 0);
        if (documentId == 0L || until > 0 && until <= (int) (System.currentTimeMillis() / 1000L)) {
            return null;
        }
        return documentId;
    }

    public static int getUntil(int account, long userId, int fallback) {
        if (!hasOverride(account, userId)) {
            return fallback;
        }
        SharedPreferences p = prefs();
        if (p == null) {
            return fallback;
        }
        int until = p.getInt(key(account, userId, "until"), 0);
        return until > 0 && until <= (int) (System.currentTimeMillis() / 1000L) ? 0 : until;
    }

    public static TLRPC.EmojiStatus getStatus(int account, long userId, TLRPC.EmojiStatus fallback) {
        if (!hasOverride(account, userId)) {
            return fallback;
        }
        Long documentId = getDocumentId(account, userId, null);
        if (documentId == null) {
            return null;
        }
        int until = getUntil(account, userId, 0);
        if (until > 0) {
            TLRPC.TL_emojiStatusUntil status = new TLRPC.TL_emojiStatusUntil();
            status.document_id = documentId;
            status.until = until;
            return status;
        }
        TLRPC.TL_emojiStatus status = new TLRPC.TL_emojiStatus();
        status.document_id = documentId;
        return status;
    }

    public static void save(int account, long userId, Long documentId, Integer until) {
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        p.edit()
                .putBoolean(key(account, userId, "enabled"), true)
                .putLong(key(account, userId, "document_id"), documentId == null ? 0L : documentId)
                .putInt(key(account, userId, "until"), until == null ? 0 : until)
                .apply();
    }

    public static void clearOverride(int account, long userId) {
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        p.edit()
                .remove(key(account, userId, "enabled"))
                .remove(key(account, userId, "document_id"))
                .remove(key(account, userId, "until"))
                .apply();
    }
}
