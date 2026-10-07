package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.telepremium.TelePremiumUiScale;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;

public class TelePremiumAppearanceActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Масштаб интерфейса");
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
        content.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView description = new TextView(context);
        description.setText("Масштаб изменяет весь интерфейс TelePremium: текст, иконки, строки, отступы, панели и диалоги. По умолчанию используется 92%.");
        description.setTextSize(14);
        description.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        description.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(12), AndroidUtilities.dp(21), AndroidUtilities.dp(18));
        content.addView(description);

        addScale(content, context, 85);
        addScale(content, context, 90);
        addScale(content, context, 92);
        addScale(content, context, 95);
        addScale(content, context, 100);

        TextView hint = new TextView(context);
        hint.setText("После изменения экран приложения будет пересоздан, чтобы новый масштаб применился ко всем элементам.");
        hint.setTextSize(13);
        hint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(18), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        content.addView(hint);

        fragmentView = scrollView;
        return fragmentView;
    }

    private void addScale(LinearLayout parent, Context context, int percent) {
        int current = TelePremiumUiScale.getScalePercent(context);
        TextCell cell = new TextCell(context, getResourceProvider());
        cell.setTextAndValue(percent + "%", current == percent ? "Выбрано" : "", true);
        cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        cell.setOnClickListener(v -> {
            if (TelePremiumUiScale.getScalePercent(context) == percent) {
                return;
            }
            TelePremiumUiScale.setScalePercent(context, percent);
            TelePremiumUiScale.apply(context);
            Toast.makeText(context, "Масштаб интерфейса: " + percent + "%", Toast.LENGTH_SHORT).show();
            Activity activity = getParentActivity();
            if (activity != null) {
                activity.recreate();
            }
        });
        parent.addView(cell, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                AndroidUtilities.dp(58)
        ));
    }
}
