<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/osmo-logo-dark.svg">
  <img alt="Osmo" src="docs/osmo-logo-light.svg" width="480">
</picture>

### Your music, in focus

[![Latest release](https://img.shields.io/github/v/release/aarush-io/Osmo?style=for-the-badge&labelColor=0d1117)](https://github.com/aarush-io/Osmo/releases)
[![License](https://img.shields.io/github/license/aarush-io/Osmo?style=for-the-badge&labelColor=0d1117)](LICENSE)

[**Features**](#features) · [**Install**](#install-and-updates) · [**Credits**](#credits)

</div>

> [!NOTE]
> Osmo continues the GPL-3.0-licensed [upstream music player](https://github.com/steeben/Dial), itself based on [BitChord](https://github.com/kushagrasinghx/BitChord) by [kushagrasinghx](https://github.com/kushagrasinghx). The upstream history is retained. Everything from BitChord (Hi-Res audio, crossfade, word-synced lyrics, Spotify, scrobbling and more) is still here. For BitChord's full feature list, see its [original README](https://github.com/kushagrasinghx/BitChord#features).

> [!IMPORTANT]
> Not affiliated with, endorsed by, or connected to YouTube or Google in any way. Use it at your own discretion.

---

## Features

### Home
- **Speed dial.** Your pinned songs, albums, playlists and artists in a grid, the first thing you see. Hold a tile to move it or open its menu.
- **Mixes.** A page of mixes from Spotify and YouTube Music, one swipe away.
- **Artist picks.** The artists you pin and play most.
- **I'm Feeling Lucky.** A die that starts a radio from something you've liked or played lately.
- **For You.** New releases, recents and recommendations below the dials.

### Library
- **One library.** Your YouTube Music library, uploads, device files, downloads and Spotify together.
- **Quick to browse.** An A–Z index, list or grid per tab, and a default library to open on.
- **Fix a song's details.** Compare your file with its YouTube Music match and keep the title, artist, album and cover you prefer.

### Player
- **Colours follow the song**, and the player opens out of the mini player.
- **Lyrics.** The current line on the player, or the full synced sheet.
- **Canvas.** Spotify Canvas video behind compact controls.
- **Queue.** What's next, with the controls still in reach.
- **Sideways.** Video on the left, lyrics on the right.

### Customize
- **Theme and style.** System, Light or Dark, in Glass, Frost or Solid, with adjustable transparency, blur and saturation.
- **App icon.** Four colours, dark or bright.
- **Progress bar.** Glow, Wave or Plain.
- **Home grid.** Choose the picks, grid size, tile names and tile style.
- **Navigation bar.** Reorder the tabs and choose the start screen.

And more, on top of everything BitChord already does.

---

## Screenshots

These screenshots come from [Dial's repository](https://github.com/steeben/Dial) and show the shared app interface.

<div align="center">

<img src="docs/screenshots/home-dials.jpg" width="100%" alt="Home: speed dial in light and dark themes, mixes, and pinned artists" />

<img src="docs/screenshots/library.jpg" width="100%" alt="Library: For You, library views, songs, and song details" />

<img src="docs/screenshots/pages-wide.jpg" width="100%" alt="Pages: a playlist, an album, and an artist" />

<img src="docs/screenshots/player.jpg" width="100%" alt="Player: now playing, Canvas, queue, and video with lyrics" />

<img src="docs/screenshots/customize.jpg" width="100%" alt="Customize: home, app icons, progress styles, and appearance settings" />

</div>

---

## Install and updates

Download the latest APK from the [Osmo Releases](https://github.com/aarush-io/Osmo/releases) page. Each release has four APK files:

- **arm64-v8a.** For most phones. Try this file first.
- **armeabi-v7a.** For phones with a 32-bit system. Many Android Go phones are in this group. Use this file if the first file does not install.
- **x86_64.** For emulators and for devices with an x86 processor.
- **universal.** This file installs on all devices. It is the largest file. Use it if you do not know which file to select.

Before you install, you must permit "Install unknown apps" for the app that you use to download the file.

The app installs as **Osmo**, separately from the official BitChord, so the two can sit side by side without sharing app data. You'll need to sign in again the first time.

Each release is signed with the same key, so a new version installs over the old one and keeps your sign-in and settings. The app also checks this repository for updates when it launches, and Settings › Miscellaneous › Check for updates asks on demand.

To build it yourself, see [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Credits

This project builds on the [upstream music player](https://github.com/steeben/Dial), [BitChord](https://github.com/kushagrasinghx/BitChord) and their contributors. The lyrics providers, audio sources and other dependencies are credited in BitChord's [upstream README](https://github.com/kushagrasinghx/BitChord#features). The uploaded-songs fix draws on how [Metrolist](https://github.com/MetrolistGroup/Metrolist) and its InnerTube library handle uploads.

If you like BitChord, consider [supporting its author](https://github.com/kushagrasinghx/BitChord#support).

## Disclaimer

Osmo is an independent, community-driven third-party audio player and client. It is **not** associated with Google LLC, YouTube Music, Deezer, Telegram, or any of their parent companies. It does not host, upload, or store copyrighted music files, and it is provided for personal, educational and fair-use purposes. You are responsible for ensuring your use follows your local copyright laws and the YouTube Terms of Service.

## License

Licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE).
