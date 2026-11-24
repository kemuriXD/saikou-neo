# Privacy policy

Last updated: 24 November 2026

Saikou Neo doesn't run servers of its own. The only data it collects about you
is in crash and bug reports, described below.

## What stays on your device

- Your AniList or MyAnimeList sign-in token. It's only sent to the service it
  belongs to, and never appears in a report.
- Your settings, watch progress and installed extensions.

Uninstalling the app, or clearing its data, removes all of this.

## What leaves your device

- The app fetches anime information from AniList and MyAnimeList and, when
  you're signed in, updates your list there. They receive your requests and your
  token, under their own privacy policies:
  [AniList](https://anilist.co/terms) and
  [MyAnimeList](https://myanimelist.net/about/privacy_policy).
- The app fetches artwork for the shows you look at from AniZip and fanart.tv.
  They see which show it asks about, but never your token.
- When you sign in on the TV with the companion app, your account
  is sent from the phone to the TV over your local network, encrypted. It
  doesn't go through the internet or any server of ours.
- Extensions you install make their own network requests to
  the sources they use. Saikou Neo doesn't see or control what those sources
  collect.
- Crash and bug reports, described next.

## Crash and bug reports

When the app crashes or stops responding, or hits an error it recovers from, it
sends a crash report. When you send a bug report from the TV or the phone, the
app sends that too. This applies to every release channel (alpha, beta and
stable).

A report contains:

- Your AniList or MyAnimeList user ID and name, if you're signed in.
- The app version and release channel.
- Device details: model, Android version, memory, free storage, network type
  and whether it's a TV or a phone.
- What you were doing: the current screen, the anime and episode, and the
  extension in use with its version.
- Recent app logs. Tokens, sign-in headers, cookies, web address parameters
  and IP addresses are removed before the report is sent.
- For a crash, the error and where in the app it happened. This can name the
  extension in use.
- For a crash in native code, a snapshot of the app's memory at that moment.
  Unlike the logs, it can't be cleaned first.
- For a crash, the names of the app's running tasks. These can include the
  address of a site the app was connected to, such as one an extension uses.

A bug report also contains a screenshot of the app, the category you picked,
and any description or screenshots you add on the phone.

Reports go to Google Firebase: crash reports to Firebase Crashlytics and bug
reports to Cloud Firestore. The app signs in to Firebase anonymously to send a
bug report, so there's no account with us. Crashlytics also collects its own
install ID and device data. Google handles all of this under the
[Firebase privacy terms](https://firebase.google.com/support/privacy).

Reports are deleted after 90 days.

If a bug report becomes a GitHub issue, the issue holds only your description
and a report code. Logs and device details are never posted there.

Builds made without the project's Firebase settings, such as your own builds
from the source code, contain no Firebase code and send no reports.

### Turning reports off

- Crash reports are on by default. You can turn them off in Settings.
- A bug report is only sent when you press Send, after the app shows you what's
  in it.

### Deleting your reports

Email [hello@kemuri.vip](mailto:hello@kemuri.vip) with your AniList or
MyAnimeList name, or the report code, and the reports will be deleted. Please
don't ask in a GitHub issue, because issues are public.

## What we don't do

- No analytics or advertising.
- No accounts with us.
- Nothing is sold, and reports are only used to fix the app.

## Children

Saikou Neo isn't aimed at children under 13.

## Changes

This policy may change. Changes are made in this file, and the date at the top
shows when it last changed.

## Contact

Questions go to the project's
[GitHub issues](https://github.com/kemuriXD/saikou-neo/issues). For anything
private, such as deleting a report, email
[hello@kemuri.vip](mailto:hello@kemuri.vip).
