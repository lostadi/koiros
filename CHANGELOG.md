# Changelog

## 2.0 — One shared world

- All forms now live in one shared Game of Life world instead of nine private boards. They sense it locally, seed life in their wake, and swim in one measured current.
- Forms can merge: contact grows a bond that pulls them into one larger body with a single rhythm, rotation, depth, colour, chaotic drive, and form. Merged bodies divide again along a new axis, keeping part of what they shared. Up to four forms per body; a new **Merging** switch (on by default).
- Colour now lives in the water: forms paint their hue into the (unseen) world, Life carries it, and forms drift toward the colour around them.
- A companion that reacts only to events: forms come to look when you tap, welcome you back to the home screen, follow the time of day and the battery, and — with **Know my day** and usage access — take on the colours of the apps you spent time in. No internet permission; nothing leaves the phone.
- More vibrant light: additive blending, soft glow sparks, auras that match each body's actual hue, threads of light between fusing or dividing bodies, and flashes at fusion and division.
- Depth layers and home-page drift from the device build are now part of Koiros.
- Lower power when idle: 24 fps after eight seconds without touch (20 fps in Battery Saver or on a low battery, with fewer points), a matching display-rate request, and no wake-ups between drawn frames.
- Saved controls are preserved; new settings default on.

## 1.8 — Koiros public release

- Named and credited Koiros as Lee Ostadi’s evolving computational wallpaper.
- Preserved the current simulation, ten forms, slow controls, population changes, collisions, gradual hues, and tap ripples.
- Added portable SDK build scripts, clean-build byte comparisons, GitHub CI, installation instructions, privacy notes, and complete upstream credits.
- Kept package identity and release signing continuity for upgrades from Koi / Life 1.7.

## Earlier development versions

1.7 added touch ripples and encounter color exchange. 1.6 refined slow-motion
controls and bounded rendering cost. Earlier versions integrated Artificial
Koi forms with Game of Life, frequency projections, and coupled chaos.
