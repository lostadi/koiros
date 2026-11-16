<div align="center">

# Koiros

### Lee Ostadi’s evolving computational wallpaper

Living mathematics. Slow currents. Color that unfolds.

[Download for Android](https://github.com/lostadi/koiros/releases/latest) · [Website](https://leoostadi.com/koiros/) · [Build it yourself](docs/BUILDING.md)

</div>

Koiros turns an Android background into a small, continuously evolving world.
Koi-like creatures drift, gather, separate, and change form. Neon gradients
travel gently across their bodies. Each scene grows from interacting Game of
Life fields, frequency projections, and coupled chaotic dynamics.

Created by **Lee Daghlar Ostadi**, building on **Artificial Koi** by
**Studio Coldstream / Mathew Molt** and **GoL Screensaver** by **Harold Martin**.
[Full credits and provenance](docs/CREDITS.md).

The name combines *koi* with *kairos*, the idea of the right or opportune
moment—a name for forms unfolding in their own time.

## Install and make it yours

1. On an Android device, open the [latest release](https://github.com/lostadi/koiros/releases/latest) and download **Koiros-1.8.apk** from Assets.
2. Open the APK. If Android asks, allow that browser or file manager to install apps, then install Koiros.
3. Open **Koiros**, adjust the preview, and tap **Set wallpaper**. Confirm in Android’s wallpaper picker.

Requires **Android 8.0 or newer** and live-wallpaper support. No root, account,
network connection, Ostadix installation, or paid service is required to run
the app. Home-screen and lock-screen options depend on the device.
[Compatibility and troubleshooting](docs/COMPATIBILITY.md).

For a calm starting point, keep **Drift** and **Shape evolution** at **0.18×**.
**Color drift** defaults to **0.25×**. Choose **1 Hz** for slower Life generations
or **4 Hz** for more frequent simulation updates; drawing runs independently.
The sliders reach **0.02×** for very gradual motion.

## What evolves

- **Ten forms:** Koi, Jellyfish, Manta, Coral, Helix, Breathe, Bloom, Trance, Koi II, and Nebula, continuously morphing between one another.
- **One to nine creatures:** keep a fixed count or let the population change gradually up to your chosen maximum.
- **Living color:** 48-hue spatial gradients, slow palette drift, and gentle color exchange between nearby forms.
- **Shared motion:** curved currents, soft collisions, and optional fading tap ripples.
- **Ongoing dynamics:** nine independent Conway Life boards feed measured frequency projections; coupled Lorenz systems keep driving visual evolution when boards settle.
- **A quiet background:** independent speed controls, adaptive point density, and pause while hidden.

This is a simulation rendered on your device, rather than a prerecorded video.
Its chaotic driver and pattern renewal encourage continued variation; it does
not promise mathematically unique frames forever.

## Build and verify

The project is Java with Android framework APIs and Python standard-library
build tools. It has no Gradle, Maven, or third-party runtime dependencies.
Install the documented JDK and Android SDK, then run:

```sh
python3 tools/verify_reproducible.py
```

This runs all six simulation test suites and makes two clean builds, comparing
their APK bytes. Unsigned APKs are build artifacts; install the signed release
above or [sign your own build](docs/BUILDING.md).

Reproducibility means the **same source, toolchain, and build settings** produce
the same unsigned APK. Reproducing a signed APK also requires the same signing
key. Release assets include hashes, build metadata, and verification evidence.
The Android/Termux release toolchain and the Linux CI reference toolchain are
documented separately; their APKs are not assumed to match each other.

The Android package remains `org.ostadix.koilife` so Koiros can update the
earlier Koi / Life app with the same signing key and preserve saved controls.
The public version starts at **1.8**, continuing those development versions.

## Credits, privacy, and license

Koiros is released under **GPL-3.0-or-later**. Its corresponding source,
build scripts, and original upstream notices are included. The adapted GoL
component retains its MIT license. [Credits](docs/CREDITS.md) · [License](LICENSE).

The app has no Internet permission, advertisements, accounts, or analytics.
Preferences stay in Android app storage; Android’s own backup settings may
include them in system backup or device transfer. [Privacy details](docs/PRIVACY.md).

Ostadix supported development and numerical/rendering verification. It is an
optional development tool, not an app dependency. [How it works](docs/DYNAMICS.md)
and [releasing an update](docs/RELEASING.md).
