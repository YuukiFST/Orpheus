# UI Polish — Foundation (Phase 1) Design

Date: 2026-07-25  
Status: approved for planning  
Scope: design-system foundation only (tokens / chrome / type roles / motion grid). Screen redesigns are later phases.

## Constraint (frontend.md HARD EXCEPTION)

Orpheus already has a Design System (terminal phosphor palette, JetBrains Mono Nerd, `OrpheusShapeSet`, `OrpheusMotion`, `TerminalModifiers`, Pixel theme mode).

- **Do not** introduce a new aesthetic, palette, or greenfield frontend-skill pipeline.
- **Do** polish in place: spacing scale, motion orphans → tokens, chrome discipline, type role clarity.
- Pixel mode stays Pixel; Dark/Light stay mono terminal. No palette rewrite.

## Goals

1. Give every screen a shared spacing grid so layout polish later is consistent.
2. Make motion reference `OrpheusMotion` (and new distance tokens) instead of ad-hoc ms values on high-traffic surfaces.
3. Document type roles so titles / meta / labels stop drifting.
4. Keep terminal chrome (`terminalBorder`, press scale) only where `LocalTerminalChrome` intends it.

## Non-goals

- New palette, accent, or Pixel theme rewrite.
- Redesign of player, library, search, settings, or setup screens (Phases 2–5).
- Broad copy rewrite.
- Emulator / on-device UI driving (see `CLAUDE.md`).
- Adding new motion recipes for their own sake; this phase retargets existing motion.

## Roadmap (app-wide polish — approved)

| Phase | Focus | Spec timing |
|-------|--------|-------------|
| **1 (this)** | Tokens / chrome / type / motion grid | This doc |
| **2** | Player + mini sheet layout/visual | Separate spec after Phase 1 ships |
| **3** | Library + search | Separate spec |
| **4** | Settings | Separate spec |
| **5** | Setup (post-install) | Separate spec |

Each phase: own spec → plan → implement. No big-bang PR.

## Phase 1 — Foundation

### 1. Spacing — `OrpheusSpacing`

Add a token object (Compose `Dp` constants) in `ui/theme/`:

| Token | Value |
|-------|-------|
| `xxs` | 4.dp |
| `xs` | 8.dp |
| `sm` | 12.dp |
| `md` | 16.dp |
| `lg` | 24.dp |
| `xl` | 32.dp |

**Usage in this phase:** adopt on shared chrome surfaces (common modifiers / shared list chrome / theme helpers touched while wiring tokens). Full screen-by-screen padding migration is Phases 2–5 — those phases **must** prefer `OrpheusSpacing` over magic numbers.

### 2. Motion — orphans → `OrpheusMotion`

Extend `Motion.kt` with **distance** tokens aligned to transitions.dev usage:

| Token | Value | Usage |
|-------|-------|--------|
| `DistanceMicro` | 4.dp | text / content swap |
| `DistanceSmall` | 6.dp | small shake segment |
| `DistanceBase` | 8.dp | page slide, badge diagonal |
| `DistanceMedium` | 12.dp | text reveal |
| `DistanceLarge` | 30.dp | ceremonial appear only |

Retarget ad-hoc durations/easings/distances on high-traffic surfaces already in scope for foundation wiring:

- Player sheet open/close / expand (not a full player redesign)
- Lyrics panel motion if present
- Slider / scrubber related animation specs if hardcoded
- Nav / tab sliding that already animates

**Rules (transitions-polish):**

- Open/close asymmetry: open `DurationFast` (250), close `DurationQuick` (150); shared `EaseSmoothOut`.
- Never bounce a close; overshoot only on entrances.
- Stagger offset `DurationStagger` (40); total stagger under ~300ms.
- Never delay a close or dismiss.
- Usage-first mapping — do not force-swap values that match no token usage.

### 3. Type roles (documentation + light enforcement)

Document in theme (KDoc on `Type.kt` / typography helpers), no new font family:

| Role | Use |
|------|-----|
| **Title** | Primary track / screen heading |
| **Meta** | Artist, secondary line, timestamps |
| **Label** | Chrome labels, settings rows, section labels |

Phase 1 does not restyle every screen; it locks the contract so Phases 2–5 apply roles instead of inventing sizes.

### 4. Chrome discipline

- `terminalBorder` / press-scale modifiers apply only when terminal chrome is active (`LocalTerminalChrome` / existing gates).
- Pixel / non-terminal modes must not inherit terminal border noise from foundation changes.
- No new decorative borders, glows, or CRT overlays in this phase.

## Architecture / files

Expected touch set (implementation may narrow):

- `app/.../ui/theme/Motion.kt` — distance tokens; any helper tweaks
- New: `app/.../ui/theme/Spacing.kt` (or equivalent name) — `OrpheusSpacing`
- `app/.../ui/theme/Type.kt` — role KDoc / minor role alignment if already inconsistent in theme layer
- `app/.../ui/theme/TerminalModifiers.kt` — chrome gate clarity only if needed
- High-traffic animation call sites that hardcode ms (sheet / lyrics / slider / nav) — retarget only

Unit tests: extend theme/resolution tests if spacing/motion helpers are testable; no UI instrumentation.

## Success criteria

- `OrpheusSpacing` exists and is the documented padding source for later phases.
- Listed motion orphans on foundation surfaces use `OrpheusMotion` (+ distance tokens where applicable).
- Type roles documented in theme code.
- Terminal chrome still gated; Pixel/Light/Dark unchanged in palette.
- Compile + relevant unit tests pass. No emulator requirement.

## Out of scope until later phases

Player/mini visual hierarchy, library rows, search layout, settings IA, setup wizard layout, and copy polish — each gets its own design doc after Phase 1 lands.

## Approval record

- Roadmap A (foundation first, then screens): approved  
- Phase 1 foundation contents: approved  
- Deliverables + non-goals: approved  
