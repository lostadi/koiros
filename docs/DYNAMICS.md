# How Koiros evolves

Koiros 3.0 has two models, chosen under **Model** in the app.

- **Emergent lattice** (the default) reduces the pond to the equations each dot obeys. Nothing above the dot is scripted: bodies, their textures, spirals, synchrony, fusion and division come out of those equations.
- **Classic forms** keeps the ten Artificial Koi point equations and the shared-world behaviour of Koiros 2.0, described [further down](#classic-forms).

Both live in the same hidden Game of Life world, which is never drawn.

## Emergent lattice

### What a dot is

Each dot *i* has a position **x**ᵢ and velocity **v**ᵢ, a rhythm θᵢ, a hue *h*ᵢ, a spin *s*ᵢ, a swimming heading ψᵢ, a depth *d*ᵢ and a valence *V*ᵢ (2 to 6: the most bonds it can hold). It also belongs to a lineage, the group it was born in, which fixes its natural rhythm and what its colour, spin and depth relax back toward. Lengths are in units of the screen's shorter side (the lattice spacing *a* = 0.025, about 32 px on a 1280-px-wide screen); time is pond seconds, which run at Drift × 1.5 real seconds.

### Bonds: the propensity to make lattices

Two unbonded dots within reach (their exclusion distance plus 0.2*a*), each with a free valence, bond at rate

  *k*ₒₙ = 5 · *R* · ((1 + cos(θᵢ − θⱼ)) / 2)² · (0.3 + 0.7 · (1 + cos(*h*ᵢ − *h*ⱼ)) / 2) · *Q*

so dots bond most readily when they are in step and alike in colour. *R* is the rewiring rate set by **Shape evolution**, and *Q* = (1 − *c*ᵢ)(1 − *c*ⱼ) applies only between different lineages (quorum, below). A bond is a damped spring whose rest length breathes with the two rhythms:

  *L* = *a* · ½(*g*ₛᵢ + *g*ₛⱼ) · (1 + ½(*g*ᵦᵢ + *g*ᵦⱼ) · (cos θᵢ + cos θⱼ) / 2),  **F** = *σ* [36 (*r* − *L*) + 2.5 (Δ**v** · **r̂**)] **r̂**

where *g*ₛ and *g*ᵦ are each dot's bond-length and breathing genes (see [Rules that evolve](#rules-that-evolve)).

Its strength *σ* grows from 0 to 1 over 0.45 pond seconds, and fades out over 0.35 when it breaks. A bond snaps when stretched past 1.85*a*, and otherwise at hazard rate

  0.35 · *R* · [0.05 + ((1 − cos(θᵢ − θⱼ)) / 2)² + 0.6 *u*² + 2.5 *c̄*]

where *u* is the local restlessness of the Life world (the fraction of cells that changed nearby) and *c̄* the pair's mean quorum crowding. Bonds that meet at a dot repel in angle (energy 0.12 *a* cos φ for each pair), so a dot's bonds spread evenly: three open to 120°, four to 90°, six to 60°.

All dots repel inside 0.72*a*. Unbonded dots also keep an exclusion distance that depends on valence, from 1.0*a* for six bonds to 1.6*a* for two, so dots that hold few bonds make open lattices: chains, honeycomb nets, square nets.

### What travels along bonds

Weighted by bond strength and normalised by each dot's total bond weight:

- Rhythm (Kuramoto coupling): θ̇ᵢ = ω*ₗ* εᵢ *m* + 2.4 ⟨sin(θⱼ − θᵢ)⟩ + 0.9 *K*ᵢ, where ω*ₗ* is the lineage's natural frequency, εᵢ a fixed ±2% per dot, *m* the mood, and *K*ᵢ the pull of the local mean rhythm in the near field (below).
- Colour: *ḣ*ᵢ = 0.7 ⟨arc(*h*ⱼ − *h*ᵢ)⟩ + arc(*h*ᵢ⁰ − *h*ᵢ) / 40, relaxing toward the lineage colour *h*ᵢ⁰; the hidden water's colour is also absorbed where the dot swims.
- Spin and depth diffuse the same way (rates 0.5 and 0.4) and relax toward the lineage's values over 30 and 25 pond seconds.
- Heading: ψ̇ᵢ = 2.0 ⟨sin(ψⱼ − ψᵢ)⟩ plus a slow random turn.

### How a dot moves: swarmalator terms

The dots are deposited on a coarse grid (cells of 0.0625) and blurred at two scales: *near* (σ = 0.06) and *wide* (σ = 0.30). The near field is also weighted by cos θ and sin θ, so each dot can sense the density of dots *in step with it*. This follows the swarmalator model of O'Keeffe, Hong and Strogatz (2017), in which oscillators attract more strongly when their phases agree. The walls mirror the field.

Each dot relaxes (drag 6 per pond second, implicitly integrated) toward the velocity

  **v**\* = κᵢ [0.0025 ∇ρₙₑₐᵣ + 0.0035 ∇(ρ_cos cos θᵢ + ρ_sin sin θᵢ)]  (gather, harder toward dots in step)
     + 0.065 *s*ᵢ **ẑ** × ∇̂ρₙₑₐᵣ · min(1, |∇ρₙₑₐᵣ| / 2.5)  (swirl around the density it feels)
     + 0.026 *g*ᵥᵢ (cos ψᵢ, sin ψᵢ)  (swim, scaled by the swimming gene)
     + 0.02 **u**_Life  (the Life current)
     − 0.010 · (1 − ς) · *climate* · ∇ρ_wide  (spread: bodies keep apart)
     + 0.03 · ς · ∇̂ρ_wide  (a dot in sparse surroundings climbs toward the nearest crowd)

plus the bond, angle and spacing forces, walls (dots within 0.14 of a wall are eased away and turn their heading back), a little thermal jitter that grows where Life is restless, and a person's touch or welcome. κᵢ = (0.4 + 0.6 *V*ᵢ / 6)(1 − 0.9 *c*ᵢ)(1 + 1.2 (1 − *n*ᵢ / *V*ᵢ)): dots that hold more bonds gather harder, so dense cores sort inward (differential adhesion); crowded dots gather less; and dots with unused valence (*n*ᵢ bonds of *V*ᵢ) seek company harder. ς = clamp(1 − ρ_near / 0.5, 0, 1) is how sparse a dot's own neighbourhood is, so shed fragments drift back to bodies instead of being pushed away. With **Vary**, the climate factor breathes with the coupled chaotic drive.

### Quorum

*c*ᵢ = clamp((ρ_wide(**x**ᵢ) − 0.90) / 0.5, 0, 1) is how crowded a dot feels at long range. A normal body's centre reads 0.55 to 0.75; a body several lineages large reads above 1. Crowded dots gather less, their bonds fray faster, and they stop bonding to other lineages, so no body keeps growing.

### Rhythms that wander

Each lineage's natural frequency (3.7 radians per pond second, ±12%) follows an Ornstein–Uhlenbeck walk with a time constant of 15 pond seconds divided by *R*. Which bodies can lock into one rhythm therefore keeps changing.

### Rules that evolve

Each dot carries three genes: its preferred bond length *g*ₛ (a multiplier of *a*, 0.8 to 1.35), its breathing strength *g*ᵦ (0.04 to 0.3) and its swimming drive *g*ᵥ (0.5 to 1.8). Each gene has an expressed value, used by the equations, and a remembered one.

- **Shared:** expressed genes diffuse along bonds (rate 0.4, weighted like rhythm). A fused body blends its genomes.
- **Expressed toward what the surroundings favour:** each gene relaxes, at 0.1 × *R* per pond second, toward its remembered value shifted by the hidden world. The local Life pattern *p* ∈ [−1, 1] shortens the favoured bond length by 0.08 *p* and strengthens swimming by 0.3 *p*. Restless water strengthens breathing by 0.12 (*u* − 0.3).
- **Remembered and inherited:** the remembered value learns what the dot has become (time constant 50 pond seconds). It also relaxes toward its lineage's genome and mutates slightly, more in restless water. A divided body therefore carries what it became while fused.
- **Lineages drift:** each lineage's genome follows its own Ornstein–Uhlenbeck walk around where it began (bond length ±0.15, breathing ±0.07, swimming ±0.35, time constant 45 pond seconds / *R*). Lineages never converge, and which genomes meet keeps changing.
- **Spin learns:** the spin a dot remembers follows the spin its body actually has, so rotation is inherited through fusion and division.

Valence stays structural: lacy, low-valence rims are where bodies tear and divide, and letting valence evolve as well made oversized bodies common in 30-minute runs.

### What emerges

None of these is written anywhere in the code; they are what the equations do:

- **Bodies.** Connected groups of bonds, each with its own texture: crystal cores (six-bond dots sort inward), open nets, and lacy chains at the rim.
- **Spirals.** Spiral arms form from swirl that is strongest at a body's edge, together with rewiring.
- **Synchrony.** Bodies pulse in one rhythm, with visible waves where they are not quite locked.
- **Fusion.** Bodies that fall into step attract and bond, and colour, spin and depth average along the new bonds.
- **Division.** Rhythms that cannot stay locked drift out of step: out-of-step bonds fray, and attraction between them turns to repulsion. Quorum also loosens bodies that grow too large.
- **Exchange.** Dots exchanged in a fusion or division carry their lineage's colour memory into their new body.
- **Spread.** Bodies use the whole screen.

### What is set, not emergent

- Each lineage's recipe of valences (centre to rim), colour, spin, depth, natural rhythm and starting genes.
- The starting arrangement, which is random on every start: groups of random size land at random, well-spread places, as lumpy tilted clouds with part of each group still loose.
- The drawing. The colour gradient across each body, the depth classes, and the tilted-dome projection that gives the three-dimensional look are presentation, not dynamics.
- Fusions and divisions are only *observed*, for the flashes and the log. A fusion is counted when at least four grown bonds join two earlier bodies of 36 or more dots; a division when a body's dots end up in two groups of 36 or more.

### Drawing

Every line is a bond and ends at two drawn dots. Lines are hairlines about a third of a dot's width. There are no free-floating threads, and the Life world and the water are never drawn.

- **Glow:** dots in the bright half of their rhythm draw one size class larger and carry a faint glow.
- **Colour:** colour runs as a gradient across each body, diagonally and from core to rim, about 130° in all. Each dot crosses into the next of the 48 colour steps at its own threshold, so palette drift flows through a body rather than switching it at once.
- **Depth:** each body is drawn as a domed disc tilted toward or away from you about a slowly turning axis. Nearer bodies are larger, and a dome's near side is larger and brighter.
- **Parallax:** home-page swipes shift near dots farther than far ones.
- **Projection without tearing:** the projection is a smooth field over the screen, blended from nearby bodies, so bonded neighbours always get nearly the same transform. A snapped bond is not drawn while it fades, and no bond is drawn longer than 2.6 spacings.
- **Debris recedes:** fragments far from any body draw in the smallest, dimmest class.
- **Flashes:** a soft glow marks each fusion and division, with no ring.

### Cost

Physics advances in fixed steps of 0.025 pond seconds. Frames between steps draw positions interpolated between the last two states, so work follows Drift rather than the display rate: at Drift 0.11 that is about 6.6 steps a second, however many frames are drawn.

### Measured

On screen during a real home-screen visit (the build just before the evolving rules; drawing cost is unchanged): 3.6–5.1 ms of main-thread work per frame while touched at 60 fps (about a quarter of one CPU core) and 3.6–4.5 ms per frame at the idle 24 fps (about a tenth of a core), close to the classic forms' 2.0 figures.


Offscreen Android renders on the person's phone at their settings (Drift 0.11, Shape evolution 0.10, Colour drift 0.41, seven lineages of about 270 dots, 1 Hz Life), 4 to 5 minutes each:

| | |
|---|---|
| Screen lit at any moment | 30–34% (91% lit at some point) |
| Fusions / divisions | 10–11 / 5–9 per 4–5 minutes |
| Dots holding a bond | 98–99% |
| Longest drawn bond | 83 px (rest 32 px); no frame with a bond over 3 spacings |
| Dots within 0.3 of a tap | +40 to +90 after 6 seconds |
| Simulation + geometry per drawn frame | about 1.4 + 0.9 ms |

Headless 30-minute runs on four seeds, with the same equations:
- **Events:** mostly 8 to 40 fusions and divisions per 5 minutes after the opening minutes.
- **Oversized bodies:** a body over three lineages' size is present 0.5–7% of the time.
- **Loose dots:** 7–16% of dots sit outside any body of 14 or more, as drifting fragments drawn small and dim.

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
