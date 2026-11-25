# How Koiros evolves

## One shared world

All forms live in a single toroidal Conway Life world of 54 by 120 cells (B3/S23, about 32% initial occupancy), mapped onto the whole screen. Its clock advances from elapsed visible time at the selected 1 Hz or 4 Hz, separately from display-synchronized drawing. Point density adapts to measured drawing cost with gradual recovery.

Each generation is projected onto eight spatial Fourier modes: `(1,0), (0,1), (1,1), (2,1), (1,2), (2,2), (3,1), (1,3)`. Coefficients are measured from the evolving cells and blend over 0.45 seconds. The resulting flow field is one current that every form swims in, so neighbours drift together, and its scalar projection deforms every body that passes through it. Visual deformation follows a further gradual response controlled by Shape evolution.

Every form senses the world where it is: the live fraction and the fraction of changed cells in a disc around it, plus the local current and projection. These local measurements drive its colour travel, form progression, rhythm, and how restless a merged body becomes. Once per generation each form seeds a few new cells at its centre, so its wake becomes part of the world that others later swim through.

## Merging and dividing

Forms that stay in contact for about half a pond second begin to fuse. A bond grows over 2.6 pond seconds: it acts as a spring whose rest length shrinks to zero while damping the pair's relative motion, until both share one centre and one velocity. While bonded, the two converge in visual rhythm (at a bounded rate), rotation, depth, colour and chaotic drive; the higher-numbered form adopts the other's form between visible blends. Convergence is deliberately gentle: in offscreen renders at slow Drift, merged members became identical in all of these within roughly 10 to 17 seconds of fusing, after which they render as one larger body whose growth is gentler than by area. Up to four forms can share a body.

After at least five pond seconds a merged body may divide. The chance rises with Life turbulence around it and when three or more forms share it. One member buds off along a fresh axis while the bond weakens over 2.2 pond seconds; threads of light stretch between them and a soft flash marks the moment they part. Each keeps part of the shared rhythm, form and colour, then slowly returns toward its own colour and depth. A divided pair rests for nine pond seconds before it can fuse again. Pond seconds run at the Drift speed; the convergence of appearance runs in visible seconds at a deliberately gentle rate, so fusion resolves even at the slowest Drift without anything visibly speeding up. Turning Merging off lets bonded forms divide and keeps them apart.

## Colour in the water

Each cell of the world carries a hue vector. Forms paint their hue into the water around them each generation. Colour then spreads along living cells, so it travels with gliders and growing patterns, and fades with a 26-second time constant where nothing lives (three times more slowly where life persists). Unbonded forms drift toward the colour of the water they are in, so hues pass between forms through the world itself. The world is never drawn directly; it is felt through the forms.

## Chaos, palette, and light

The coupled Lorenz drive continues when the world settles. Its nine state variables are integrated together using fixed 1/120-second RK4 steps, with weak directed ring coupling. World activity and density gently change its parameters. A watchdog recovers from nonfinite, runaway, or persistently stationary trajectories. The base equations are from [Lorenz (1963)](https://wind.mit.edu/~emanuel/Lorenz/EdLorenz/Deterministic_63.pdf); the coupling, Life integration, and bonded sharing are additions in this application.

Measured Life activity, population, and spectral patterns combine with Lorenz state and speed to drive colour cycling and form progression. A 48-hue neon palette produces cohesive gradients across each body. Eased transitions follow tours that visit all ten forms. Points, filaments, glow, auras and flashes blend additively, so overlapping and merged bodies brighten rather than cover one another.

## The companion

Everything here reacts to events; nothing polls, senses, or runs while the pond is hidden. Holding a finger still on the home screen draws nearby forms to circle it, and any touch earns a glance. A tap on empty wallpaper (reported by the launcher) brings them to look and adds affection, which makes them come more eagerly and fades over days without play; taps on icons do not count. Fast drags, such as swiping between pages, are ignored. Returning to the home screen after twenty seconds or more earns a welcome: the forms drift toward the lower middle of the screen with a soft glow, more strongly the longer you were away (fully after four hours). A touch or a welcome lifts the pond's own pace by at most 1.8x; responses last rather than pull hard. The time of day and the battery set a mood: calmer and dimmer from 21:00, sleepy below 20% when not charging (which also lowers the frame rate and point count), a little brighter while charging.

With *Know my day* and Android usage access, Koiros reads once per return which apps were in the foreground since you last saw it (at most the last twelve hours, at least fifteen seconds each), takes the dominant vivid hue of each app's icon, and releases up to four of those colours: a soft glow at the edges, and each visible form leans part of the way toward one of them before slowly returning to its own colour over minutes. Only icon hues are remembered. Koiros has no internet permission, so none of this can leave the phone.

## Renewal, pausing, and power

Empty worlds renew after six seconds. Still or period-two patterns get a 24-second observation period; very old runs renew after 12 visible minutes. Renewals can be disabled. Rendering and evolution pause while hidden, without catching up across screen-off time. Full 60 fps follows touches, page swipes, and unlocks; after eight quiet seconds the pond draws at 24 fps (20 fps in Battery Saver or on a low battery), asks the display for a matching rate, and sleeps between drawn frames. The application requests no network or calendar access.
