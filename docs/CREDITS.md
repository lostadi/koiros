# Credits and provenance

**Koiros is a computational live wallpaper created by Lee Daghlar Ostadi (Lee Ostadi).** Lee's
original integration brings together cellular automata, coupled chaotic
systems, evolving forms, color, currents, collisions, and touch ripples.
Copyright (C) 2026 Lee Daghlar Ostadi for original Koiros additions and modifications.

The project began with two Android applications: **Artificial Koi** and
**GoL Screensaver**. Koiros adapts their openly licensed source into one native
Android application. Their original authors retain credit for their work.
The original APK binaries are not embedded in Koiros.

## Artificial Koi

- **Author:** Studio Coldstream / Mathew Molt (`mathewmolt`), with the
  acknowledgments retained in [the original credits](../LICENSES/Artificial-Koi-CREDITS.md).
- **Source:** [Artificial Koi on Codeberg](https://codeberg.org/mathewmolt/ArtificialKoi).
- **Source revision:** `8834094e2357e5482e1495b80516f12360ff72dc`.
- **License:** GNU GPL version 3 or later; [unmodified license text](../LICENSES/GPL-3.0-or-later.txt).
- **Contribution used:** the ten parametric creature formulas from
  `LiveWallpaperService.kt` and `lib/koi_algorithms.dart`, adapted into
  [`KoiForms.java`](../app/src/org/ostadix/koilife/KoiForms.java).

The forms are Koi, Jellyfish, Manta, Coral, Helix, Breathe, Bloom, Trance,
Koi II, and Nebula. Artificial Koi credits digital artist
[@yuruyurau](https://x.com/yuruyurau) as the inspiration for this style of
point-based algorithmic creature art, and describes its formulas as its own
reinterpretations. Koiros retains that distinction.

Artificial Koi's original icon was made by Studio Coldstream from its creature
art with an AI image generator. That attribution is preserved in the original
credits; those upstream raster icon files are not included in this app.

## GoL Screensaver

- **Author:** Harold Martin (`hbmartin`).
- **Original copyright notice:** Copyright (c) 2025 Harold "Roldy" (Martin) Martin.
- **Source:** [GoL Screensaver on GitHub](https://github.com/hbmartin/onyx-boox-screensaver-gol).
- **Source revision:** `c8ee139f78735512db3fb6638825685db0fc0a7a`.
- **License:** MIT; [unmodified copyright and permission notice](../LICENSES/GoL-Screensaver-MIT.txt).
- **Contribution used:** wrapped-neighbor and Conway update behavior from
  `GolController.kt`, adapted in
  [`LifeField.java`](../app/src/org/ostadix/koilife/LifeField.java).

Koiros extends that behavior with nine independently seeded fields,
elapsed-time simulation clocks, spatial Fourier projections, smooth visual
responses, and optional renewal of settled patterns.

## Koiros modifications

The source was adapted and integrated during November 2026. Koiros adds the
combined renderer and controls, gradual form transitions and spatial hue
gradients, coupled Lorenz dynamics, shared currents, variable populations,
soft collisions, encounter color exchange, touch ripples, adaptive rendering,
tests, and a portable build and release workflow. These changes are attributed
to Lee Daghlar Ostadi; the upstream contributions listed above keep their original
attribution and license notices.

Ostadix was used for development orchestration and numerical/rendering checks.
It is not required to install or run the wallpaper.

## License of the combined application

Koiros is free software under **GPL-3.0-or-later**: you may redistribute and/or
modify it under the GNU General Public License as published by the Free
Software Foundation, either version 3 or, at your option, any later version.
It comes **without any warranty**, including implied warranties of
merchantability or fitness for a particular purpose. See [LICENSE](../LICENSE).

The MIT notice for the adapted GoL component remains intact. Releases include
the corresponding source and build instructions alongside the APK. The
in-app **About Koiros → Licenses** screen contains these credits and both
license texts.
