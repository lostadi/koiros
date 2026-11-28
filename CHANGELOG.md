# Changelog

## 3.4.1 — One gradient on the keyboard

- **The keyboard is one shifting gradient.** With root, Gboard wears an image theme Koiros draws: the pond's main colour flowing into its neighbours across the keys (70 degrees of hue), dimmer toward the bottom so labels stay clear. As the pond's colour glides, the gradient is redrawn around the new hue. Gboard reads its theme when it starts, so it is restarted for a new gradient only while the keyboard is hidden, at most once a minute. Turning **Theme follows the wallpaper** off returns Gboard to its own theme. (`tools/termux/koiros-keyboard.py`; the style sheet in `tools/termux/gboard/` is the one Gboard writes for an image theme.)

## 3.4 — Many colours, gliding

- **Never one hue.** Material You grows a whole theme from one seed, so on its own every colour is a shade of it. The theme now carries the pond's three strongest colours: the first seeds Material You, and, with root, the second and third become the system's secondary and tertiary colours. Each gets a full tonal palette (the same hue at every lightness, as vivid as the screen allows), written as Koiros's own overlay (`tools/termux/koiros-palette.py`). A pond showing fewer colours lends the rest from a triad.
- **Gradual.** Each of the three hues glides toward the colour it follows with a two-minute time constant, and the theme moves in steps of at most 9 degrees, at most every 20 seconds, so colours shift through every hue between instead of jumping.
- Termux's greys take the second and third colours too.

## 3.3 — Your phone takes the pond's colours

- **Theme follows the wallpaper.** Koiros reads which colours the pond is showing (each dot weighted by how brightly it is drawn; for the classic model, each creature by its size) and sets Android's Material You seed to the strongest one, in the lattice's own neon with the Vibrant style. The system bars, Settings, the keyboard and every app that uses Material You colours follow. The theme moves only when the main colour has shifted at least 14 degrees, and at most every 45 seconds, because each change restyles every open app. Previews never touch it.
- **Termux follows too.** Koiros asks Termux to run `~/.termux/koiros-colors.sh` with the pond's three strongest colours: the main one tints the background, text and cursor, and the sixteen terminal colours keep their meanings in the lattice's neon.
- **Your own theme comes back.** Turning off **Theme follows the wallpaper** restores the theme and Termux colours you had before.
- Both need permissions Android grants only from a computer or with root: `WRITE_SECURE_SETTINGS` for the system theme, and Termux's `RUN_COMMAND` (with `allow-external-apps = true`). `ostadix/theme-setup.O` installs the APK, grants both, installs the Termux script and checks the theme engine. Without them Koiros changes nothing.

## 3.2 — Dots meet your apps

- **Collisions with the home screen.** With **Collide with my apps** on, the dots treat the icons, folders, widgets, dock and search bar on your home screen as solid. A dot that reaches one is moved back to its surface and rebounds, and a swimmer heading into it turns away, so bodies flow and break around your apps. Icons are round, their names are pills below them, and widgets keep their rounded shapes; shapes follow the home pages as you scroll, and collisions pause on the lock screen.
- Android does not tell a wallpaper where the launcher draws its apps, so this needs an optional reader, **Koiros app collisions**, turned on in Accessibility settings. It reads the launcher's window only, at most four times a second and only after a change, keeps nothing but shapes and positions, and Koiros still has no internet permission. See [Privacy](docs/PRIVACY.md).
- **Install from the repository.** The signed APK is now also committed under `apk/`, alongside the GitHub release.

## 3.1 — Shapes, heat and an open frame

- **Open shapes, no collapse.** In long runs during this release's development, bodies crushed themselves into a few tight balls: gathering added up through a body into a pressure no spacing force could hold. The continuous exclusion principle now senses crowding at each dot's own scale: the pull together fades as packing nears 1.4 times a relaxed lattice and pushes apart beyond it. Bodies stay open lattices (rings, loops, crescents, curled arms and webs) and light about a third of the screen at any moment, against 7% for the collapsed state.
- **Colour is kind.** A dot's colour sets its bond length, breathing, valence, swimming and mass, replacing 3.0's genes. Each lineage keeps its own colour, which drifts at its own rate, so fused bodies show colour patches and gradients instead of averaging to one hue.
- **Heat.** A vertex crowded per bond it holds heats and glows red. Hot vertices repel each other, gather less, fray their bonds to each other and bond outward to cool vertices, so crowded clusters unfold.
- **Mass** varies with colour, bonds and heat, and divides every force.
- **Hidden dimensions.** Each dot has two hidden coordinates; the screen shows a slowly turning projection, so forces come and go.
- **Strokes.** Dots move to their own rhythm: in and out, around, and in small circles. Bodies in step move as one, with waves travelling from their cores.
- **An open frame.** The screen is a window onto a larger pond. Groups of varying size, shape, density, speed and colour slide in from beyond the edges, parts of bodies drift out, and the pond keeps within a few percent of its size.
- **Smooth colour.** Every dot is drawn in its exact colour, and every bond is a gradient between its two dots. 3.0 shifted each dot's hue with its rhythm and snapped it to 48 steps, so colours flipped on every beat; colour now changes steadily. The beat is a smooth swell instead of a jump between size classes.
- **Touch.** Dragging moves nearby dots with your finger and can fling them; a tap startles them outward and snaps their rhythms into one bright beat.
- **More vibrant.** Brighter bonds, dots, glows and auras; the kinds' reds stay vivid.
- **Lighter drawing.** Dots, glows and bonds take three draw calls instead of a few hundred. Below Android 10, where hardware canvases cannot draw vertex batches, the wallpaper draws on a software canvas.

## 3.0 — The emergent lattice

- New default model, **Emergent lattice**: the pond reduced to the equations each dot obeys. Dots bond up to their valence, more readily when in step and alike in colour; bonds are springs that breathe with the rhythm and snap when stretched or out of step; rhythm, colour, spin, depth and heading spread along bonds; each dot gathers toward dots in step with it (swarmalator coupling), circles the density it feels, swims, drifts in the hidden Life current, and senses crowding (quorum). Bodies, crystal cores and lacy rims, spiral arms, synchrony, fusion and division emerge; nothing above the dot is scripted. See [How Koiros evolves](docs/DYNAMICS.md).
- Every line is a bond between two dots: hairlines a third of a dot's width. No free-floating threads; fusion and division flashes are soft glows without rings.
- Uses the whole screen: bodies spread over it, including the top and bottom.
- Colour runs as a gradient across each body and flows smoothly as the palette drifts.
- A three-dimensional look: near bodies larger and brighter (as with the Koi forms), bodies drawn as tilted domes, home-page parallax.
- A random, irregular starting arrangement on every start; **New pond** gives a fresh one.
- The lattice runs 1.5x faster than the classic forms at the same Drift.
- Rules that evolve: each dot's bond length, breathing and swimming are genes that spread along bonds, are remembered and inherited through fusion and division, drift with each lineage, and lean toward what the hidden world favours.
- Shed fragments seek the nearest crowd and dots with unused valence gather harder; fragments far from bodies are drawn small and dim.
- The three-dimensional projection is a smooth field over the screen, so bonded neighbours never tear apart into long lines; fading bonds are not drawn.
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
