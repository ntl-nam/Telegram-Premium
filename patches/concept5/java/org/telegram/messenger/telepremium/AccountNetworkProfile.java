package org.telegram.messenger.telepremium;

import org.telegram.tgnet.ConnectionsManager;

public class AccountNetworkProfile {

    public static final int MODE_INHERIT = 0;
    public static final int MODE_DIRECT = 1;
    public static final int MODE_AUTO = 2;
    public static final int MODE_MTPROTO = 3;
    public static final int MODE_SOCKS5 = 4;

    public int mode = MODE_INHERIT;
    public String displayName = "";
    public String locationName = "";
    public String address = "";
    public int port = 1080;
    public String username = "";
    public String password = "";
    public String secret = "";
    public byte ipStrategy = ConnectionsManager.USE_IPV4_IPV6_RANDOM;
    public boolean fallbackEnabled = true;

    public AccountNetworkProfile copy() {
        AccountNetworkProfile out = new AccountNetworkProfile();
        out.mode = mode;
        out.displayName = displayName;
        out.locationName = locationName;
        out.address = address;
        out.port = port;
        out.username = username;
        out.password = password;
        out.secret = secret;
        out.ipStrategy = ipStrategy;
        out.fallbackEnabled = fallbackEnabled;
        return out;
    }

    public boolean isProxyMode() {
        return mode == MODE_MTPROTO || mode == MODE_SOCKS5;
    }

    public boolean isConfiguredProxy() {
        return isProxyMode() && address != null && !address.trim().isEmpty() && port > 0;
    }

    public static String modeLabel(int mode) {
        switch (mode) {
            case MODE_DIRECT:
                return "Прямое";
            case MODE_AUTO:
                return "Авто";
            case MODE_MTPROTO:
                return "MTProto";
            case MODE_SOCKS5:
                return "SOCKS5";
            case MODE_INHERIT:
            default:
                return "Общий маршрут";
        }
    }
}
