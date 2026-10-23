# AerialViews+ for Fire OS 6 (fork)

Fork of [naveeneppalapally/AerialViews-Plus](https://github.com/naveeneppalapally/AerialViews-Plus) (GPLv3, see `LICENSE` and `CREDITS.md`)
tested on a Fire TV Stick 4K gen 1 (Fire OS 6.7, Android 7.1). Separate from the MIT-licensed Free My TV launcher.

## What changes
- Own application id (`com.hectorx24.aerialviewsplus`), installs next to the original Aerial Views; Firebase/analytics removed (F-Droid flavor).
- **Mix by share** (Settings > Sources > YouTube): *Porcentaje de YouTube* (default 43 %), *Porcentaje de tus enlaces* (default 7 %); the rest is Apple TV etc.
  If the YouTube pool is small, fewer Apple items are used per pass instead of repeating YouTube videos.
- **Own links** (*Mis enlaces de YouTube*): one per line, `URL` and an optional cut point (`1:30`). They are always part of the offer, repeated spaced out at the chosen percentage.
  *Reproducir uno de mis enlaces ahora* starts the screensaver with that one first.
- **End cut**: a manual cut point, else [SponsorBlock](https://sponsor.ajay.app) `outro`/`selfpromo`/`interaction` at the end of the video (looked up once, cached), else the whole video.
  Works automatically for any video added later. Optional overrides for testing: `Android/data/<package>/files/yt_endcuts.txt` (`ID=SECONDS`, `*=SECONDS`, `share=N`, `pinshare=N`).
- **Glass clock** (Settings > Overlays > Clock > *Reloj estilo cristal*): large, tall, condensed, uniform translucent white, no painted gradient.
- Sound: *Playlist > Volume* (existing setting; Apple videos have no audio, so it only affects YouTube).

## Build
JDK 21, Android SDK 37: `./gradlew :app:assembleFdroidRelease` (signing from `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; no keys in the repository).
Unit test: `./gradlew :app:testFdroidDebugUnitTest --tests '*YouTubeShareTest*'`.

## Power timers on Fire OS (outside this app)
`settings put system screen_off_timeout <ms>` starts the screensaver; `settings put secure sleep_timeout <ms>` puts the device to sleep (Fire OS then sends HDMI-CEC standby). Aerial's own black-out timer tops out at 60 min.
