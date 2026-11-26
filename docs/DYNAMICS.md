# How Koiros evolves

Koiros 3.1 has two models, chosen under **Model** in the app.

- **Emergent lattice** (the default) reduces the pond to the equations each dot obeys. Nothing above the dot is scripted: bodies, their textures, spirals, synchrony, fusion and division come out of those equations.
- **Classic forms** keeps the ten Artificial Koi point equations and the shared-world behaviour of Koiros 2.0, described [further down](#classic-forms).

Both live in the same hidden Game of Life world, which is never drawn.

## Emergent lattice

### What a dot is

Each dot *i* has a position in four dimensions (two on the screen and two hidden, see [Hidden dimensions](#hidden-dimensions)) and a velocity. It also has:
- a rhythm θᵢ and a hue *h*ᵢ;
- a heat *r*ᵢ between 0 and 1;
- a spin *s*ᵢ and a swimming heading ψᵢ;
- a depth *d*ᵢ;
- a valence *V*ᵢ, 2 to 6: the most bonds it can hold.

It belongs to a lineage, the group it was born in. The lineage fixes its natural rhythm and what its colour, spin and depth lean back toward. Lengths are in units of the screen's shorter side: the lattice spacing *a* = 0.025, about 32 px on a 1280-px-wide screen. Time is in pond seconds, which run at Drift × 1.5 real seconds.

### Colour is kind

A dot's colour sets its properties, the way an element's place in the table sets an atom's. With *H* the colour as drawn (the hue plus 214°), each property peaks at its own colour:

| Property | Rule | Peaks at |
|---|---|---|
| Bond length | × (1 + 0.085 cos(*H* − 45°)) | orange-yellow: open, airy lattices |
| Breathing | 0.14 + 0.09 cos(*H* − 120°) | green |
| Valence | lineage valence + round(0.9 cos(*H* − 200°)), 2 to 6 | cyan-blue: an extra bond, so crystals; a dot that loses valence lets its longest bonds go |
| Swimming | × (1 + 0.37 cos(*H* − 290°)) | violet |
| Mass | (1 + 0.615 cos(*H* − 220°)) · (1 + 0.12 (*k* − 2)) · (1 − 0.45 *r*), 0.35 to 3, for *k* bonds | blue is heavy, bonds add mass, heat makes a vertex light |

Mass divides every force on a dot, its own drive included.

Colour flows, and the properties flow with it:
- **Relaxes:** toward the dot's remembered colour at 1/16 per pond second. The target is shifted by the hidden Life pattern under the dot (±70°) and by how deep inside a body it sits (±35°), which paints gradients across bodies.
- **Diffuses** along bonds at 0.1.
- **Drifts** with **Color drift**: faster when the Life world is busy. The rate itself eases over 20 seconds, so colours shift steadily.
- **Remembers:** the memory learns what the dot became (time constant 120 pond seconds, not while it is hot) and leans back toward its lineage's own colour (90 pond seconds). Each lineage's colour drifts at its own rate, 0.7 to 1.3 times the drift. Lineages keep distinct colours through fusion and division, so a fused body shows its lineages as colour patches and gradients, and which colours meet keeps changing.

### Bonds: the propensity to make lattices

Two unbonded dots within reach (their exclusion distance plus 0.2*a*), each with a free valence, bond at rate

  *k*ₒₙ = 5 · *R* · ((1 + cos(θᵢ − θⱼ)) / 2)² · (0.3 + 0.7 · (1 + cos(*h*ᵢ − *h*ⱼ)) / 2) · (1 − *r*ᵢ*r*ⱼ)(1 + 0.195 |*r*ᵢ − *r*ⱼ|) · *Q*

so dots bond most readily when they are in step and alike in colour. Hot vertices hardly bond to each other but bond readily to cool ones. *R* is the rewiring rate set by **Shape evolution**, and *Q* = (1 − *c*ᵢ)(1 − *c*ⱼ) applies only between different lineages (see Quorum below). A bond is a damped spring whose rest length breathes with the two rhythms:

  *L* = *a* · ½(*g*ₛᵢ + *g*ₛⱼ) · (1 + ½(*g*ᵦᵢ + *g*ᵦⱼ) · (cos θᵢ + cos θⱼ) / 2),  **F** = *σ* [36 (*r* − *L*) + 2.5 (Δ**v** · **r̂**)] **r̂**

where *g*ₛ and *g*ᵦ are the bond length and breathing set by each dot's colour.

Its strength *σ* grows from 0 to 1 over 0.45 pond seconds, and fades out over 0.35 when it breaks. A bond snaps when stretched past 1.85*a*, and otherwise at hazard rate

  0.35 · *R* · [0.05 + ((1 − cos(θᵢ − θⱼ)) / 2)² + 0.6 *u*² + 2.5 *c̄* + 2 *r*ᵢ*r*ⱼ]

where *u* is the local restlessness of the Life world (the fraction of cells that changed nearby) and *c̄* the pair's mean quorum crowding. Bonds that meet at a dot repel in angle (energy 0.12 *a* cos φ for each pair), so a dot's bonds spread evenly: three open to 120°, four to 90°, six to 60°.

All dots repel inside 0.72*a*. Unbonded dots also keep an exclusion distance that depends on valence, from 1.0*a* for six bonds to 1.6*a* for two, so dots that hold few bonds make open lattices: chains, honeycomb nets, square nets. Between hot vertices it grows by a factor of 1 + 0.514 √(*r*ᵢ*r*ⱼ).

### Heat: crowded vertices glow red

A vertex's pressure is how crowded it is per bond it holds:

  ℓᵢ = *n*ᵢ / (1 + 0.5 *k*ᵢ),  *p*ᵢ = clamp((ℓᵢ − 3.6) / 1.5, 0, 1)

where *n*ᵢ counts the dots within 1.35*a* (bonded or not) and *k*ᵢ is its strength-weighted bond count. Heat follows pressure: it rises toward *p*ᵢ at 0.444 per pond second and cools toward it at 0.25.

In a well-knit lattice each neighbour is a bond, and a vertex stays cool. A crushed core heats, and so does a loose dot in a crowd. Heat is drawn as red and is a vertex's most repulsive state:
- **Repels:** hot vertices keep each other further apart.
- **Gathers less:** a fully hot vertex drifts outward.
- **Frays:** hot–hot bonds fray.
- **Links outward:** hot vertices bond to cool vertices on the outer shell.
- **Lightens:** heat makes a vertex light.

A crowded cluster therefore unfolds into new shapes and cools as it spreads.

### What travels along bonds

Weighted by bond strength and normalised by each dot's total bond weight:

- **Rhythm** (Kuramoto coupling): θ̇ᵢ = ω*ₗ* εᵢ *m* (1 + 0.095 (ρ̃ᵢ − 0.8)) + 2.53 *E*ʷᵢ ⟨sin(θⱼ − θᵢ)⟩ + 0.783 *E*ʷᵢ *K*ᵢ.
  - ω*ₗ*: the lineage's natural frequency.
  - εᵢ: a fixed ±2% per dot.
  - *m*: the mood.
  - ρ̃ᵢ: how deep in a body the dot sits. Deeper dots keep slightly faster time (a pacemaker), so cores lead and phase waves travel to the rim.
  - *K*ᵢ: the pull of the local mean rhythm in the near field.
  - *E*ʷᵢ: the exclusion factor at body scale (below).
- **Colour:** as above, diffusing at 0.1.
- **Spin and depth** diffuse the same way (rates 0.5 and 0.4) and relax toward the lineage's values over 30 and 25 pond seconds.
- **Heading:** ψ̇ᵢ = 2.0 ⟨sin(ψⱼ − ψᵢ)⟩, plus a slow random turn.

### A continuous exclusion principle

The dots are deposited on a coarse grid (cells of 0.0625) at their positions on the screen, and blurred at two scales: *near* (σ = 0.06, a few spacings) and *wide* (σ = 0.30). The near field is also weighted by cos θ and sin θ, so each dot can sense the density of dots *in step with it*, its same-state occupancy:

  *n*ᵢ = (ρ_cos cos θᵢ + ρ_sin sin θᵢ) / ρ_lattice,  *E*ᵢ = clamp(1 − (*n*ᵢ / 1.4)³, −1.5, 1)

ρ_lattice is the density of a relaxed lattice. The same law with the total near density gives *B*ᵢ for all dots, in step or not. Gathering is scaled by these factors, so the pull together fades as packing nears 1.4 times a relaxed lattice and turns into a push apart beyond it. Rhythm coupling follows the same law at body scale (the wide field, critical at 1.15), *E*ʷᵢ: a body in step stays in step, while an over-full same-state crowd falls out of step. Sensed at the dot's own scale, it loosened the tightest cores' rhythm: phase order within bodies fell from about 0.89 to 0.76. While *E*ᵢ < 0, a degeneracy pressure (0.02 |*E*ᵢ|) also pushes the dot down the body-scale gradient of dots in step with it. Many dots can share a place only by occupying different rhythms, and no body can crush itself.

### How a dot moves: swarmalator terms

The gathering follows the swarmalator model of O'Keeffe, Hong and Strogatz (2017), in which oscillators attract more strongly when their phases agree. Each dot relaxes (drag 6 per pond second, divided by its mass, implicitly integrated) toward the velocity

  **v**\* = κᵢ [0.0025 *B*ᵢ ∇ρₙₑₐᵣ + 0.00506 *E*ᵢ ∇(ρ_cos cos θᵢ + ρ_sin sin θᵢ)]  (gather, harder toward dots in step)
     + 0.0939 *s*ᵢ **ẑ** × ∇̂ρₙₑₐᵣ · min(1, |∇ρₙₑₐᵣ| / 2.5)  (swirl around the density it feels)
     + strokes (below)
     + 0.026 *g*ᵥᵢ (cos ψᵢ, sin ψᵢ)  (swim, scaled by its colour's swimming)
     + 0.02 **u**_Life  (the Life current)
     − 0.0072 · (1 − ς) · *climate* · ∇ρ_wide  (spread: bodies keep apart)
     + 0.0465 · ς · ∇̂ρ_wide  (a dot in sparse surroundings climbs toward the nearest crowd)

plus the bond, angle and spacing forces, the frame (below), a little thermal jitter that grows where Life is restless, and a person's touch or welcome.

- **κᵢ** = (0.4 + 0.6 *V*ᵢ / 6)(1 − 0.9 *c*ᵢ)(1 + 1.2 (1 − *k*ᵢ / *V*ᵢ))(1 − 1.08 *r*ᵢ). Dots that hold more bonds gather harder, so dense cores sort inward (differential adhesion). Crowded and hot dots gather less, and dots with unused valence seek company harder.
- **ς** = clamp(1 − ρ_near / 0.5, 0, 1) is how sparse a dot's own neighbourhood is, so shed fragments drift back to bodies instead of being pushed away.
- **Vary:** the climate factor breathes with the coupled chaotic drive.

**Strokes.** Each dot also moves to its own rhythm, relative to the density it feels:
- **In and out:** 0.0278 cos θᵢ along ∇̂ρₙₑₐᵣ.
- **Around:** 0.0033 *s*ᵢ sin θᵢ.
- **Circle:** a small circle along its heading, 0.0087 (cos(θᵢ + ψᵢ), sin(θᵢ + ψᵢ)).

In a body in step, every dot strokes together; where phases lag, the strokes travel through it as waves.

### Hidden dimensions

Each dot has two hidden coordinates.
- **Interactions:** bonds, spacing, bending and exclusion act in all four dimensions. Everything sensed at the scale of crowds acts on the dot's shadow on the screen and is lifted back into four dimensions along the directions the projection mixes: gathering, swirl, spread, the current, the frame, touch and strokes.
- **Room:** the hidden room opens over the first minute: a restoring pull beyond ±0.02, and a hard wall at ±0.05.
- **Projection:** what you see is the shadow:

    *x*_screen = *x* cos *A* + *z*₁ sin *A*,  *y*_screen = *y* cos *B* + *z*₂ sin *B*

  The angles swing slowly: *A* = 0.433 sin(2π*t*/61 + φ_A) and *B* = 0.433 sin(2π*t*/89 + φ_B). The arrangement turns past the screen, and forces between dots come and go.

### An open frame

The screen is a window onto a larger pond.
- **Edge zone:** within 0.14 of an edge, swimmers turn their heading back and are eased inward.
- **Shore:** beyond the edge, a pull back proportional to the distance *b* out, fading as (1 − *b*/0.18)² to nothing at the far side of a 0.18 margin. A dot carried that far has left.
- **Spill:** all of this holds fully while the pond is at its size (the chosen **Lineages** × 270 dots), weakens as it fills past it, and reverses beyond 5% over. An over-full pond overflows at its rim.
- **Arrivals:** groups arrive from beyond the edges, 0.06 per pond second until the pond is 4% over its size, plus 0.1 for each tenth it is short. So groups keep coming and going while the pond stays within a few percent of its size.

Each arriving group slides in through a random point of the edges, a row at a time, placed so that its shadow enters there. Every group differs:
- **Size:** 6 to 76 vertices.
- **Shape:** a thin stream (rows of one or two) or a broad band (three to eight across), entering as a triangular lattice.
- **Density:** 0.75 to 1.95 spacings apart.
- **Speed:** 0.03 to 0.12 per pond second, with an entry drive that fades over 12 pond seconds.
- **Colour:** its lineage's colour ± 40°, and with it its kind.
- **Depth and rhythm:** a random depth, and the lineage's rhythm and spin.

### Quorum

*c*ᵢ = clamp((ρ_wide(**x**ᵢ) − 0.90) / 0.5, 0, 1) is how crowded a dot feels at long range. A normal body's centre reads 0.55 to 0.75; a body several lineages large reads above 1. Crowded dots gather less, their bonds fray faster, and they stop bonding to other lineages, so no body keeps growing.

### Rhythms that wander

Each lineage's natural frequency (3.7 radians per pond second, ±12%) follows an Ornstein–Uhlenbeck walk with a time constant of 15 pond seconds divided by *R*. Which bodies can lock into one rhythm therefore keeps changing.

### What emerges

None of these is written anywhere in the code; they are what the equations do:

- **Shapes.** Open, lacy lattices that take on rings, loops, crescents, curled arms and webs. Crystal cores form where six-bond dots sort inward, and lacy chains at the rim.
- **Unfolding.** A cluster that packs too tight heats, glows red at its most crowded vertices, and unfolds: hot vertices push apart and link outward.
- **Synchrony.** Bodies stroke and pulse in one rhythm, with waves travelling from their cores where they are not quite locked.
- **Fusion and division.** Bodies that fall into step attract and bond. Rhythms that cannot stay locked drift out of step, out-of-step bonds fray, and attraction turns to repulsion; quorum also loosens bodies that grow too large.
- **Colour.** Gradients by depth and by the hidden pattern, and patches where lineages meet. Since colour sets kind, a body's texture shifts as its colours do.
- **Exchange.** Groups drift in, parts of bodies drift out over the edges, and fused bodies carry their lineages' colours into the bodies they divide into.

### What is set, not emergent

- **Lineages:** each lineage's recipe of valences (centre to rim), colour, spin, depth and natural rhythm.
- **Starting arrangement:** random on every start. Groups of random size land at random, well-spread places, as lumpy tilted clouds with part of each group still loose.
- **Arrivals:** the random size, shape, density, speed, colour and point of entry of each arriving group.
- **Drawing:** the depth layers and the tilted-dome projection that give the three-dimensional look are presentation, not dynamics.
- **Events:** fusions and divisions are only *observed*, for the flashes and the log. A fusion is counted when at least four grown bonds join two earlier bodies of 36 or more dots; a division when a body's dots end up in two groups of 36 or more.

### Drawing

- **Bonds:** every line is a bond and ends at two drawn dots. Lines are hairlines about a third of a dot's width. The Life world and the water are never drawn.
- **Exact colour:** each dot is drawn in its own colour, with no palette steps, and every bond is a gradient between its two dots' colours. Colour moves through bodies as a gradient instead of switching.
- **Heat:** heat is mixed in as a vivid red, as strongly as the vertex is hot, and hot vertices glow red. The kinds' own colours are slightly softer near pure red, so vivid red always means heat.
- **The beat:** a dot's size and brightness follow its nearness and its rhythm smoothly, so a body in step swells and brightens as one on every beat, and its dots glow near the top of it.
- **Depth:** each body is drawn as a domed disc tilted toward or away from you about a slowly turning axis. Nearer bodies are larger, and a dome's near side is larger and brighter. Dots are drawn far to near.
- **Parallax:** home-page swipes shift near dots farther than far ones.
- **No tearing:** the projection is a smooth field over the screen, blended from nearby bodies, so bonded neighbours always get nearly the same transform. A snapped bond is not drawn while it fades, and no bond is drawn longer than 2.6 spacings.
- **Steady state:** when the pond moves a dot to another slot, or a group arrives, the drawing state follows each dot, so nothing else twitches.
- **Edges:** dots are drawn up to a bond's length beyond the edge, so bonds run off the screen to real dots.
- **Flashes:** a soft glow marks each fusion and division, with no ring.

Dots, glows and bonds are drawn as three batches of textured quads, every vertex in its dot's colour.

### Cost

Physics advances in fixed steps of 0.025 pond seconds. Frames between steps draw positions interpolated between the last two states, so work follows Drift rather than the display rate: at Drift 0.11 that is about 6.6 steps a second, however many frames are drawn. Because bodies are no longer crushed, each dot has a handful of close neighbours instead of dozens, so a step costs well under half what it did when bodies collapsed into balls.

### Measured

Offscreen Android renders on the author's phone (seven lineages of about 270 dots, 1 Hz Life, Shape evolution 0.10, Colour drift 0.41), 300 pond seconds each, run at Drift 0.5 so a long run fits in a few minutes:

| | Now | Bodies collapsing (before the local exclusion) |
|---|---|---|
| Screen lit at any moment | 34% (every part lit at some point) | 7% |
| Physics step, 1,890 dots | about 3.4 ms | about 8.8 ms |
| Dots holding a bond | 99% | 97% |
| Colour spread within a body | about 35° | about 10° |

Other figures from the same renders:
- **Loose dots:** 5%.
- **Hot vertices** (heat over 0.3): about 2%.
- **Pond size:** 103% of its target on average.
- **Longest drawn bond:** 83 px at full resolution (rest 32 px); no frame with a bond over 3 spacings.
- **Fusions and divisions:** 140 to 190 each per 300 pond seconds, about 5 a minute at the default Drift.
- **Unison:** within bodies of 40 dots or more, the phase order parameter averages about 0.89 (headless, 160 pond seconds; 1 is perfect unison).

Headless runs of 400 pond seconds, with the same equations: the pond stayed within 103–104% of its size, ten groups arrived, about 190 dots left over the edges, and 1,000 to 2,000 dots crossed the screen's edge each way per pond minute.

Fusions and divisions are only *observed*. A fusion is counted when at least four grown bonds join two earlier bodies of 36 or more dots; a division when a body's dots end up in two groups of 36 or more.

### References

The equations draw on well-known models, adapted here:
- **Rhythm coupling:** Kuramoto's phase coupling for rhythm.
- **Gathering:** the swarmalator model of K. P. O'Keeffe, H. Hong and S. H. Strogatz (*Nature Communications*, 2017) for gathering toward dots in step.
- **Chaotic drive:** the Lorenz system (E. N. Lorenz, *Journal of the Atmospheric Sciences*, 1963) for the crowding climate.
- **Hidden world:** Conway's Game of Life (B3/S23) for the hidden world.

## Classic forms

### One shared world

All forms live in a single toroidal Conway Life world of 54 by 120 cells (B3/S23, about 32% initial occupancy), mapped onto the whole screen. Its clock advances from elapsed visible time at the selected 1 Hz or 4 Hz, separately from display-synchronized drawing. Point density adapts to measured drawing cost with gradual recovery.

Each generation is projected onto eight spatial Fourier modes: `(1,0), (0,1), (1,1), (2,1), (1,2), (2,2), (3,1), (1,3)`. Coefficients are measured from the evolving cells and blend over 0.45 seconds. The resulting flow field is one current that every form swims in, so neighbours drift together, and its scalar projection deforms every body that passes through it. Visual deformation follows a further gradual response controlled by Shape evolution.

Every form senses the world where it is: the live fraction and the fraction of changed cells in a disc around it, plus the local current and projection. These local measurements drive its colour travel, form progression, rhythm, and how restless a merged body becomes. Once per generation each form seeds a few new cells at its centre, so its wake becomes part of the world that others later swim through.

### Merging and dividing

Forms that stay in contact for about half a pond second begin to fuse. A bond grows over 2.6 pond seconds: it acts as a spring whose rest length shrinks to zero while damping the pair's relative motion, until both share one centre and one velocity. While bonded, the two converge in visual rhythm (at a bounded rate), rotation, depth, colour and chaotic drive; the higher-numbered form adopts the other's form between visible blends. Convergence is deliberately gentle: in offscreen renders at slow Drift, merged members became identical in all of these within roughly 10 to 17 seconds of fusing, after which they render as one larger body whose growth is gentler than by area. Up to four forms can share a body.

After at least five pond seconds a merged body may divide. The chance rises with Life turbulence around it and when three or more forms share it. One member buds off along a fresh axis while the bond weakens over 2.2 pond seconds; threads of light stretch between them and a soft flash marks the moment they part. Each keeps part of the shared rhythm, form and colour, then slowly returns toward its own colour and depth. A divided pair rests for nine pond seconds before it can fuse again. Pond seconds run at the Drift speed; the convergence of appearance runs in visible seconds at a deliberately gentle rate, so fusion resolves even at the slowest Drift without anything visibly speeding up. Turning Merging off lets bonded forms divide and keeps them apart.

### Colour in the water

Each cell of the world carries a hue vector. Forms paint their hue into the water around them each generation. Colour then spreads along living cells, so it travels with gliders and growing patterns, and fades with a 26-second time constant where nothing lives (three times more slowly where life persists). Unbonded forms drift toward the colour of the water they are in, so hues pass between forms through the world itself. The world is never drawn directly; it is felt through the forms.

### Chaos, palette, and light

The coupled Lorenz drive continues when the world settles. Its nine state variables are integrated together using fixed 1/120-second RK4 steps, with weak directed ring coupling. World activity and density gently change its parameters. A watchdog recovers from nonfinite, runaway, or persistently stationary trajectories. The base equations are from [Lorenz (1963)](https://wind.mit.edu/~emanuel/Lorenz/EdLorenz/Deterministic_63.pdf); the coupling, Life integration, and bonded sharing are additions in this application.

Measured Life activity, population, and spectral patterns combine with Lorenz state and speed to drive colour cycling and form progression. A 48-hue neon palette produces cohesive gradients across each body. Eased transitions follow tours that visit all ten forms. Points, filaments, glow, auras and flashes blend additively, so overlapping and merged bodies brighten rather than cover one another.

### The companion

Everything here reacts to events; nothing polls, senses, or runs while the pond is hidden. Holding a finger still on the home screen draws nearby forms to circle it, and any touch earns a glance. A tap on empty wallpaper (reported by the launcher) brings them to look and adds affection, which makes them come more eagerly and fades over days without play; taps on icons do not count. Fast drags, such as swiping between pages, are ignored. Returning to the home screen after twenty seconds or more earns a welcome: the forms drift toward the lower middle of the screen with a soft glow, more strongly the longer you were away (fully after four hours). A touch or a welcome lifts the pond's own pace by at most 1.8x; responses last rather than pull hard. The time of day and the battery set a mood: calmer and dimmer from 21:00, sleepy below 20% when not charging (which also lowers the frame rate and point count), a little brighter while charging.

With *Know my day* and Android usage access, Koiros reads once per return which apps were in the foreground since you last saw it (at most the last twelve hours, at least fifteen seconds each), takes the dominant vivid hue of each app's icon, and releases up to four of those colours: a soft glow at the edges, and each visible form leans part of the way toward one of them before slowly returning to its own colour over minutes. Only icon hues are remembered. Koiros has no internet permission, so none of this can leave the phone.

### Renewal, pausing, and power

Empty worlds renew after six seconds. Still or period-two patterns get a 24-second observation period; very old runs renew after 12 visible minutes. Renewals can be disabled. Rendering and evolution pause while hidden, without catching up across screen-off time. Full 60 fps follows touches, page swipes, and unlocks; after eight quiet seconds the pond draws at 24 fps (20 fps in Battery Saver or on a low battery), asks the display for a matching rate, and sleeps between drawn frames. The application requests no network or calendar access.
