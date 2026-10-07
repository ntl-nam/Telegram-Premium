package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.telepremium.AccountNetworkController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;

public class TelePremiumNetworkCenterActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Сеть и стабильность");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        AccountNetworkController controller = AccountNetworkController.getInstance();

        int connected = 0;
        int active = 0;
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (!UserConfig.getInstance(account).isClientActivated()) {
                continue;
            }
            active++;
            int state = controller.getConnectionState(account);
            if (state == ConnectionsManager.ConnectionStateConnected || state == ConnectionsManager.ConnectionStateUpdating) {
                connected++;
            }
        }

        TextView hero = new TextView(context);
        hero.setText("Умное подключение\n" + connected + " / " + active + " аккаунтов на связи");
        hero.setTextSize(20);
        hero.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hero.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        hero.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(18), AndroidUtilities.dp(21), AndroidUtilities.dp(18));
        hero.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        content.addView(hero);

        TextView header = new TextView(context);
        header.setText("Маршруты аккаунтов");
        header.setTextSize(14);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
        header.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(20), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        content.addView(header);

        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            UserConfig config = UserConfig.getInstance(account);
            if (!config.isClientActivated()) {
                continue;
            }

            final int accountId = account;
            TLRPC.User user = config.getCurrentUser();
            String name = user == null ? "Аккаунт " + (account + 1) : UserObject.getUserName(user);
            int ping = controller.getPing(account);
            String route = controller.getSummary(account);
            String value = route + (ping > 0 ? " • " + ping + " мс" : "");

            TextCell cell = new TextCell(context, getResourceProvider());
            cell.setTextAndValue(name, value, true);
            cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            cell.setOnClickListener(v -> presentFragment(new TelePremiumNetworkProfileActivity(accountId)));
            content.addView(cell, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(58)
            ));
        }

        TextView hint = new TextView(context);
        hint.setText("Каждый аккаунт может использовать собственный Direct, MTProto или SOCKS5 маршрут. Общие Telegram-прокси продолжают работать для аккаунтов в режиме «Общий маршрут».");
        hint.setTextSize(13);
        hint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(16), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        content.addView(hint);

        fragmentView = scrollView;
        return fragmentView;
    }
}
