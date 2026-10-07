package org.telegram.messenger.telepremium;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

public class AccountNetworkStore {

    private static final AccountNetworkStore INSTANCE = new AccountNetworkStore();
    private static final String PREFIX = "telepremium_network_account_";

    public static AccountNetworkStore getInstance() {
        return INSTANCE;
    }

    private SharedPreferences prefs(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                PREFIX + account,
                Context.MODE_PRIVATE
        );
    }

    public boolean hasProfile(int account) {
        return prefs(account).getBoolean("configured", false);
    }

    public AccountNetworkProfile load(int account) {
        SharedPreferences p = prefs(account);
        if (!p.getBoolean("configured", false)) {
            return null;
        }

        long currentUserId = UserConfig.getInstance(account).getClientUserId();
        long boundUserId = p.getLong("bound_user_id", 0);
        if (boundUserId != 0 && currentUserId != 0 && boundUserId != currentUserId) {
            p.edit().clear().apply();
            return null;
        }

        AccountNetworkProfile profile = new AccountNetworkProfile();
        profile.mode = p.getInt("mode", AccountNetworkProfile.MODE_INHERIT);
        profile.displayName = p.getString("display_name", "");
        profile.locationName = p.getString("location_name", "");
        profile.address = p.getString("address", "");
        profile.port = p.getInt("port", 1080);
        profile.username = p.getString("username", "");
        profile.password = p.getString("password", "");
        profile.secret = p.getString("secret", "");
        profile.ipStrategy = (byte) p.getInt("ip_strategy", 2);
        profile.fallbackEnabled = p.getBoolean("fallback_enabled", true);
        return profile;
    }

    public AccountNetworkProfile loadOrDefault(int account) {
        AccountNetworkProfile profile = load(account);
        return profile != null ? profile : new AccountNetworkProfile();
    }

    public void save(int account, AccountNetworkProfile profile) {
        long currentUserId = UserConfig.getInstance(account).getClientUserId();
        prefs(account).edit()
                .putBoolean("configured", true)
                .putLong("bound_user_id", currentUserId)
                .putInt("mode", profile.mode)
                .putString("display_name", safe(profile.displayName))
                .putString("location_name", safe(profile.locationName))
                .putString("address", safe(profile.address))
                .putInt("port", profile.port)
                .putString("username", safe(profile.username))
                .putString("password", safe(profile.password))
                .putString("secret", safe(profile.secret))
                .putInt("ip_strategy", profile.ipStrategy)
                .putBoolean("fallback_enabled", profile.fallbackEnabled)
                .apply();
    }

    public void clear(int account) {
        prefs(account).edit().clear().apply();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
