# TelePremium Concept 5 — implementation plan

Source-of-truth: `mallstorm/Telegram-Premium`.

## Goal

Concept 5 turns TelePremium into a multi-account Telegram client with:

- up to 10 accounts;
- an independent network route per account;
- a TelePremium control center;
- account/network/file management;
- later: route resilience scoring, unified local file index, storage analytics and full Concept 5 visual polish.

## Implemented in Pass 5A/5B foundation

### TelePremium Center
A new settings entry opens a dedicated TelePremium center with shortcuts to:

- Multi-account center
- Network and stability
- File Center
- Translation/language
- Cache and storage
- Themes and interface

### Account Center
The initial Account Center:

- lists all activated account slots;
- shows real Premium/Standard server status;
- shows the effective network route summary and current ping;
- switches accounts on tap;
- opens the account network profile on long press;
- allows adding an account into a free slot.

### Per-account network core
New classes:

- `AccountNetworkProfile`
- `AccountNetworkStore`
- `AccountNetworkController`

Each Telegram account slot can have its own profile:

- inherit stock Telegram global route;
- direct;
- auto foundation;
- MTProto proxy;
- SOCKS5 proxy;
- IPv4 / IPv6 / mixed strategy.

The patch keeps stock global Telegram proxy behavior for accounts that do not have an explicit TelePremium profile.

An explicit profile is applied through the already account-indexed native APIs:

- `native_setProxySettings(account, ...)`
- `native_setIpStrategy(account, ...)`

Stock `ConnectionsManager.setProxySettings(...)` is patched so a global proxy change does not overwrite explicit per-account TelePremium profiles.

### Account binding
Network settings are stored per account slot and bound to the Telegram user id. If a different user logs into the same slot, the old explicit profile is discarded instead of leaking route credentials to the new account.

### Network Center
The initial Network Center:

- shows connected / active account count;
- shows route and ping per account;
- opens an account-specific network profile editor.

### Network profile editor
Initial editor supports:

- route mode;
- profile name;
- region label;
- host/IP;
- port;
- SOCKS5 username/password;
- MTProto secret;
- IP strategy;
- proxy availability check;
- save/apply for one account only;
- reset back to Telegram global route.

### File Center foundation
Initial File Center:

- exposes downloads for every activated account;
- embeds Telegram's existing `SearchDownloadsContainer`;
- links to storage/cache management;
- links to normal Telegram download settings.

No files are duplicated.

## Not yet complete

These items are intentionally scheduled after the foundation compiles and is runtime-tested:

1. Concept 5 card-based visual polish matching the approved mockup.
2. Automatic RouteEvaluator / fallback chain.
3. Stability score, anti-flapping and route history.
4. Reusable named network profiles assigned to multiple accounts.
5. Secure Keystore-backed proxy credential storage.
6. Unified SQLite File Center index across all accounts.
7. Type filters: PDF / DOCX / ZIP / APK / video / photo / audio.
8. Storage analytics per account.
9. Per-chat translation overrides.
10. Dark Concept 5 theme.

## Safety invariants

Concept 5 must not break or replace Telegram's:

- MTProto authorization;
- DC configuration;
- messages;
- secret chats;
- calls;
- push;
- uploads/downloads;
- QR login;
- notifications;
- media streaming.

The new layer orchestrates existing Telegram components instead of reimplementing the protocol.
