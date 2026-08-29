<div align="center">

<img src="assets/Melox-icon.png" alt="Melox app icon" width="96"><br>

# [MeiloX](https://github.com/NEORUAA/MeiloX)

#### An Android local music player based on <a href="https://github.com/compose-miuix-ui/miuix">Miuix</a>

![Android](https://img.shields.io/badge/Android-9%2B-3DDC84?style=flat&logo=android&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue)
![Miuix](https://img.shields.io/badge/Miuix-0.9.3-4F6BED?style=flat)
![Media3](https://img.shields.io/badge/Media3-1.11.0-4F6BED?style=flat)

</div>

---

## Overview

MeiloX is an Android local music player built with Jetpack Compose, Miuix, and AndroidX Media3.

## Features

### Library and Home

- Scan the system media library or restrict the scan scope to selected folders
- Search, sort, and browse songs, albums, artists, and folders with alphabetical indexes
- Open dedicated album and artist details, then start playback from the current page queue
- View random recommendations and recently added tracks on Home
- View music library statistics
- Choose a scan refresh policy
- Configure folder scan scopes and block folders

### Playback, queue, and restoration

- Seek through tracks, use Previous and Next, and switch between ordered, repeat-one, and random playback
- Open or clear the queue, remove individual songs, play a song next, or add songs to the current queue
- Restore the queue, current track, and playback position after reopening or process restart without starting playback automatically
- Open an external audio file through Android and play it directly in Melox

### Local lyrics and track information

- Support character- and word-timed lyrics, translation lines, text size, and font weight adjustments
- Choose lyric alignment, blur inactive lines, and whether playback controls remain visible on the Lyrics page
- Show title, artist, album, format, bitrate, sample rate, bit depth, duration, and file location
- With Music Tag Editor or Lyrico installed, jump to editing from track actions

### Player and appearance

- Follow the system theme, or use light and dark themes
- Use dynamic colors based on the current artwork or system wallpaper
- Enable blurred artwork, Dynamic Flow, a floating bottom bar, and liquid glass effects
- Use Miuix or AOSP page transitions, progressive top-bar blur, and the hide-bottom-bar option
- Left-align the player title; overflowing titles scroll once
- Choose the default startup page

## Requirements

- Android 9 (API 28) or later
- Liquid glass and other runtime visual effects require Android 13 or later

## Supported formats and permissions

- Supports local AAC, AIFF, ALAC, APE, FLAC, M4A, MP3, MP4, OGA, OGG, OPUS, WAV/WAVE, and WMA audio files; playback also depends on the Android and Media3 decoders
- The first scan requires music access: “Music and audio” on Android 13 or later, or storage read access on Android 12 and earlier
- When a custom folder is selected, grant access to that folder and its subdirectories through the system folder picker
- Current APKs are provided for `arm64-v8a` only

## Download and install

- Stable builds: download from [GitHub Releases](https://github.com/Inefy-03/Melox/releases)
- Test builds: follow the [Telegram channel](https://t.me/MeloxPlayer)

## Credits

- [Miuix](https://github.com/compose-miuix-ui/miuix) - UI components and design system
- [AndroidX Media3](https://developer.android.com/media/media3) - local playback and system media sessions

MeiloX is under active development. Report issues through [Issues](https://github.com/Inefy-03/Melox/issues) or submit a [Pull Request](https://github.com/Inefy-03/Melox/pulls).
