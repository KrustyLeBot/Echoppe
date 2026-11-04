# Échoppe

<img src="branding/echoppe-512.png" width="128" align="right" alt="Échoppe icon">

An Android app that gathers your shopping sites in one place, one tab per site, without ads, trackers
or cookie banners, and without the sites pushing you into their own apps. It runs on Firefox's engine
(GeckoView) so that real browser extensions do the blocking. The interface is in French.

## What it does

- **No site by default.** You add each site yourself (address, optional tab name). Each site is a tab
  at the top; long-press a tab and drag it to reorder, or use the arrows in Réglages.
- **Logins stay.** Every site keeps its cookies between launches, so you stay signed in.
- **Tabs stay loaded.** Switching tabs never reloads a page. The back button walks the site's
  history; on its first page, the app goes to the background.
- **Each tab reopens where you left it.** The last page of each site is remembered on the phone (only
  pages of the site itself: a payment page of the bank is not reopened). Changing a site's address, or
  « Accueil du site » in the tab menu, goes back to its home page.
- **Ads, trackers, app banners, chat widgets and cookie banners are gone**, by uBlock Origin and
  Consent-O-Matic (below). From the tab menu, each one's popup turns it off for that site only, and
  uBlock's element picker removes anything left on a page.
- **Per-site options** (tab menu → Modifier, or Réglages): desktop version; « Effacer cookies et
  données » (logs you out of that site only); delete the site.
- **Downloads** (invoices, PDFs) go to the phone's Downloads folder.
- **File uploads** (a photo for a return, say) use Android's file picker.

## Privacy: tracker-free, not a security tool

The goal is that no site, and no tracker embedded in it, can follow you from one shop to another.

### Extensions

Android's WebView (Chromium) cannot run browser extensions, and neither can Chrome for Android. That
is why the app embeds [GeckoView](https://mozilla.github.io/geckoview/), Mozilla's embeddable Firefox,
which runs the same WebExtensions as Firefox for Android. Two of them are installed from
addons.mozilla.org (signed by Mozilla, both « recommended » by Mozilla for Android) the first time the
app starts; no page loads before. The app checks for new versions every 4 days (Réglages → Mettre à
jour les extensions does it by hand).

| Extension | Licence | Job |
|---|---|---|
| [uBlock Origin](https://github.com/gorhill/uBlock) | GPL-3.0 | Blocks ads, trackers and malware with its filter lists (EasyList, EasyPrivacy, uBlock filters…), which it updates itself. Two settings once per phone, from Réglages (a banner reminds you until done): subscribe to [Fanboy's Mobile Notifications](https://secure.fanboy.co.nz/fanboy-mobile-notifications.txt) (« open in the app » banners, Amazon's included; it is in no list of uBlock's catalogue), through uBlock's own subscribe page, one tap; and tick the « Nuisances » (Annoyances) lists: cookie notices, chat widgets, newsletter pop-ups. Its popup (tab menu → uBlock Origin) turns it off for one site or picks an element to hide for good. |
| [Consent-O-Matic](https://github.com/cavi-au/Consent-O-Matic) | MIT | Answers cookie consent pop-ups with « refuse », on every site that uses one of the 200+ consent tools it knows (OneTrust, Didomi, Quantcast, Cookiebot, TrustArc, Sourcepoint…); its rules are updated from its repository. Defaults: every category off, so only necessary cookies (login, cart) remain. |

No tool refuses cookies on 100 % of sites: a home-made banner Consent-O-Matic does not know is hidden
by uBlock's cookie notice lists instead, which, under the GDPR, means no consent was given.

### Firefox's own protections

| Measure | How |
|---|---|
| One isolated context per site | Each tab browses in its own context (`site_<id>`, Firefox's "containers"): separate cookies, storage, cache. A tracker loaded by two sites sees two strangers. |
| Enhanced Tracking Protection, strict | Trackers, fingerprinters, cryptominers and social trackers blocked by Firefox itself. |
| Total Cookie Protection | Third-party cookies are partitioned per site. |
| Tracking parameters | Stripped from links (`utm_*`, `fbclid`…). |
| Global Privacy Control | Sent to every site (the legal "do not sell or share" signal). |
| No exit to other apps | Only `http(s)` pages load. `market://`, `intent://`, custom schemes and Play Store / App Store links are refused; an `intent://` link with a web fallback opens that fallback in the tab. A page can never start another app. |
| No pop-up windows | `target=_blank` and `window.open` open in the same tab. A window opened empty, then sent elsewhere (Amazon's « Ouvrir » in its app banner), is an invisible catcher: a web page opens in the tab, an app link is refused. |
| No permissions | Location, camera, microphone and notifications are always refused. |
| No telemetry | No analytics in the app, Firefox telemetry is not set up, login saving is off. App backup is off. |

Deleting a site deletes its context (cookies, storage, cache); « Effacer cookies et données » does the
same and keeps the site.

Limits, accepted:

- **Fingerprinting** is reduced by Firefox's strict mode, not removed: every tab is the same phone.
- **Same IP address** for every site: use a VPN if that matters.
- **Firefox security fixes** arrive with Échoppe updates: GeckoView is pinned in `app/build.gradle.kts`
  and must be bumped with each Firefox release (every 4 weeks).
- **APK size**: about 90 MB, almost all of it the Firefox engine (arm64 phones only).

## Data

The site list is `Documents/Echoppe/sites.json`:

```json
{
  "format_version": 1,
  "sites": [
    { "id": "k3v9x0a1qz", "name": "Amazon", "url": "https://amazon.fr/", "desktop": false }
  ]
}
```

In tab order; unknown fields are ignored. `id` is random and never changes: it names the site's private context, so renaming a site
keeps its login. The file is written atomically (`.<name>.synctmp-<uuid>`, flushed, renamed). An
unreadable file is reported in the app and never overwritten; a `format_version` newer than the app is
refused. Cookies, logins, history, each tab's last page, the selected tab and the extensions' settings
are device-only and never in that folder.

That folder needs the "all files access" permission, which the app asks for with a banner; until it is
granted the list is empty and cannot be changed.

## Proton Drive sync (Synchrotron)

The folder is set up in [Synchrotron](https://github.com/KrustyLeBot/Synchrotron) (add
`Documents/Echoppe`, e.g. with `Apps/Echoppe` on Proton Drive); the app itself has no sync settings.
Each time it opens it asks Synchrotron for a sync; the site list is read-only while it runs (browsing
goes on) and is re-read afterwards. A site added on one phone appears on the others, logged out: logins
never travel.

## Get the APK

Every push to `main` runs the unit tests, builds the APK signed with the release key and publishes it
as the single `latest` release (`Echoppe.apk`, titled "Échoppe build N"; N is also the APK version
code). Download it from the repository's Releases page. Signing key, secrets and CI: `docs/RELEASE.md`.

In the app, a banner offers each new build when the app opens (Réglages → Mise à jour checks by hand);
it downloads the APK, checks its size and SHA-256, and opens Android's install screen (the phone asks
once to allow installs from this app). The downloaded file is deleted the next time the updated app
starts. Every build is signed with the same key, so updates install over the old app.

## Build locally

JDK 17 and the Android SDK platform 37.2 are required (GeckoView is built against it, hence AGP 9.5
and Kotlin 2.4, newer than the rest of the suite). Without the release key, a local build is signed
with the SDK's debug key, so it cannot update an installed release build:

```
./gradlew testDebugUnitTest assembleRelease
```

## Project layout

```
app/src/main/java/app/echoppe/
  EchoppeApp.kt, MainActivity.kt, EchoppeViewModel.kt   app, activity, state
  data/       Site + SiteUrl (address parsing), SitesFile (JSON format), SiteStore, AtomicFiles
  web/        Browser (the Gecko engine and its protections), Extensions (install, update, popups),
              Tabs (one session and context per site, last page), Navigation (what may load),
              Prompts (page dialogs), Downloads
  ui/         MainScreen (tabs, menu, page, extension popups), SettingsScreen, SiteDialog, theme
  suite/      suite kit copied from Synchrotron (update banner and flow, shared settings UI)
  sync/       SynchrotronClient, copy of Synchrotron's client library
app/src/test/   unit tests of the pure logic
branding/   icon: echoppe.svg (izakaya), adaptive-icon layers, render-icons.js, monochrome.py
```

`node branding/render-icons.js` re-renders the launcher layers from the SVGs (needs Playwright), then
`python3 branding/monochrome.py` derives the themed-icon layer from the foreground.
