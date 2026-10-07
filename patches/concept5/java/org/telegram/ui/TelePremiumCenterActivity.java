package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.telepremium.TelePremiumUiScale;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;

public class TelePremiumCenterActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("TelePremium");
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

        TextView hero = new TextView(context);
        hero.setText("TelePremium\nБольше, чем мессенджер");
        hero.setTextSize(22);
        hero.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hero.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        hero.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(22), AndroidUtilities.dp(24), AndroidUtilities.dp(22));
        hero.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        content.addView(hero, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        addSection(content, context, "Инструменты");

        addRow(content, context, "Мультиаккаунты", "До 10 аккаунтов и быстрое переключение", R.drawable.msg2_devices,
                v -> presentFragment(new TelePremiumAccountCenterActivity()));
        addRow(content, context, "Сеть и стабильность", "Отдельный маршрут для каждого аккаунта", R.drawable.msg2_data,
                v -> presentFragment(new TelePremiumNetworkCenterActivity()));
        addRow(content, context, "Файлы", "Загрузки, документы и локальное хранилище", R.drawable.msg2_folder,
                v -> presentFragment(new TelePremiumFileCenterActivity()));
        addRow(content, context, "Перевод", "Штатный перевод Telegram и языковые настройки", R.drawable.msg2_language,
                v -> presentFragment(new LanguageSelectActivity()));
        addRow(content, context, "Кэш и память", "Использование хранилища и очистка", R.drawable.msg2_data,
                v -> presentFragment(new CacheControlActivity()));
        addRow(content, context, "Масштаб интерфейса", "Сейчас " + TelePremiumUiScale.getScalePercent(context) + "%", R.drawable.msg2_discussion,
                v -> presentFragment(new TelePremiumAppearanceActivity()));
        addRow(content, context, "Темы и оформление", "Цвета, фон и параметры чатов", R.drawable.msg2_discussion,
                v -> presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)));

        addSection(content, context, "Concept 5");
        TextCell status = new TextCell(context, getResourceProvider());
        status.setTextAndValue("Состояние реализации", "Pass 5A / 5B", false);
        content.addView(status, rowParams());

        fragmentView = scrollView;
        return fragmentView;
    }

    private void addSection(LinearLayout parent, Context context, String title) {
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(14);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
        header.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(20), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        parent.addView(header);
    }

    private void addRow(LinearLayout parent, Context context, String title, String subtitle, int icon, View.OnClickListener listener) {
        TextCell cell = new TextCell(context, getResourceProvider());
        cell.setTextAndValueAndIcon(title, subtitle, icon, true);
        cell.setOnClickListener(listener);
        cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        parent.addView(cell, rowParams());
    }

    private LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                AndroidUtilities.dp(58)
        );
    }
}
