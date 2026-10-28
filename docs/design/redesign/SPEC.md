# KINETICKK redesign — implementation spec

This spec turns the design canvas into work on the real codebase. Read it top to
bottom once, then use it as a reference per screen.

- **Visual truth:** `screenshots/` (rendered boards) and `boards/*.dc.html` + `boards/kk.css`
  (exact numbers: sizes, colors, timings, easing, layout). The `.dc.html` files are HTML with
  `{{holes}}`, `<sc-for>`, `<sc-if>` and a `renderVals()` script that computes data; read the
  script to understand states. `kk.css` holds every shared class and keyframe.
- **Game truth:** the Kotlin code. Names, numbers, counts, stats, choices, available actions,
  settings rows and content always come from the game. Text and numbers on the boards are
  placeholders unless this spec says otherwise.
- **Machine-readable:** `tokens.json` (colors, type roles, easing, durations, palettes, tiers,
  color-vision palettes), `icons.json` (every icon as SVG path data on a 24 grid).
- **Fonts:** `fonts/` (static, subset Latin + Latin-Ext + Cyrillic TTFs) and `fonts/licenses/`.

---

## 1. Scope

**In scope:** every pixel the player sees — HUD, world rendering style (enemies, Core,
singularity, projectiles, pickups, damage numbers, kill/hit effects), in-run overlays (rewards,
pause, points of interest, terminal/report), all menus (Home, Armory, Core Matrix, Rebirth, Codex,
Settings, profile-unavailable), phone layouts, typography, color, iconography, motion.

**Also in scope (the only non-visual additions):**
1. **Color vision** setting (Default, Protan, Deutan, Tritan, Mono) — section 9.
2. Passing already-existing data to renderers that need it (e.g. rebirth tier to the Rebirth
   screen and arena, current level to the level-badge tier, selected Home item to the palette).

**Out of scope — do not change:** game rules, balance, spawn logic, physics, input mapping and
key bindings, state machines/reducers (beyond plumbing above), save format (except section 9's
codec-safe field), networking/release tooling, audio. Do not add new screens, new flows or new
settings rows other than Color vision. If a board shows something the game has no data or flow
for (e.g. a Title/attract screen, Quit item, Controls/Accessibility tabs, six weapon slots when
the run has one active weapon, "Restart run" in pause), **do not fake it**: apply the visual
language to what exists and list the gap in the PR description under "Design items not
implemented (no game support)".

**Adaptation rule:** when the game has an element the design does not show, style it with the
closest design component (same family, same tokens). When the design and game disagree on
structure, keep the game's structure and apply the design's look.

---

## 2. Hard rules (from the owner's review — non-negotiable, everywhere incl. phone layouts)

1. **No index numbers** like `01`, `02`, `§01` anywhere, especially before labels.
2. **No middle dots** `·` as separators. Use layout (gaps), line breaks, or the thin sheared
   separator bar (2 px × 0.85 em, 40% opacity, −12° shear) if a separator is unavoidable.
3. **No arrow symbols or arrow-shaped icons, and no emoji** on any component: `→ ← ↑ ↓ ↵ › ‹ ▶ ◀ ◇`
   and any glyph/path that draws an arrowhead. Replace "A → B" comparisons with labelled
   values ("Now +48%" / "Next +72%") or two values where the second is in the accent color.
   `drawKineticArrow` and any arrow glyph must be removed or replaced by a non-arrow mark.
   The dart-shaped enemy silhouette is replaced by a pentagon (`M12 3 21 10 17.5 21h-11L3 10Z`)
   if the game has an arrow-like enemy shape.
4. **No hint / instruction text in the UI** ("Press…", "Tap to…", "Hold Enter…", key legends,
   "Esc Back", footers explaining rules). Buttons may exist; hints may not. Any explanation that
   still matters goes behind an **(!) info button** with a tooltip.
   Amendment (0.5.0): action slots (Active 1–3, Apex) show their bound key as a mono key cap while
   the keyboard is in use. No verbs, no other key legends; Dash and Brake keep icon + label only.
5. **Levels read "Lvl X"** everywhere (player level, weapon level, mastery milestones).
   Russian uses the game's existing short form for level followed by the number (no "L7",
   no "LV 14", no "Lv.").
6. **Reduced HUD information**: timer only at top center (no track/pips under it); integrity
   shows one number (no "/max"); speed gauge shows number + tick ladder + overdrive bar (no
   state words like "Cruise ×1.8"); no text warnings — states are shown with color, pulse,
   stripes and edge glows.
7. **Don't explain boss mechanics** (no phase labels / "phase I/II/III" tags on the Architect).
8. **Rarity must read through effects, not only color** (see Card component).
9. **Each Home menu item has its own palette** (7 designed, apply to the items the game has).
10. **Each Rebirth tier has its own theme**; tier 10 is black-and-white with a black hole.

Grep checks the PR must pass (in `*.kt` and string resources, excluding tests and license
texts): `·`, `→`, `←`, `↑`, `↓`, `↵`, `›`, `‹`, `▶`, `◀`, `◇`, `§`; level labels matching
`\bL\d+\b`, `LV `, `Lv.`; string literals that are only a zero-padded index (`"0" + (i + 1)`,
`padStart(2, '0')` equivalents).

---

## 3. Tokens (put in `foundation:design`)

All values come from `tokens.json` / `kk.css :root`. Draft Kotlin (adapt names to the module's
conventions; keep SPDX headers):

```kotlin
object Kk {
    // ground & structure
    val Ink = Color(0xFF0A0B0A); val Ink1 = Color(0xFF0F110F); val Ink2 = Color(0xFF151815)
    val Ink3 = Color(0xFF1D211D); val Ink4 = Color(0xFF272C27)
    val Line = Color(0xFF2A2F2A); val Line2 = Color(0xFF3C423C)
    val Bone = Color(0xFFF1F0E8); val Bone2 = Color(0xFFD6D5CC)
    val Mute = Color(0xFFA3A298); val Mute2 = Color(0xFF86857B)
    // roles (these five are swapped by Color vision, see §9)
    val Volt = Color(0xFFD8FF3E)    // you / confirm / player
    val Hazard = Color(0xFFFF3B6B)  // threat / enemy / danger
    val Heat = Color(0xFFFF8A1F)    // dash heat
    val Shield = Color(0xFF45E0FF)  // shield / barrier
    val Pol = Color(0xFFA98BFF)     // polarity / cursor
    val Volt2 = Color(0xFFBDE82A); val VoltInk = Color(0xFF141A00)
    val Hazard2 = Color(0xFFD61F4F); val HazardInk = Color(0xFF2A0510)
    // rarity
    val RCommon = Color(0xFFC9C8BE); val RUncommon = Color(0xFF5CF2A6); val RRare = Color(0xFF4DA6FF)
    val REpic = Color(0xFFB777FF); val RLegend = Color(0xFFFFB627)
    // relic aspects
    val AVector = Color(0xFFD8FF3E); val AGravitic = Color(0xFF9B7BFF); val AIon = Color(0xFF45E0FF)
    val ARift = Color(0xFFFF4FA3); val APrism = Color(0xFFE4F1FF); val AEntropy = Color(0xFFFF6A2B)
    val ASovereign = Color(0xFFFFC93C)
}
object KkEase {
    val Pull = CubicBezierEasing(.18f, 1.45f, .4f, 1f)  // selection slabs, cards, slams (overshoot)
    val Snap = CubicBezierEasing(.75f, 0f, .2f, 1f)     // shutters, wipes, tab swaps
    val Out  = CubicBezierEasing(.16f, 1f, .3f, 1f)     // entrances, number drift, meters
    val In   = CubicBezierEasing(.6f, 0f, .9f, .4f)     // exits, warnings closing in
}
object KkTime { const val Impact = 16; const val Snap = 90; const val Pull = 220
    const val Shutter = 420; const val Drift = 600; const val AmbientMin = 24_000 }
object KkShape { const val Shear = -12f; const val Slash = -27.5f }
```

**Migration of legacy tokens** (`DesignTokens.kt`): first repoint the old names to the new
palette so untouched code already looks right, then migrate call sites to role names while
redoing each screen, then delete legacy names that are unused. Semantics to fix while migrating
(the old code uses some colors for different meanings): shield is **Shield (cyan)**, polarity is
**Pol (violet)**, player/confirm is **Volt**, danger is **Hazard**, heat is **Heat**. Old
`SpaceBlack`→Ink, `OverlayPanel`→Ink2, `GridBlue`→Ink3, `White`→Bone, `Muted`→Mute,
`DarkLine`→Line, `Red`→Hazard, `Orange`→Heat, `Cyan`→Shield, `Violet`→Pol, `Gold`→RLegend,
`Blue`→RRare, `KineticAccent`→Volt. `Magenta`/`Acid`: inspect each use and map by meaning
(rift aspect / uncommon rarity / home palette etc.).

**Screen share of color:** ink 70 / bone 20 / volt 8 / hazard 2. Volt is precious: it marks
"you" and "confirm". Hazard only marks danger.

---

## 4. Typography & fonts

Files in `fonts/` (OFL 1.1, instanced + subset to Latin, Latin-Ext-A, Cyrillic, punctuation,
№, −, ×, ₽, €; OpenType layout features kept, incl. `tnum`):

| file | family / weight | role |
|---|---|---|
| kk_wide_black.ttf | Unbounded 900 | `wide`: clock, titles, big numerals, speed |
| kk_wide_bold.ttf | Unbounded 700 | wide secondary (small wide labels) |
| kk_cond_black_italic.ttf | Sofia Sans Extra Condensed 900 italic | `cond`: menus, buttons, headings, damage numbers |
| kk_cond_black.ttf | Sofia Sans Extra Condensed 900 | cond upright heavy |
| kk_cond_extrabold.ttf | Sofia Sans Extra Condensed 800 | `label`: labels, tabs, tags (+9% tracking) |
| kk_body_regular/medium/bold.ttf | Sofia Sans Semi Condensed 400/500/700 | `body`: descriptions, tooltips |
| kk_mono_medium/bold.ttf | Martian Mono 500/700 | `mono`: readouts, small data (+5% tracking) |

Install: copy the TTFs into `foundation/design/src/commonMain/composeResources/font/`, the
license texts into `.../composeResources/files/` (same pattern as `onest-OFL.txt`), and add the
four families to `docs/project/THIRD_PARTY_NOTICES.md` and `docs/project/ASSET_PROVENANCE.md`
(source: github.com/google/fonts `ofl/unbounded`, `ofl/sofiasansextracondensed`,
`ofl/sofiasanssemicondensed`, `ofl/martianmono`; modified: static instances + subset via
fontTools). Remove Onest/Oswald and their notices only if nothing references them afterwards.

Extend `InterfaceTypography` to roles `wide`, `cond`, `label`, `body`, `mono` (families built
from the files above with correct weight/style). Text rules:
- `wide`: uppercase, tracking 0, line height 0.9.
- `cond`: italic 900, uppercase, line height 0.86.
- `label`: 800 upright, uppercase, tracking +0.09 em, 15 px default.
- `body`: 400/500, sentence case, 16 px, line height 1.4.
- `mono`: 500, uppercase, tracking +0.05 em, 11 px default (min 14 px on phones for body text).
- Every number that changes (timer, integrity, speed, matter, stats) uses tabular figures:
  `fontFeatureSettings = "tnum"`.
- Unbounded has very wide glyphs: never use negative tracking with it; check Russian strings
  fit (they are ~20–30% longer) — prefer `cond` for long labels.

---

## 5. Component library (build in `foundation:design`)

Build each as a DrawScope helper (for Canvas-rendered screens) **and** a thin Modifier/composable
wrapper where Compose-layout screens need it (e.g. `Modifier.kkSlab(...)` via `drawBehind`).
Exact geometry lives in `kk.css` under the listed class; states are shown on
`screenshots/boards/Components.png` and `Main.png`.

| Component | kk.css | Notes |
|---|---|---|
| Slab / Button | `.btn`, `.btn-*`, `.is-*`, `.armed` | Parallelogram face (horizontal cut 12–14 px), echo slab behind offset (6,6)→(10,10) on hover/focus. Variants: primary (volt face, ink text), bone, ghost (ink-3 face, bone text), hazard. States: idle, hover, focus (2 px bone outline offset 4), pressed (1-frame bone flash, echo collapses), disabled (ink-3, mute text), locked (hatch + lock icon), armed (hazard, second press confirms). Sizes xs 30 / sm 38 / md 52 / lg 72 height; touch min 44. |
| Menu item | `.mi` | Cond 900 italic, 64 px (desktop Home). Selected: bone slab grows from the item to the screen edge in 240 ms (Pull), volt echo offset (−14,10), 3 trailing speed lines, text turns ink, whole item translates −26 px and rotates −2°. Dim (mute-2) for secondary (e.g. exit). Locked: mute + lock icon. Optional `stamp` (e.g. "Unlockable"). Optional small mono sub-value (counts like "7/12"), shown only when selected. **No index numbers.** |
| Tile | `.tile` | Form/weapon tiles: ink-2 face with cut, selected = bone face + volt echo; locked = hatch. |
| List row | `.lrow` | Rows in lists (weapons, lab, settings): hover ink-3, selected bone face + volt echo. |
| Tabs / Segmented / Toggle / Slider | `.tabs .tab`, `.segctl`, `.tgl`, `.sld` | Tabs: cond labels, selected bone slab + volt underline. Segmented: sheared cells, selected volt. Toggle: sheared track, ON volt. Slider: sheared track + volt fill + slab thumb, −/+ stepper buttons. |
| Meter | `.meter`, `.seg`, `.striped`, `.striped-v`, `.g` | All meters lean −12°. Segmented where the unit matters (integrity: 20-point segments). Hit ghost: hazard/bone segment that drains over 500 ms. Overheat: hazard/ink diagonal stripes moving. Overdrive active: volt stripes moving. |
| Level badge | `.lvl .t1…t6` | "Lvl" small label + wide number. Tier by level under the run's level cap (42): t1 1–9 bone; t2 10–19 bone + ink seam + volt echo; t3 20–29 volt face + ink seam + bone echo + sheen; t4 30–39 ink face, volt text and inset line, volt/ink striped echo (5 px) + sheen; t5 40–41 gold foil (animated gradient, the colour-vision preset's gold) + ink seam + bone echo (6 px) + rotating gold rays; t6 APEX at the cap: ink face inside a gold foil frame, foil text, gold-striped echo (5 px) and volt echo (9 px), gold rays and counter-rotating volt rays, looks by Apex state (cooling: static dimmed foil, no volt echo; ready: animated foil, glow pulse, sheen; active: moving gold stripes on the frame, volt echo and volt rays fading with the activation). Face metrics are equal for every tier. Slams in on level-up; a tier change grows the echo, sweeps a bone shutter and rings (600 ms); reaching the cap slams from 1.6 and rings twice (1200 ms). |
| Info (!) + tooltip | `.info`, `.tipbox` | 24 px sheared outline square with italic "!" (mute; volt on hover/focus). Tooltip: bone slip, ink text, body 14 px, 270 px wide, top-right corner cut 10 px, appears on hover/focus/tap (tap toggles on touch). Focusable, has accessibility label. |
| Tag / Stamp / Chip / Toast | `.tag*`, `.stamp*`, `.chip`, `.toast` | Tag: small cond label on sheared plate (volt/hazard/bone/line variants). Stamp: rotated −6° cond label with hard offset shadow, slams in. Chip: header counters (gem + number + small label). Toast: build-update feed row. |
| Card (rarity) | `.card`, `.cfx`, `.crays`, `.cname`, `.band` | Rarity band on top, icon plate, name, two stat lines, tags. **Effects by rarity** — common: flat; uncommon: inner glow (inset, rarity color, 46 px blur); rare: + sheen sweep (3.4 s, Snap) + echo offset 4; epic: + halftone rising from bottom (9 px dots, 30%) + 5 rising diamond sparks (2.8 s) + echo offset 6 pulsing 1.6 s + name glow; legendary: + rotating conic rays behind the card, striped gold echo (animated), foil band and foil name (animated gradient r-legend↔#FFF3C8), ring burst when dealt. The face's right edge slants in by its 22 dp cut toward the bottom, so the rows under the name (and a relic's cost) end clear of that edge and the frame (its border, the epic rim) at the lowest line they show, 2 dp to spare (`kkCardFrameReachDp`): the measured text's bottom, or the text viewport's while it scrolls. The name and the labels above the rows sit high enough for the plain end padding. |
| Relic slot | `.rslot`, `.asp-*` | Diamond with aspect-colored outline, ink-2 inside, aspect icon. Empty = hatch. Synergy: straight bracket above linked slots with a tag label; preview synergy = dashed bracket. |
| Weapon slot | `.wslot` | Sheared square, icon, cooldown = conic sweep (ink 80%), ready = volt glow, "Lvl N" mono bottom-right, max level = legendary color. Empty = hatch + plus. |
| Fills | `.halftone`, `.stripes`, `.hatch`, `.bg-grid`, `.fade-rad` | Halftone: dot grid (color @10–14%) masked by radial fade. Stripes: 45° hazard/ink bands, animated. Hatch: 45° bone @8% lines. Grid: 40 px ink-1 grid lines. |
| Icons | `icons.json` | Parse SVG path data once with `PathParser().parsePathString(d).toPath()`, cache per icon, scale by size/24. Styles: stroke (2 grid units, square cap, miter join), fill, dashed (3/3), dotted (1/4). |
| Separator | `.sep` | 2 px × 0.85 em bar, 40% opacity, −12° shear. Use sparingly. |
| Transitions | `kk-shutter`, `a-slam`, `kk-deal`, `a-wipe`, `a-in-*` | Screen change = 3 slabs at −27.5° cross the screen in 420 ms staggered 60 ms (Snap); new screen swaps under the ink slab; entrances start as the last slab clears. Slam: scale 1.3→1 with −3° rotation (Pull, 220 ms). Deal: cards fly from the Core position with rotation (Pull, 550–750 ms, staggered 90 ms). |

Motion defaults: see `boards/Motion.dc.html` (curves, durations, choreography tables for
"enter Deploy" and "level up"). Ambient loops never faster than 24 s per revolution.

---

## 6. CSS → Compose translation

| CSS on boards | Compose / Skia |
|---|---|
| `clip-path: polygon(...)` slabs, chamfers | `Path` polygons built once per size; `drawPath` |
| `transform: skewX(-12deg)` | build sheared polygon geometry, or `withTransform { transform(Matrix().apply { values[Matrix.SkewX] = tan }) }` |
| `rotate`, `translate`, `scale` keyframes | `withTransform { rotate/translate/scale }` driven by the existing frame clock (`withFrameNanos`) + `KkEase` |
| linear/radial/conic gradients | `Brush.linearGradient` / `radialGradient` / `sweepGradient` |
| `mask-image` radial fades | draw into a layer (`drawContext.canvas.saveLayer`) then `drawRect(radialGradient, blendMode = BlendMode.DstIn)` |
| halftone dots | pre-render one dot tile to `ImageBitmap` once; `ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))` |
| repeating stripes / hatch | cached `ImageShader` tile or `Brush.linearGradient` with hard stops + `TileMode.Repeated`; animate by translating the shader matrix |
| `background-clip: text` foil | `drawText(..., brush = Brush.linearGradient(...))` with animated offset |
| `box-shadow` glow | a blurred copy only where cheap (menus); in-run use stacked translucent strokes instead of blur |
| `filter: grayscale()/brightness()/blur()` backdrop | Canvas screens: draw a scrim (ink @ 80–85%) over the world; optional `graphicsLayer { renderEffect = BlurEffect(...) }` on menus only (Android 12+ has it; elsewhere fall back to scrim) |
| CSS 3D (`perspective`, `rotateX(66deg)`) Home orbit | plain math: ellipse `x = r cos θ`, `y = r sin θ · cos 66°`, rotated −20°; draw back half before the singularity, front half after |
| `text-transform: uppercase` | uppercase with the current locale (`uppercase()`), keep RU correct |
| letter-spacing in em | `TextStyle(letterSpacing = x.em)` |

---

## 7. Screens

Reference px on boards = dp at the 1440×810 frame. Scale layouts with the existing geometry
helpers (`d()`, `*LayoutGeometry`, layout modes REGULAR / COMPACT_LANDSCAPE / COMPACT_PORTRAIT).
Always update geometry **and** its pointer resolver/hit-test together, keep semantics nodes,
content descriptions and `testTag`s working, and keep the three layout modes.

### 7.1 HUD — `ball/gameplay/interaction/.../canvas/HudRenderer.kt` (+ `StatusOverlayRenderer.kt`, `BuildNotificationRenderer.kt`, `PerformanceHudRenderer.kt`)
References: `HUD.png`, `HUD-Anatomy.png`, state boards `HUD-Dash/Overheat/Polarity/Overdrive/Critical/Elite/Architect.png`, `states/HUD--*.png`, `boards/HUD.dc.html` (its `renderVals()` defines what changes per state).
- **Top-left:** Level badge (tier by `level` and `maxLevel`), thin Data bar under it (`data/nextLevelData`), plus a 3 px Data line across the whole top edge. Bar and line evolve with the tier (t1 bone; t2 bone with a volt head; t3 volt; t4 volt/ink stripes on ink; t5 gold foil); at t6 both are the Apex meter (4 px line): gold recharge while cooling, full breathing foil while ready, draining gold stripes while active. Phones show no bar; their top line is the Apex meter.
- **Top-center:** timer only (`formatRunTime(elapsed)`), wide 44 px. Turns hazard in the Architect phase. Nothing under it.
- **Top-right:** matter chip (`runMatter`), key chip (`keys`, only when > 0), pause button (`RunningControlTarget.PAUSE`).
- **Right edge:** chain `×combo` (cond 900i) with a draining bar (`comboTime`), hidden when no combo.
- **World-anchored halos:** Core halo (left arc = integrity, right arc = heat, dashed ring = shield when `maxShield > 0`); cursor halo around the singularity (arc = `polarityStability`, turns hazard < 25%, pulses while draining); tether dashed Core→cursor line (weight scales with pull, solid hazard when contact is close — use `tetherDistance`).
- **Bottom-left:** integrity number only (wide 44 px, turns hazard when low) + segmented integrity bar (20-point segments, hit ghost) + shield cells (cyan) + dash charges as pips + heat bar (striped hazard when `overheated`). Character ability (`characterAbility`) shown as a small sheared meter in this cluster, no text label.
- **Bottom-center:** speed number (wide, horizontally stretched with `velocityTier`) + 30-tick velocity ladder (volt, last ticks hazard when over the top tier) + overdrive bar (`overdriveCharge`; volt stripes while `overdriveTime > 0`).
- **Bottom-right:** relic diamonds (`equippedRelics`, max from `content.relicPolicy.maxSlots`, synergy link bar) + **the game's active weapon slot(s)** (`weapon`, `weaponLevel`; the board shows six slots — show as many as the game actually has).
- **States (no text):** dash = afterimage rings + dash pip spend; overheat = striped heat bar + "Overheat" stamp at the Core only while offline; polarity = narrow hazard edge glow (60 dp) on the offending side + cursor halo pulse; overdrive = pulsing volt screen border; critical = hazard vignette at heart-rate pulse + integrity bar pulsing; elite = elite name + segmented hazard bar top-center under the timer; architect = "The Architect" (its weakness glyph before the name while it has an aspect) + 3-part hazard bar top-center, the active third's track in its aspect hue and striped while it shifts (no part labels), timer hazard.
- **Remove:** warning strings (`PolarityWarning`, `OverdriveActive`, etc. in HUD), "LV"/"Lv." labels, "/max" integrity, speed-state words, any key hints.
- **Messages** (`message`, `messageTime`, build notifications): toasts per `Components` toast + `Feedback.png` multi-kill banners (ink → bone → volt, docking under the chain counter, never covering the Core).
- **Performance HUD (F3):** mono 11 px, volt, top-left panel; keep content.
- **Phones:** `Mobile-HUD.png` (COMPACT_LANDSCAPE) and `Mobile-Portrait.png` (COMPACT_PORTRAIT): same modules, smaller; Dash (volt, large) and Brake (ink-3) touch buttons at the thumb side; no "safe area" or "finger offset" labels.
- **Active row (Z/X/C) and Apex (0.5.0):** shown once the first active item binds a slot. Desktop: three 52 u sheared squares extending the Dash/Brake row to the left (gap 8 u, 12 u before Brake), each with its bound key as a mono key cap 3 u below while the keyboard is in use (windows ≥ 600 dp). Landscape phones: Z and X left of Dash, C below Dash at the margin (48 dp). Portrait phones: Brake, Z, X, C, Dash share the bottom row (Brake and Dash narrow to 80/92 dp at the board width), the Build button moves into the top row left of pause and the performance toggle stays in the pause menu. A slot shows its effect's glyph (the shared `skillGlyph` table) in the effect's element; the element-less actives (Slingshot, Full Stop, Catalyze, Dual Infusion) draw in the preset's `kinetic` role, the volt their cards wear. It shows the cooldown as the weapon slot's ink sweep, rank pips (gold at rank III), two charge squares when it has charges and the active/in-flight bar. Every slot is usable from the start: a slot no active item holds is **empty, never locked** — a recessed Ink1 face inside a dashed Line2 edge (the "not yet" outline of an unmet pairing chip) with its three rank pips unlit, no glyph, no hatch, no lock; a press flashes it faintly; it reads "Active n: empty" / "Активный эффект n: пусто". Feedback: press = 90 ms bone face; refusal = 240 ms shake + bone sweep edge (no target also rings the effect's reach around the Core); ready = bone flash + volt ring; refund = the sweep edge flashes in the effect's colour; a new effect in the slot (an active item binds it, or replaces the one there) = slam + ring, keyed on the slot's effect (the first bind slides the row in; a replacement's lower rank never rings as a rank-up); rank-up = the new pip fills + ring. The Apex control is the level badge itself (its key cap sits at the Data bar's left end at the cap; a press while cooling shakes the badge); while the Apex runs, its boss strikes left show as gold pips above the boss bar.
- Hot path: this runs every frame. No allocations per frame (cache Paths, Brushes, text layouts where the existing code does), respect the existing performance benchmark (`GameplayTelemetryPerformanceBenchmark`).

### 7.2 World & combat feedback — `WorldEntityRenderer.kt`, `WorldCombatRenderer.kt`, `WorldBackgroundRenderer.kt`, `PointOfInterestRenderer.kt`, `fx/*`
References: `Feedback.png`, `HUD*.png`, `boards/Feedback.dc.html` (9 loops with timings).
- Background: ink with 40 px ink-1 grid, subtle radial halftone around the Core.
- Enemies: ink-1 fill (aspect-tinted), hazard outline (1.5/2/3 px by frame), hazard core dot for shooters; affinity as an element inner contour and a lower-left weakness tab; elite = octagon double outline, slow spin, its aspect as two element inner contours; Architect = triangle-in-square frame (see `Architect.png`), frames in its aspect hue, a pulsing bone shield while it shifts. Hit flash: fill turns bone for 1 frame.
- Core: bone disc; Form silhouette per `CoreShape` (ORB/PRISM/SHARD/RING/DIAMOND/TESSERACT). Singularity: hazard rings + dot with crosshair ticks.
- Damage numbers (respect existing settings: on/off, size, format, color thresholds): cond 900i; color tiers bone < 200, volt < 1K, heat < 3K, hazard ≥ 3K with crit stamp; pop with overshoot, drift away from the Core over 600 ms.
- Kill: flash, 4 shards follow travel direction, data/matter drops; "dismantled" slash glyph only for elites.
- Ram / impact: screen shake (respect the setting; a smooth sway whose reach grows with the square of the hit, capped at 6 dp; no full-screen flash), bone slash across the target, volt ring.
- Damage taken: hazard edge glow on the side of the source, split-channel integrity number, hazard ghost drains in 500 ms.
- Pickups: Data (bone diamonds) and Matter (volt gems) arc into the Core; one summed line, not a popup each.
- Points of interest (`PointOfInterestKind`): SEALED_ANOMALY = gravitic diamond marker with hazard corner brackets and a timer; COLLAPSING_ORBIT = bone ring with volt progress arc; RESONANT_CIRCUIT/totem = stacked sheared plates with a volt key block (see `Totem.png`, `Anomaly.png`). Off-screen markers: small icon + distance at the screen edge (no arrow shape).
- Reactions, skills and the Apex (0.5.0): each of the 21 reactions draws its own procedural look in its two elements' hues (`fx/FxSpec.kt` tables, one painter, no stored particles); Breaks keep their seven figures; skills draw in their element (the preset's volt without one), the Apex in the preset's gold with the character's own silhouette. Reaction, skill, Break and Apex links are styled per kind and coloured by element (the weapon zigzag stays the weapon's). Threat is reserved for damage the player takes: shockwave colour indices 8–14 are element rings, 15 gold, 16–22 jagged element quake rings.
- Lingering states come from the render model only: combat fields (a timer rim always, fills by kind; four volt rim ticks on the player's own fields), control glyphs and marks outside the status ring (daze, silence, throw, Blackfire, Unmake, marks, Void Anchor, Apex strike brackets), Core states, skill zones and the Void Anchor tether. No arrow-shaped marks; flames, shards and bars are blunt.
- Detail: a per-frame operation budget (transient 900, links 600, fields 220, glyphs 480, Core states 60; signature layers always draw), crowd detail (> 60 on-screen enemies drop secondary layers, > 90 keep signatures only) and the Reaction effects setting (Full / Reduced / Minimal: fewer layers and elements, lower peak alpha, lighter field fills, one-line links) — field rims, glyphs, Core states, damage numbers and the first-discovery stamp never change with it.
- Trial panel (when a trial is active): top-left panel per `Anomaly.png` — label, name, (!) info with the rules, segmented progress, reward text. No rule sentences in the panel.

### 7.3 Rewards — `rewards/RewardContent.kt`, `RewardPresentation.kt`, `RewardLabels.kt`
- `ChoiceType.ITEM` and `WEAPON`: `LevelUp.png` — world dimmed (grayscale + scrim), level badge slams top-left, 3 cards dealt from the Core with rotation (−4°, 0°, +3°), rarity effects per §5, selected card lifts 22 px with −1.5° tilt; buttons Reroll (with remaining count tag), Take, Build. Stat changes: "Now/Next" or before value muted + after value in rarity color — **no arrows**. "New!" stamp from persisted discovery (keep existing logic). Compact: `Mobile-LevelUp.png`.
- `TOTEM`: `Totem.png` — totem illustration left, "offerings" list right as large rows (icon plate, kind tag, name, description, "Lvl N" + 10 mastery ticks, mastery tier name), (!) info for mastery rules; Reroll + Take buttons.
- `RELIC` / `RELIC_BIND`: `Relic.png` — incoming relic panel left (aspect icon in big diamond, aspect tag, rank, name, description, effect line); **straight row of relic slots** in the middle with names under them; existing synergy = solid bracket + tag above linked slots; synergy the choice would create = dashed bracket in that aspect color; selected slot ring volt (bind) or hazard (replace); preview panel right with rows signed `+ / − / =`; primary button Bind/Replace (hazard for replace), Meld, Salvage with matter gem. Use the game's real actions (`RelicChoiceAction`).
- Active items (0.5.0 follow-up): an item with an active effect is an ordinary item card (rarity material, element hue and pattern, tag ornament, forecast, connections, Reborn) with these differences. Its identity chips lead with an **Active** chip naming the Z/X/C slot it binds to or ranks: the player's binding as a bone key cap after the label ("Active [Z]" / "Актив [Z]"), "Slot n" / "Ячейка n" while the input is touch (the host's last running input was a touch; any gameplay key brings the key back); while every slot holds another active the chip reads "Replaces a slot" / "Заменит ячейку", outlined in the threat role like a relic Replace. This first chip is never left out: where it is wider than the chip column (a long key name, the largest text size, a phone card) it is drawn scaled down to the column's width, while later chips still drop by priority. An active in the run character's lane (`CoreShapeDefinition.activeLane`) adds a Signature chip naming the character with its form. The band reads rarity · "Rank n/3" (the rank its effect reaches: owned stacks + 1) instead of "Stack n/max". The icon plate shows the effect's glyph with three sheared rank pips under it (filled to that rank; an unlit pip is a Line2 slot, or an Ink one where the plate is light enough to swallow Line2, as the tinted plates of pale hues and the lit Codex tiles are, `activeItemEmptyPipColor`); on the board a New! stamp sits on the plate's upper corner instead of its lower one, clear of the pips; this mark (`drawActiveItemMark`, `canvas/ItemIconRenderer.kt`) is the one the pause Build grid and the Codex's Build tiles draw for an active. The body leads with the **Active** section (what the effect does, that rank's numbers as one comma-joined line in which every value keeps its label and unit on its line, so a wrap never leaves a number alone, what rank III adds when the card reaches it, each of its numbers kept with the word before it), then the **Passive** label and the passive rule rows; phone cards keep both (mechanics are never dropped). One `SkillId` → glyph table (`skillGlyph`, `canvas/GameplayVisualSemantics.kt`) serves the HUD slots, card plates and the other screens; the watermark of an Uncommon or Rare active is that glyph except where its faint outline would repeat a card of the same rarity, element and ornament (`activeWatermark`: Gravity Well wears the spiral); the epic actives borrow family scenes whose owners never share their element and tag (Resonator Redline Reactor, Entropic Tide Dark Star, Catalyze Storm Lattice, Dual Infusion Volatile Compound).
- `ACTIVE_SLOT` ("Replace an active" / "Замена активного предмета", short like "Replace relic", so the stamp fits a portrait phone at 175 %): one card per held active, drawn as that held item's card (material, element hue, glyph and rank pips, band "Rank n/3", the slot's Active chip and lane chip, its effect at the held rank) titled "Replace {name}" and ending with "{incoming} takes this slot at rank 1" ("rank 1" on one line, and the Russian "с рангом 1" with its preposition), forecast chips from the nucleus replacement preview; the last card keeps the loadout and reads as a decline (hatched plate, Matter glyph, band "Actives" / "Активные", the category it keeps as the relic Leave hollow's band reads "Relics" (short enough for a portrait phone card's band, like the Codex's Active filter), "Every active stays and {incoming} is salvaged for +N matter" with "+N matter" on one line, N the nucleus's `ChoiceOption.salvageMatter`, never recomputed). Not rerollable.
- Remove subtitles that are instructions (e.g. "Select replace slot", "Time suspended") — the heading + the layout say it. Keep headings short.

### 7.4 Pause — `StatusOverlayRenderer.drawPause` + `PauseLayoutGeometry`
`Pause.png` / `Mobile-Pause.png`: skewed ink panel on the left with "Paused" (wide 92) and the frozen timer; menu items (Menu item component, pause size 46 px) for the game's `PauseTarget` entries only; destructive exit uses a hold/armed state if the game already confirms; right side "Build" overview with whatever the render model offers (weapon + level, relics with synergy, key stats, run statistics). No key hints.
- Items grid (desktop board): the active items lead in Z/X/C order (a long build never counts them away under "+N"), then the passive items in catalog order. An active draws as its slot in small: a sheared Ink2 plate (0.8 of the cell) edged in its hue at 60 %, the effect's mark with its rank pips (as its card's plate), and the slot's key on a bone key cap at the cell's top-right corner, ink mono, at least square, a third of the cell tall (the card's Active chip); "1/2/3" while the input is touch. Key names shrink to the cell's width, never past it. Reborn rim and suppression hatch as for any item.

### 7.5 Terminal / run report — `terminal/TerminalContent.kt`, `CoreDeathRenderer.kt`
`Report.png` (victory) and `Report-Defeat.png`: header tags (rebirth, form), huge two-line title (victory: volt line 1; defeat: hazard line 1), stamp with the cause, matter banked big + breakdown, bank total with (!) info ("Matter banks on victory, on death and when you quit"), build row (weapon slots with Lvl, relic diamonds), right skewed panel with Combat and Collection stats (Combat has (!) info for how damage is counted), buttons (victory: Rebirth + Re-enter; defeat: Re-enter + Lab; small Menu). Shatter illustration in the middle: victory = the Architect's diamond lattice shattered in volt with a surviving bone Core; defeat = the Core shattered in hazard around a black singularity (see `Report.dc.html` script for the procedural shard generator: 11 wedges, seeded, shards fly out 16–56 px and keep floating). **No diagonal slash line.** Keep existing reveal timing (`terminalRevealDelay`) and action readiness.

### 7.6 Home — `flow/session/interaction/home/impl/*` (`HomeRenderer.kt`, `HomeLayoutGeometry.kt`, `DefaultHomeFeature.kt`, `HomeState.kt`)
The game's Home hosts **both** the menu (START/LAB/ARMORY/REBIRTH/CODEX/SETTINGS) and the Core choice (six `CORE_*` targets). Combine `Home.png` (menu, hero, palettes, facts card) with the form tiles and form showcase from `Deploy.png`:
- Right: menu items (Menu item component, 64 px, stepped 12 px left per row), game's order and labels, optional count sub-values from real data. No key letters (`menuKeys` goes away).
- Left: hero = tilted orbit (3D-projected ellipse, rotateX 66°, rotateZ −20°): the Core loops around the singularity on a conic trail, accretion rays pulse inward, enemies placed on the orbit get hit as the Core passes (flash + ring + 3 shards). Big faint word of the selected item behind (acc @7%).
- **Palettes:** the selected/hovered item recolors the whole screen: Start `#D8FF3E`, Armory `#FF8A1F`, Lab `#45E0FF`, Rebirth `#FF4FA3`, Codex `#A98BFF`, Settings `#5CF2A6` (and `#A3A298` for an exit item if the game has one). Background = mix(acc 7%, ink 93%); halftone = acc @12%; transition 350 ms Out. Extend the existing `menuAccent()`; states in `states/Home--*.png` and `states/Mobile-Home--*.png`.
- Form choice: six tiles row (Tile component) with form icons from `icons.json` `forms.*` mapped to the game's `CoreShape`; the selected form shows as the big silhouette + its name in wide type + trick tag + one-line description + 4 pip stats if the game has them (`Deploy.png`, `states/Deploy--*.png`). Locked forms: hatch + lock icon.
- Facts card bottom-left: selected item label, one big value, 2–3 facts from real data, (!) info with the description. Chips top-right from real data (matter, rebirth, codex, weapons). Version bottom-right.
- Remove instruction strings (`HOME_INSTRUCTIONS`), copyright line with dots (keep license/copyright in a clean form if legally needed, e.g. a small mono line without separators).
- Phones: `Mobile-Home.png` + palette states.

### 7.7 Armory — `ball/profile/interaction/armory/impl/*`
`Armory.png`: header (Home button, title, "7/12"-style count from data, (!) info), 4-column weapon tiles (status STARTER/OWNED or cost with gem; icon; name; tags as separate words), right detail panel (icon plate, wide name, description, tag list, Mastery ladder with Lvl 1/3/6/10 milestones and their bonuses, (!) info), primary Unlock with cost / Set as starter / disabled with "Need N more" in hazard. No footer.

### 7.8 Core Matrix — `matrix/impl/*` (`MatrixRenderer.kt`, `MatrixState.kt`, `MatrixLayout.kt`)
Replaces the Lab (route and L key unchanged). Four branch columns (Kinetic, Bulwark, Systems, Fortune; role-coloured headers with icon and owned/total count) of `.tile` node tiles, two lanes per branch and one row per prerequisite depth; elbow prerequisite lines (bone when the prerequisite is owned, line-2 otherwise) bend in the gap above the node. A tile shows the name, rank pips (owned in the branch colour, next outlined when affordable), and the next price with a gem, `Max`, or the lock reason with the lock glyph (`Reborn N`, `Locked`); owned nodes carry a branch-coloured bar, keystones a gem. Right detail (phones: docked below the scrolling grid): branch / keystone / Rebirth tag, wide name, description, `Rank N/M` with pips, the status sentence (what is missing), Buy rank primary button with cost. Header: owned/total nodes + (!) info and a two-press `Reset` (ghost, then hazard `Confirm`) that refunds every rank. Hover highlights a tile; a press, a tap or keyboard focus selects it (Buy rank acts on that selection, never on a tile the pointer only crossed), a press or Enter on the selected tile buys, arrows move between tiles. Purchase feedback: the tile flashes (invert 1 frame) and nudges (Pull). No footer, no key hints.

### 7.9 Rebirth — `rebirth/impl/*` (`RebirthRenderer.kt`, `RebirthState.kt`, `RebirthPointerResolver.kt`)
`Rebirth.png`, `states/Rebirth--tierNN*.png`, `Rebirth-Tiers.png`, `Rebirth-X.png`, `Rebirth-Advance.png`, `boards/Rebirth.dc.html`:
- Theme per tier from `tokens.json rebirthTiers` (index = target tier), background tint, halftone density, orbit ring count, hazard band from tier 5, streaks; tier 10 = pure black, grayscale, animated black hole (lensing disk, photon ring, event horizon, infalling sparks — see `.bh` in kk.css).
- Big tier numeral (wide 300; 230 at tier 10), direction name (use the game's tier modifier names/data), tier ladder 0–10 (cleared = tier color, current = bone, next = outlined in its color, 10 = striped b/w), comparison table (hostile vs for-you modifiers, current vs next; increases in hazard), Advance button in the tier color with armed (two-press or the game's existing confirm) and locked states; (!) info: "Your run build resets. Matter, Matrix nodes, unlocked weapons, discoveries and settings stay." (adapt to what the game actually keeps).
- Advance animation (`Rebirth-Advance.png`, keyframes `kk-adv-*` in kk.css): whole sequence 1.6 s — shutter slabs at −27° sweep across in 620 ms (Snap, staggered), stepped hazard flash, old screen dims from 30%, new numeral slams from scale 2.6 / −10° after 28% (Pull), short shake at 30–62%, then the Start button. Nothing lingers after 1.6 s.
- Also theme the in-run arena lightly by rebirth tier (`rebirthLevel` is in the render model): grid tint and halftone color only.

### 7.10 Codex — `flow/session/interaction/codex/impl/*`
`Codex.png`: left nav = list rows per section with counts, Search field (label "Search", no instruction label), rarity legend with counts; center = 20×20 item matrix (families × components) if the game's catalog maps to it, else keep the game's grid and restyle cells (rarity color when discovered, hatch when undiscovered, selected ring bone, row/column highlight volt @10%); right detail: rarity tag, wide name, family/component tags, stat panels, "Max N". Remove explanatory captions ("20 families × 20 components…", "Pick it once…", "Row = family…"). Keep the game's filters as a segmented control: All, In build (needs a run) and Active (the discovered active items; "Active" / "Активные"). Like the sort, the segments shrink together to fit their column (the nav's width, the phone row's), and on a landscape phone the category tabs leave room for the filters beside them and the row's gaps.
- Items matrix shape (287 items, 0.5.0 with the eighteen active items): the Tag sort's rows hold 29/41/31/31/23/26/27/34/24/21 items and the catalog needs 55 reference columns. Banded (a rarity header, every rarity in its own column range, one line per row) needs those 55 columns plus a 4-column label at the smallest wide cell (22 dp): from a **1,530 dp** wide matrix (bands 12/15/12/12/4 columns starting at columns 4/16/31/43/55, 11 lines). Narrower matrices **pack** (each row wraps behind its label, no rarity header): a 1,440 dp matrix packs at one line per row (10 lines; accepted for 0.5.0, it banded before the actives joined), 1,280 dp at 10 lines, the 710 dp matrix of the 1440×810 frame at 27 lines. `CodexItemRowsTest.theShippedTagShapePacksOnTheBoardAndBandsOnAWideWindow` pins these numbers.
- Active items: a found active's cell shows its effect's glyph (the shared table, boxed within the glyph's reach, its lines at least 1.25 dp so a line glyph reads on the light chrome as firmly as the filled passive marks) instead of its element/tag mark, and an **ACTIVE notch** in the bottom-left corner above the stack bar: a Bone triangle (the key cap's colour; the NEW notch is the `you` one top-left) edged in Ink so it stands off every fill, legs max(5 dp, 0.2 cell), clear of the glyph and the other marks at 22–56 dp cells; read aloud as "Active item" / "Активный предмет". In the Build tab an active's tile draws its slot mark (glyph and rank pips). Its sheet adds an **Active** chip (the effect's glyph, `you` rim, as the reward card's) before the element and tag chips, the stack chip reads "Rank n/3" / "Ранг n/3", and after the flavor (the effect's summary) an **Active** section on the rule-line card: the effect's glyph and label, one row per rank (mono "Rank 1–3", `you` for I and II and gold for III like the slot's pips, then that rank's cooldown and values as one comma-joined line with the numbers in the item's hue, each value held on its label's line as on the card), then what rank III adds under a gold rule; the rule lines follow under a **Passive** label. All numbers come from the effect's content (`SkillDefinition.rankLines`, `rankThree`). "Offered from" shows the item's own level: actives wait for the run level whatever Matter the profile collected, and its (!) says so.

### 7.11 Settings — `settings/impl/*` (`SettingsRenderer.kt`, `SettingsLayout.kt`, `SettingsState.kt`, `SettingsPointerResolver.kt`, `SettingsVolumeControl.kt`)
`Settings.png`, `states/Settings--*.png`: tabs = the game's `SettingsGroup` (Game, Sound, Graphics, Interface); rows by type: segmented control, toggle, slider with −/+ steppers; each row has a (!) info with the explanation (no description lines); focused row = bone slab + volt marker bar at the left (a short sheared bar, **not** a triangle). Right: **live preview per tab** that reacts to the current values — Graphics: a mini arena showing screen shake, particles and the role palette (swatches YOU/THREAT/HEAT/SHIELD/POLARITY) that switches with Color vision; Interface: a damage-number/text-size sample that changes with size/format/thresholds and the run-statistics side; Sound: live level bars; Game: language sample word + simulation speed orbit. "Reset tab" ghost button top-right if the game supports resetting, else omit. Add the **Color vision** row as the first row of Graphics (§9).
- Controls (0.5.0): the Z/X/C bindings read "Active 1–3" / "Активный эффект 1–3" with "Casts the active effect in slot n." / "Применяет активный эффект из ячейки n." behind their (!); no settings text says skill / навык.

### 7.12 Other
- Profile-unavailable screen (`DefaultProfileUnavailableFeature.kt`): ink screen, wide title, body text, primary + ghost buttons.
- Shared `ProfilePanel.kt`: restyle to the header pattern (Home ghost button, title cond 44, count, (!) info, matter chip).
- Screen transitions between menus: shutter (§5) where the app already animates transitions; otherwise a 150 ms crossfade.

---

## 8. Copy pass (EN + RU)

Files: `ball/gameplay/interaction/.../localization/{English,Russian}GameplayText.kt`,
`flow/session/interaction/.../localization/SessionText{English,Russian}.kt`,
`ball/profile/interaction/.../localization/{English,Russian}ProfileText.kt`, and any content
localization.
- Remove every `·` / `→` separator (≈148 lines currently): split into separate UI elements or
  rephrase ("Progress · 3/10" → label "Progress" + value "3/10").
- Delete strings that only exist as hints/instructions once nothing renders them
  (`HOME_INSTRUCTIONS`, "Close · Esc", key legends…). Keep keys still used by tests updated.
- Level label → "Lvl" (EN) and the RU short form, formatted "Lvl 14".
- Headings: short, no trailing punctuation, uppercase applied by the text style (not baked into
  strings) where the renderer allows.
- Russian strings are longer: check every changed layout in RU at the three layout modes.
- Card text never ends a line on a fragment: a number keeps its unit ("40 %", "3 с") and a Russian one-letter word
  ("к", "в", "с", "и") keeps the word after it, both held by a no-break space (`rewardKeepUnits`).

---

## 9. Color vision (the one new setting)

- Domain: `enum ColorVision { DEFAULT, PROTAN, DEUTAN, TRITAN, MONO }` in the profile preference
  model (follow the existing preference structure in `ball/profile/api` / `nucleus`).
- **Save format safety** — `ProfileCodec` is strict and canonical (`ignoreUnknownKeys = false`,
  re-encode must equal the payload). Add the field exactly like `languageCode` /
  `runStatisticsOnLeft`: `@EncodeDefault(EncodeDefault.Mode.NEVER) val colorVisionId: String? = null`,
  null ⇒ DEFAULT, write the id only when not default (or follow the existing pattern precisely).
  Add codec tests: an existing payload without the field decodes and stays canonical; each
  value round-trips; unknown ids are rejected like other ids.
- Settings: first row in GRAPHICS, segmented control with the five options, (!) info:
  "Remaps you, threat, heat, shield and polarity to pairs you can tell apart. Mono adds
  hatching to every threat." Add EN + RU strings.
- Rendering: one `KkRolePalette(you, threat, heat, shield, pol)` value from `tokens.json
  colorVision`, provided to every renderer (CompositionLocal for composables; passed through
  the existing render context for Canvas renderers). All role colors (Volt/Hazard/Heat/Shield/Pol)
  in world + UI read from it. MONO also draws the hatch fill over every threat (enemies,
  projectiles, hazard meters).

---

## 10. Performance & platform

- Targets: Android, desktop (macOS/Windows/Linux), WASM. Keep 60 fps on mid phones.
- No per-frame allocations in hot paths (HUD, world). Cache: Paths per size, icon Paths,
  shader tiles (halftone/stripes/hatch) as `ImageBitmap` created once, text layouts where the
  code already caches.
- No real-time blur in-run. Blur/grayscale backdrops only for static overlays, with a scrim
  fallback where `RenderEffect` is unavailable (Android < 12).
- Fonts: ~1 MB total added; load once via compose resources; remove unused old fonts.
- Run the project's performance comparison (`tools/performance/README.md`) for the HUD/world
  changes and report numbers in the PR.

---

## 11. Architecture & repo rules

- Follow `AGENTS.md`: read and use the installed `pokeball` skill (and `pokeball-composition`,
  `pokeball-review` as relevant) before implementation and for review.
- `foundation:design` must not depend on game modules. Screen code stays in its
  `interaction` module. Do not move logic between modules.
- New files: SPDX header `// SPDX-FileCopyrightText: 2026 Vladislav Tomilov` +
  `// SPDX-License-Identifier: GPL-3.0-or-later`.
- Keep `testTag`s, semantics, content descriptions, focus order and keyboard/gamepad handling.
  Info (!) buttons are focusable and announce their text.
- Do not commit this package's `boards/` and `screenshots/` (keep them local); commit
  `SPEC.md`, `tokens.json`, `icons.json` under `docs/design/` if useful for future work.

---

## 12. Verification (all must pass before opening the PR)

```bash
./gradlew desktopTest
./gradlew :app:android:assembleDebug :app:android:assembleDebugAndroidTest :app:shared:assembleAndroidDeviceTest
CHROME_BIN=/path/to/chrome ./gradlew wasmJsBrowserTest wasmJsBrowserDistribution --max-workers=1
./gradlew verifyArchitecture
```
- Update rendering tests (`GameplayLocalizedRenderingTest`, `InventoryIconParityTest`,
  `TesseractRenderingTest`, `RewardContentTest`, `TerminalContentTest`,
  `InventoryGlyphRenderingTest`, `SettingsNavigationComposeTest`, …) to the new visuals without
  weakening what they check (localization fits, bounds, hit targets, discovery logic).
- **Visual QA:** write a local, uncommitted desktop harness (compose UI test with
  `onRoot().captureToImage()` → PNG under `build/redesign-shots/`) for every screen/state in
  §7 at 1440×810, 844×390 and 390×844, EN and RU. Compare each against the matching image in
  `screenshots/` and fix visible differences in layout, color, type, spacing and effects.
- Grep checks from §2 return nothing.

---

## 13. Board index

| Board / screenshot | Game target |
|---|---|
| Main, Components, Motion, Adaptive | tokens, components, motion, scaling rules (reference only) |
| Title | none in game — skip unless an intro/attract screen exists; reuse its logo lockup on Home if useful |
| Home, Mobile-Home, states/Home--*, states/Mobile-Home--* | 7.6 |
| Deploy, states/Deploy--* | 7.6 (form choice on Home) |
| Armory | 7.7 |
| Core Matrix (Lab route) | 7.8 |
| Rebirth, Rebirth-X, Rebirth-Advance, Rebirth-Tiers, states/Rebirth--* | 7.9 |
| Codex | 7.10 |
| Settings, states/Settings--* | 7.11 (+ §9) |
| HUD, HUD-*, HUD-Anatomy, states/HUD--*, Mobile-HUD, Mobile-Portrait | 7.1 |
| Feedback | 7.2 |
| Anomaly, Architect | 7.2 (points of interest, trial panel, Architect presence) |
| LevelUp, Mobile-LevelUp | 7.3 ITEM/WEAPON |
| Totem | 7.3 TOTEM |
| Relic | 7.3 RELIC / RELIC_BIND |
| Pause, Mobile-Pause | 7.4 |
| Report, Report-Defeat | 7.5 |
