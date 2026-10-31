<!-- markdownlint-disable MD033 MD041 -->

<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/assets/saikou-neo-dark.png">
    <img src=".github/assets/saikou-neo-light.png" alt="Saikou Neo" width="520">
  </picture>
  <h1>Saikou Neo</h1>
  <p><strong>Anime, made for your TV.</strong></p>
  <p>
    <a href="https://github.com/kemuriXD/saikou-neo/actions/workflows/android.yml"><img src="https://github.com/kemuriXD/saikou-neo/actions/workflows/android.yml/badge.svg?branch=main" alt="Android build status"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/licence-GPL--3.0--only-blue" alt="Licence: GPL-3.0-only"></a>
    <img src="https://img.shields.io/badge/platform-Android%20TV%20%7C%20Fire%20TV-3DDC84?logo=android&amp;logoColor=white" alt="Platform: Android TV and Fire TV">
    <img src="https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&amp;logoColor=white" alt="Kotlin Multiplatform">
    <a href="https://github.com/kemuriXD/saikou-neo/commits/main"><img src="https://img.shields.io/github/last-commit/kemuriXD/saikou-neo" alt="Last commit"></a>
  </p>
</div>

Saikou Neo is an anime app for Android TV and Fire TV. You browse and search on
the TV, sign in to AniList from your phone, and your progress syncs as you
watch.

It ships with no video sources. Playback comes from extensions you install
separately.

> [!IMPORTANT]
> Saikou Neo does not host or distribute any video or other media, and it ships
> with no extensions. Its developers don't make or endorse extensions that give
> access to content without the rights holder's permission, and aren't liable
> for how the app or any extension is used. You're responsible for what you
> install and watch. Read the full [disclaimer and terms of use](DISCLAIMER.md).

## Status

The app is being rebuilt from scratch, and there's no release yet. The first
public release is **v0.1.0**. Follow the work on the
[project board](https://github.com/users/kemuriXD/projects/2) or in the
[milestones](https://github.com/kemuriXD/saikou-neo/milestones).

[![Download the latest build](https://img.shields.io/badge/Download-latest%20build-FF4F91?style=for-the-badge&logo=android&logoColor=white)](https://github.com/kemuriXD/saikou-neo/actions/workflows/android.yml?query=branch%3Amain+is%3Asuccess)

Open the newest run and download `saikou-neo-tv-alpha-debug` for the TV or
`saikou-neo-companion-alpha-debug` for the phone. You need to be signed in to
GitHub. These are alpha debug builds, and each one expires after 7 days.

| Milestone | What it adds | Progress |
| --- | --- | --- |
| [v0.0.1 Foundation](https://github.com/kemuriXD/saikou-neo/milestone/7) | Project setup, CI and the TV design | ![v0.0.1 progress](https://img.shields.io/github/milestones/progress/kemuriXD/saikou-neo/7?label=) |
| [v0.0.2 Browse](https://github.com/kemuriXD/saikou-neo/milestone/8) | Home, details, episode lists, the top bar and settings | ![v0.0.2 progress](https://img.shields.io/github/milestones/progress/kemuriXD/saikou-neo/8?label=) |
| [v0.0.3 Design v2](https://github.com/kemuriXD/saikou-neo/milestone/12) | A new TV look with a side rail and full-screen artwork, plus search and Discover | ![v0.0.3 progress](https://img.shields.io/github/milestones/progress/kemuriXD/saikou-neo/12?label=) |
| [v0.0.4 Play](https://github.com/kemuriXD/saikou-neo/milestone/9) | Extensions, choosing a source and server, and the player | ![v0.0.4 progress](https://img.shields.io/github/milestones/progress/kemuriXD/saikou-neo/9?label=) |
| [v0.0.5 Sign in](https://github.com/kemuriXD/saikou-neo/milestone/10) | Phone companion, AniList sign-in and TV pairing | ![v0.0.5 progress](https://img.shields.io/github/milestones/progress/kemuriXD/saikou-neo/10?label=) |
| [v0.1.0 Sync](https://github.com/kemuriXD/saikou-neo/milestone/11) | Progress syncing and Continue Watching | ![v0.1.0 progress](https://img.shields.io/github/milestones/progress/kemuriXD/saikou-neo/11?label=) |

## How it's built

Two Android apps, one for the TV and one for the phone, share a Kotlin
Multiplatform core. Video sources come from separately installed extensions.

## Credits

| What | From |
| --- | --- |
| Anime information | [AniList](https://anilist.co), [AniZip](https://ani.zip), and filler and recaps from MyAnimeList through Tenrai |
| Artwork | [TMDB](https://www.themoviedb.org), [fanart.tv](https://fanart.tv) and [TheTVDB](https://thetvdb.com) |
| Ratings | AniList, [MyAnimeList](https://myanimelist.net) through [Tenrai](https://api.tenrai.org), and IMDb and Rotten Tomatoes through [OMDb](https://www.omdbapi.com) |

This product uses the TMDB API but is not endorsed or certified by TMDB. See
[CREDITS.md](CREDITS.md) for what each service provides and its terms.

## Contributing

Guidelines are still to be written. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

[GPL-3.0-only](LICENSE). Libraries, fonts and other third-party material keep
their own licences; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Using the app means agreeing to the [disclaimer and terms of use](DISCLAIMER.md).
See also the [privacy policy](PRIVACY.md).

---

<div align="center">
  <img src="https://count.getloli.com/@kemurixd-saikou-neo?theme=booru-lewd&amp;padding=6&amp;scale=0.6&amp;darkmode=0" alt="Approximate README visits, displayed with pixel-art anime characters" width="360">
  <p><sub>Approximate visits · <a href="https://github.com/journey-ad/Moe-Counter">Moe Counter</a></sub></p>
</div>
