package org.telegram.ui;

import org.telegram.ui.ActionBar.BaseFragment;

/**
 * TelePremium navigation policy for Telegram Premium upsell surfaces.
 *
 * TelePremium never spoofs Telegram's server-side Premium entitlement. Instead,
 * any client-side navigation that would open Telegram's subscription preview is
 * redirected to TelePremium's own local feature overview.
 */
public final class TelePremiumPremiumRouter {

    private TelePremiumPremiumRouter() {
    }

    public static BaseFragment route(BaseFragment fragment) {
        if (fragment instanceof PremiumPreviewFragment) {
            return new TelePremiumOverviewActivity();
        }
        return fragment;
    }

    public static void openOverview(BaseFragment from) {
        if (from != null) {
            from.presentFragment(new TelePremiumOverviewActivity());
        }
    }

    public static boolean localAnimatedEmojiUnlocked() {
        return true;
    }

    public static boolean hideTelegramPremiumUpsell() {
        return true;
    }
}
