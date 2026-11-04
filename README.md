# Échoppe

<img src="branding/echoppe-512.png" width="128" align="right" alt="Échoppe icon">

An Android app that gathers your shopping sites in one place, one tab per site, without trackers and
without the sites pushing you into their own apps. The interface is in French.

## What it does

- **No site by default.** You add each site yourself (address, optional tab name). Each site is a tab
  at the top; long-press a tab and drag it to reorder, or use the arrows in Réglages.
- **Logins stay.** Every site keeps its cookies between launches, so you stay signed in.
- **Tabs stay loaded.** Each site has its own live WebView: switching tabs never reloads a page. The
  back button walks the site's history; on its first page, the app goes to the background.
- **Per-site options** (tab menu → Modifier, or Réglages): desktop version, tracker blocking on/off,
  « Effacer cookies et données » (logs you out of that site only), delete the site.
- **Downloads** (invoices, PDFs) go to the phone's Downloads folder, with that site's cookies only.
- **File uploads** (a photo for a return, say) use Android's file picker.

## Privacy: tracker-free, not a security tool

The goal is that no site, and no tracker embedded in it, can follow you from one shop to another.

| Measure | How |
|---|---|
| One isolated profile per site | Each site's WebView uses its own [WebView profile](https://developer.android.com/reference/androidx/webkit/ProfileStore) (`site_<id>`): separate cookies, local storage, IndexedDB, HTTP cache and service workers. A tracker loaded by two sites sees two strangers. |
| Third-party cookies refused | In every tab. |
| Tracker domains blocked | `app/src/main/assets/trackers.txt` (about 150 domains: Google Analytics/Ads, social pixels, Criteo and the ad exchanges, session replay such as Hotjar or Contentsquare, French audience measurement, affiliate trackers, deep-link SDKs). A request to one of them, or a subdomain, gets an empty answer. Payment, anti-fraud, captcha, consent banners and CDNs are left out on purpose, since blocking them breaks checkouts. Blocking can be turned off per site if a page breaks. The tab menu shows how many requests were blocked. |
| Global Privacy Control | `navigator.globalPrivacyControl` is true on every page (the legal "do not sell or share" signal). |
| No app detection | `navigator.getInstalledRelatedApps()` always answers "none". |
| No app identifier | The `X-Requested-With` header (the app's package name) is not sent; ad attribution registration is off. |
| Ordinary user agent | The WebView markers (`; wv`, `Version/4.0`) are removed, so the app reads as Chrome on Android, the most common browser, instead of standing out. |
| No exit to other apps | Only `http(s)` pages load. `market://`, `intent://`, custom schemes and Play Store / App Store links are refused; an `intent://` link with a web fallback opens that fallback in the tab. Nothing a page does starts an Android intent. |
| No telemetry | No analytics in the app; WebView usage statistics are opted out (`MetricsOptOut`). App backup is off, so cookies never leave the phone that way. |

Isolation needs an "Android System WebView" recent enough to support profiles (`MULTI_PROFILE`); the
Play Store keeps it up to date. On an older one the sites share one cookie store; Réglages says so in red, and
« Effacer cookies et données » then clears every site.

Deleting a site deletes its profile. Android only allows that once no page of the current session has
used it, so the data is wiped at the next app start at the latest.

Limits, accepted:

- **Fingerprinting** (screen size, fonts, GPU) is not countered: every tab is the same phone.
- **Same IP address** for every site: use a VPN if that matters.
- **Google sign-in** is refused by Google inside WebViews. Use each site's own login.
- **"Open in the app" banners** drawn by a site's own page stay visible (tapping them does nothing).
- **Safe Browsing** stays on: it checks pages against Google's phishing list using hash prefixes,
  which protects against fake shops.

## Data

The site list is `Documents/Echoppe/sites.json`:

```json
{
  "format_version": 1,
  "sites": [
    { "id": "k3v9x0a1qz", "name": "Amazon", "url": "https://amazon.fr/", "desktop": false, "block_trackers": true }
  ]
}
```

In tab order. `id` is random and never changes: it names the site's private profile, so renaming a site
keeps its login. The file is written atomically (`.<name>.synctmp-<uuid>`, flushed, renamed). An
unreadable file is reported in the app and never overwritten; a `format_version` newer than the app is
refused. Cookies, logins, history and the selected tab are device-only and never in that folder.

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

JDK 17 and the Android SDK 35 are required. Without the release key, a local build is signed with the
SDK's debug key, so it cannot update an installed release build:

```
./gradlew testDebugUnitTest assembleRelease
```

## Project layout

```
app/src/main/java/app/echoppe/
  EchoppeApp.kt, MainActivity.kt, EchoppeViewModel.kt   app, activity (owns the WebViews), state
  data/       Site + SiteUrl (address parsing), SitesFile (JSON format), SiteStore, AtomicFiles
  web/        WebPool (one WebView and profile per site), Navigation (what may load),
              Trackers (blocklist matching), UserAgents
  ui/         MainScreen (tabs, menu, web view), SettingsScreen, SiteDialog, theme and palette
  suite/      suite kit copied from Synchrotron (update banner and flow, shared settings UI)
  sync/       SynchrotronClient, copy of Synchrotron's client library
app/src/main/assets/trackers.txt   blocked tracker domains
app/src/test/                       unit tests of the pure logic
branding/   icon: echoppe.svg (izakaya), adaptive-icon layers, render-icons.js, monochrome.py
```

`node branding/render-icons.js` re-renders the launcher layers from the SVGs (needs Playwright), then
`python3 branding/monochrome.py` derives the themed-icon layer from the foreground.
