# UI Polish — Screens (Phases 2–5) Design

Date: 2026-07-25  
Status: approved for planning + execution (user: finish all)  
Depends on: Phase 1 foundation (`OrpheusSpacing`, `OrpheusMotion` distances, type roles, `LocalTerminalChrome`)

## Constraint (frontend.md HARD EXCEPTION)

Polish in place on Orpheus Design System. No new palette, aesthetic, or greenfield frontend pipeline. Pixel stays Pixel; Dark/Light stay mono terminal.

## Goals

Layout/visual polish across player, library, search, settings, setup:

1. Prefer `OrpheusSpacing` over magic `Dp` on touched surfaces.
2. Apply type roles: Title / Meta / Label per Phase 1 KDoc.
3. Gate terminal chrome via `terminalBorder` / `LocalTerminalChrome`.
4. Fix hierarchy/rhythm (player vertical; list row consistency).

## Non-goals

- New palette / Pixel rewrite / CRT revival
- Structural feature changes (playback, queue semantics, navigation graph)
- Broad copy rewrite
- Emulator / on-device UI drive (`CLAUDE.md`)

## Phase 2 — Player + mini

- Portrait/landscape columns: replace `SpaceAround` / `SpaceEvenly` with fixed vertical rhythm (`OrpheusSpacing` gaps + weighted spacers if needed).
- Metadata type: song title → `titleLarge` (or `titleMedium` if density requires); artist → `bodyMedium`; drop ad-hoc `RoundedSans`/sp overrides on terminal themes.
- Mini player: adopt spacing tokens; reduce crowding (prefer 72.dp height or 32.dp transport icons — pick one approach that keeps title readable).
- Insets: 24→`lg`, 12→`sm`, 8→`xs`, 4→`xxs` where used as padding/gaps.
- Travel: metadata translate 24px → `OrpheusMotion.DistanceMedium` when applicable.
- Gate decorative fills (toggle row / placeholders) behind `LocalTerminalChrome` or flatten for Pixel.

## Phase 3 — Library + search

- Adopt `OrpheusSpacing` on list gutters and row insets.
- Unify song/entity row chrome: `Surface` + `terminalBorder`; prefer one art size on local lists (50/56 → pick 56.dp or keep song 50 with entity 56 documented — prefer align entities to song template where cheap).
- Search: align search-bar and results horizontal insets.
- YouTube results: same row chrome as local search cards (not bare `Row`).
- Type roles on titles/meta/section labels.
- Remove idle double-border on terminal song rows (single `terminalBorder`).

## Phase 4 — Settings

- Apply `terminalBorder` to switch/slider/action rows that lack it (parity with `SettingsItem`).
- Subsection / list gaps: 2.dp → `OrpheusSpacing.xs` where cramped.
- Hub category tiles: add trailing chevron affordance.
- Type roles on titles/descriptions.

## Phase 5 — Setup

- Uniform horizontal page padding → `OrpheusSpacing.lg` (or `md` if density requires — consistent across pages).
- Headings from `TerminalTypography` / Material roles — no ad-hoc 32–46.sp + `RoundedSans` overrides on terminal.
- Card internal padding → nearest `OrpheusSpacing` tokens.

## Success criteria

- Compile + foundation unit tests pass.
- Touched surfaces use spacing/type/chrome tokens per above.
- No palette change; Pixel chrome still gated.
- No emulator requirement.

## Approval

User approved full package execution 2026-07-25 (“Aprova tudo — executa 2–5 agora”).
