# Upstream baseline

TelePremium uses the official Telegram Android repository as a pinned Git submodule.

- Upstream: `DrKLO/Telegram`
- Revision: `fb2e545101f41303f1e2712de2e7611a9335f1c3`
- Upstream release: 11.4.2 (5469)
- Submodule path: `upstream/Telegram`

## Why not an 11.3.4 source tag?

The control APK used for Pass 1–3 reports Telegram 11.3.4. The official public Telegram Android Git history does not expose a corresponding 11.3.4 release commit/tag: the published history in this interval moves from 11.2.3 (5335) to 11.4.2 (5469).

Therefore this repository deliberately uses the nearest later official source revision and keeps all TelePremium behavior as explicit source transformations.

This distinction matters: a future source build from this repository is not claimed to be byte-for-byte reproducible with the earlier patched 11.3.4 APK.

## Update policy

The app's own APK updater is disabled by TelePremium patches. Telegram protocol/configuration/DC updates are not disabled.

Updating the upstream submodule must be treated as a deliberate migration:
1. move the gitlink,
2. run the patch script,
3. run source verification,
4. compile,
5. regression-test login, chat sync, media, document opening, Premium translation and all account slots.
