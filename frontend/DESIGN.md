# Design system: Fatoora

Direction: **Clean indigo**. A bright, modern SaaS workspace: white cards on a cool
light-gray page, one indigo accent, soft shadows, and document states as tinted pills.
Tokens live in `src/app/globals.css`; components in `src/components`.

## Principles

1. **One accent.** Indigo marks what you can do (primary buttons, links, focus, the active
   nav item). Everything else is zinc neutrals.
2. **Cards give structure.** Every panel is a white `rounded-xl` card with a 1px border and
   `shadow-xs`; KPI cards and section headings carry a small tinted icon tile so each panel
   is recognisable at a glance.
3. **States are pills.** `StatusBadge`: a dot and the state name on a 10–12% wash of the
   state's color, sentence case. The DOM text stays the raw status (`PARTIALLY PAID`).
4. **Paper stays paper.** Document previews (`.paper`) stay white in dark mode, with an
   indigo top bar, mirroring the PDF.
5. **Numbers line up.** Every amount, date and document number uses `.figures`
   (tabular, lining). Money shows as `JOD 1,250.500`, every decimal kept.
6. **Bilingual by default.** Latin and Arabic share one face (Readex Pro). Arabic is never
   letter-spaced or uppercase-transformed.

## Color

| Token | Light | Dark | Use |
|---|---|---|---|
| `--background` | oklch(0.982 0.003 286) | oklch(0.155 0.006 286) | page |
| `--card` | white | oklch(0.2 0.008 286) | cards, popovers |
| `--foreground` | oklch(0.21 0.02 285) | oklch(0.96 0.004 286) | text |
| `--primary` | indigo 600, oklch(0.511 0.262 277) | indigo 400, oklch(0.673 0.182 277) | actions, links, focus |
| `--sidebar` | oklch(0.967 0.004 286) | oklch(0.13 0.006 286) | light sidebar; active item is a white card with indigo text |
| `--brand` / `--brand-foreground` | deep indigo / near-white | same, darker | landing hero, auth panel, pay and portal headers |
| `--pop` | light indigo | same | Arabic headline and accents on the brand field |
| `--ink-*` | draft, sent, paid, partial, overdue, expired | lifted for dark | state pills and KPI tiles only |
| `--series-1/2` | #4f46e5 / #0d9488 | #6c6ff2 / #1aa89a | chart: invoiced / collected (validated with the dataviz validator, both modes) |

Status colors never stand in for chart series.

## Type

- **Readex Pro** (variable, Latin + Arabic) everywhere; Geist Mono only for document numbers.
- Page title 1.6rem/600, card title 1rem/600, body 0.875rem, helper 0.75rem.

## Shape and space

- Radius 10px (`--radius: 0.625rem`); cards `rounded-xl`; pills fully rounded.
- Shadows: `shadow-xs` on cards and the primary button, `shadow-md` on card hover and
  popovers, a soft indigo-tinted drop under paper sheets.
- Pages: `max-w-6xl`, PageHeader (title, one line of why, actions).

## Components

- **App shell**: light collapsible sidebar (Work / Settings), org switcher card with an
  indigo initials tile, account menu with theme switch.
- **Dashboard**: four KPI cards with tinted icon tiles, chart card, aging bars, top
  clients with initials avatars, activity feed, and the onboarding route with a progress bar
  (next step as a filled indigo button).
- **StatusTabs**: segmented control (muted track, active segment is a white raised chip).
- **StatusBadge**, **PageHeader**, **EmptyState** (indigo icon tile), **ConfirmAction**, toasts.
- **DocumentSheet** and the PDF: indigo top bar, state pill under the number.

## Motion

150–220ms ease-out on state only: badge pop (`--animate-pop`), hover, dialogs, toasts.

## Accessibility

Visible focus ring (2px `--ring`), every field labelled, color never the only signal
(pills carry words, chart has legend and table), RTL-safe logical spacing.
