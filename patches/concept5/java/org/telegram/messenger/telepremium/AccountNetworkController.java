package org.telegram.messenger.telepremium;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestTimeDelegate;

public class AccountNetworkController {

    private static final AccountNetworkController INSTANCE = new AccountNetworkController();

    public static AccountNetworkController getInstance() {
        return INSTANCE;
    }

    private final AccountNetworkStore store = AccountNetworkStore.getInstance();

    public boolean hasExplicitProfile(int account) {
        return store.hasProfile(account);
    }

    public AccountNetworkProfile getProfile(int account) {
        return store.load(account);
    }

    public AccountNetworkProfile getEditableProfile(int account) {
        return store.loadOrDefault(account).copy();
    }

    public void saveAndApply(int account, AccountNetworkProfile profile) {
        store.save(account, profile);
        applyProfile(account);
        refreshPromo(account);
    }

    public void resetToGlobal(int account) {
        store.clear(account);
        applyInheritedGlobal(account);
        ConnectionsManager.native_resumeNetwork(account, true);
        refreshPromo(account);
    }

    public void applyProfile(int account) {
        AccountNetworkProfile profile = store.load(account);
        if (profile == null || profile.mode == AccountNetworkProfile.MODE_INHERIT) {
            applyInheritedGlobal(account);
            return;
        }

        ConnectionsManager.native_setIpStrategy(account, profile.ipStrategy);

        if (profile.isConfiguredProxy()) {
            ConnectionsManager.native_setProxySettings(
                    account,
                    safe(profile.address),
                    profile.port,
                    safe(profile.username),
                    safe(profile.password),
                    safe(profile.secret)
            );
        } else {
            ConnectionsManager.native_setProxySettings(account, "", 1080, "", "", "");
        }

        ConnectionsManager.native_resumeNetwork(account, true);
    }

    public long checkProfile(int account, AccountNetworkProfile profile, RequestTimeDelegate delegate) {
        if (profile != null && profile.isConfiguredProxy()) {
            return ConnectionsManager.getInstance(account).checkProxy(
                    safe(profile.address),
                    profile.port,
                    safe(profile.username),
                    safe(profile.password),
                    safe(profile.secret),
                    delegate
            );
        }

        if (delegate != null) {
            delegate.run(ConnectionsManager.native_getCurrentPingTime(account));
        }
        return 0;
    }

    public String getSummary(int account) {
        AccountNetworkProfile profile = store.load(account);
        if (profile == null || profile.mode == AccountNetworkProfile.MODE_INHERIT) {
            return "Общий маршрут";
        }

        StringBuilder out = new StringBuilder(AccountNetworkProfile.modeLabel(profile.mode));
        if (!TextUtils.isEmpty(profile.locationName)) {
            out.append(" • ").append(profile.locationName);
        } else if (!TextUtils.isEmpty(profile.displayName)) {
            out.append(" • ").append(profile.displayName);
        }
        return out.toString();
    }

    public int getPing(int account) {
        return ConnectionsManager.native_getCurrentPingTime(account);
    }

    public int getConnectionState(int account) {
        return ConnectionsManager.getInstance(account).getConnectionState();
    }

    private void applyInheritedGlobal(int account) {
        SharedPreferences p = ApplicationLoader.applicationContext.getSharedPreferences(
                "mainconfig",
                Activity.MODE_PRIVATE
        );

        String address = p.getString("proxy_ip", "");
        String username = p.getString("proxy_user", "");
        String password = p.getString("proxy_pass", "");
        String secret = p.getString("proxy_secret", "");
        int port = p.getInt("proxy_port", 1080);
        boolean enabled = p.getBoolean("proxy_enabled", false);

        if (enabled && !TextUtils.isEmpty(address)) {
            ConnectionsManager.native_setProxySettings(
                    account,
                    address,
                    port,
                    safe(username),
                    safe(password),
                    safe(secret)
            );
        } else {
            ConnectionsManager.native_setProxySettings(account, "", 1080, "", "", "");
        }

    }

    private void refreshPromo(int account) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            return;
        }
        AccountInstance instance = AccountInstance.getInstance(account);
        if (instance.getUserConfig().isClientActivated()) {
            instance.getMessagesController().checkPromoInfo(true);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
