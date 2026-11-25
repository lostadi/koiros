# Changelog

## 3.0 — The emergent lattice

- New default model, **Emergent lattice**: the pond reduced to the equations each dot obeys. Dots bond up to their valence, more readily when in step and alike in colour; bonds are springs that breathe with the rhythm and snap when stretched or out of step; rhythm, colour, spin, depth and heading spread along bonds; each dot gathers toward dots in step with it (swarmalator coupling), circles the density it feels, swims, drifts in the hidden Life current, and senses crowding (quorum). Bodies, crystal cores and lacy rims, spiral arms, synchrony, fusion and division emerge; nothing above the dot is scripted. See [How Koiros evolves](docs/DYNAMICS.md).
- Every line is a bond between two dots: hairlines a third of a dot's width. No free-floating threads; fusion and division flashes are soft glows without rings.
- Uses the whole screen: bodies spread over it, including the top and bottom.
- Colour runs as a gradient across each body and flows smoothly as the palette drifts.
- A three-dimensional look: near bodies larger and brighter (as with the Koi forms), bodies drawn as tilted domes, home-page parallax.
- A random, irregular starting arrangement on every start; **New pond** gives a fresh one.
- The lattice runs 1.5x faster than the classic forms at the same Drift.
- Physics advances in fixed pond-time steps and drawing interpolates between them, so work follows Drift rather than the display rate.
- **Classic forms** remains available under **Model**. With the lattice, **Merging** off makes dots bond only within their own lineage, **Collisions** off lets lineages pass through each other, and **Vary** lets crowding rise and fall with the chaotic drive.

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
