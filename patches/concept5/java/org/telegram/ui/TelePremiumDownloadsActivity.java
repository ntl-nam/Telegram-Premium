package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.SearchDownloadsContainer;

public class TelePremiumDownloadsActivity extends BaseFragment {

    private final int account;

    public TelePremiumDownloadsActivity(int account) {
        this.account = account;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Загрузки");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        SearchDownloadsContainer downloads = new SearchDownloadsContainer(this, account);
        fragmentView = downloads;
        return fragmentView;
    }
}
