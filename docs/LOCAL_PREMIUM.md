# TelePremium Local Premium Core

TelePremium does not spoof Telegram Premium account state.

The client uses Telegram only as the transport/account backend. Features that can be implemented on the client are owned by TelePremium and must not depend on `UserConfig.isPremium()`.

## Rules

1. Never globally force Telegram `isPremium()` to true.
2. Never send a paid Telegram mutation just to unlock a TelePremium-local feature.
3. Prefer a TelePremium store/controller and render-time override.
4. Server entitlements remain authoritative only for capabilities that physically require Telegram server support.
5. A local feature may intentionally be visible only inside TelePremium.

## Implemented locally

- Up to 10 Telegram account slots.
- Independent network profile foundation per account.
- Sponsored-message suppression.
- Bundled launcher icons.
- Local/system Voice-to-Text path.
- Client-side UI scale.
- TelePremium control/file/network/account centers.
- User name color.
- User profile color.
- Name background emoji.
- Profile background emoji.
- Local emoji status for the current user, including optional expiration.
- Local custom double-tap reaction selection.

The four appearance values above are stored in `TelePremiumPeerAppearanceStore`. They are read through `UserObject` and are not sent with `account.updateColor`.

Emoji status is stored in `TelePremiumEmojiStatusStore`, keyed by account slot + user ID. The user status selector never sends `account.updateEmojiStatus`; only channel status continues to use Telegram's channel RPC. Local status rendering is hooked into `UserObject` and the self-profile UI.

The double-tap reaction is safe to expose locally because Telegram's `MediaDataController.setDoubleTapReaction()` stores the choice in local emoji settings. TelePremium therefore exposes the custom selector without changing Telegram Premium account state.

## Premium upsell isolation

TelePremium does not open Telegram subscription purchase surfaces.

- Navigation to `PremiumPreviewFragment` is routed to `TelePremiumOverviewActivity`.
- `PremiumFeatureBottomSheet` and `PremiumPreviewBottomSheet` redirect to the TelePremium overview instead of presenting Telegram billing.
- `PremiumPreviewFragment.buyPremium(...)` has a backstop that routes to TelePremium and never starts Telegram billing.
- Animated-emoji pack tabs, headers, and add buttons in the TelePremium picker do not show Telegram Premium lock chrome.

This is UI/routing isolation only. It does not forge Telegram Premium state and does not bypass server-side entitlement checks.

## Next local-premium migration groups

### Group A — client/UI features

Good candidates for fully local implementation:

- additional app icons;
- custom themes and UI effects;
- profile/name appearance;
- local emoji decorations and local status decorations;
- local chat organization and file indexing;
- local download management;
- local transcription;
- local translation providers;
- additional account management;
- local privacy/display controls;
- client-side media tools;
- local sticker/emoji presentation where no server mutation is needed.

### Group B — features needing a TelePremium substitute

Telegram may expose these as Premium but the client can provide a different implementation:

- translation -> TelePremium translation providers;
- voice transcription -> system/local/cloud STT;
- faster downloads -> TelePremium download scheduler/concurrency where Telegram permits;
- advanced file handling -> TelePremium File Center;
- advanced account/network management -> TelePremium account/network layer;
- some story/editor conveniences -> local editor/tooling where protocol permits.

### Group C — Telegram-server entitlements

These cannot be made into the real Telegram feature purely by a local gate removal:

- Telegram-side account limits and cloud quotas;
- server-enforced maximum upload size;
- Telegram-visible Premium badge;
- server-visible emoji status when the API rejects it;
- server-visible profile/name colors when the API rejects it;
- Telegram Business server features;
- server-side story quotas/priority rules;
- paid reactions/stickers or other assets requiring server entitlement;
- any RPC returning a Premium-required error.

For Group C, TelePremium should either provide a clearly local analogue or leave the Telegram server behavior untouched.

## Architecture direction

Every migrated feature should have:

- a TelePremium feature flag/controller;
- local persistence where required;
- rendering hooks;
- no dependency on Telegram purchase UI;
- a source invariant check preventing the old Premium gate from returning.
