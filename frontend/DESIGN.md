# Design system: Fatoora

Recorded from the built UI (Phase 8). Direction: **Arab modernist bilingual**. Invoicing
as a bilingual document system, not a blue-gradient SaaS dashboard. Tokens live in
`src/app/globals.css`; components in `src/components`.

## Principles

1. **Paper and ink.** Documents are paper-white sheets (`.paper`, stays white in dark
   mode) printed in ink on a faintly green neutral ground.
2. **States are stamps.** A document is never repainted: its state is stamped on it
   (`StatusStamp`): bordered, uppercase, double-ruled, one ink per state, slight tilt at
   large size. VOID is struck through. The press animation plays only on a fresh change.
3. **One committed field.** Cedar green owns the sidebar, landing hero, auth panel and
   document header bars; everything else stays restrained.
4. **Bilingual by default.** Latin and Arabic share one face and one baseline
   (Wordmark, PDF labels). Arabic is never letter-spaced or uppercase-transformed.
5. **Numbers line up.** Every amount, date and document number uses `.figures`
   (tabular, lining). Money shows as `JOD 1,250.500`, every decimal kept.

## Color

| Token | Light | Dark | Use |
|---|---|---|---|
| `--background` | oklch(0.972 0.005 165) | oklch(0.175 0.014 170) | page ground |
| `--card` | oklch(0.995 0.002 165) | oklch(0.21 0.016 170) | surfaces, sheets |
| `--foreground` | oklch(0.22 0.02 170) | oklch(0.93 0.012 160) | ink |
| `--primary` | oklch(0.42 0.075 166) | oklch(0.74 0.1 165) | actions, focus |
| `--cedar` | oklch(0.3 0.055 168) | oklch(0.27 0.045 168) | committed brand field |
| `--saffron` | oklch(0.82 0.145 80) | same | the one accent: primary CTA on cedar, "next step", active nav mark, selection |
| `--ink-*` | draft, sent, paid, partial, overdue, expired | lifted for dark | stamp inks only |
| `--series-1/2` | #167a59 / #d9a227 | #2f9a74 / #b8892a | chart series (validated: CVD, normal-vision, band; light saffron relies on legend + table) |

Saffron never carries body text on light surfaces. Status inks never stand in for chart series.

## Type

- **Readex Pro** (variable, Latin + Arabic) for everything; Geist Mono only for document
  numbers. Fixed rem scale: page title 1.6rem/600, section 1rem/600, body 0.875rem,
  labels 0.75rem uppercase 0.1–0.14em tracking (Latin only).
- PDF: **Tajawal** (embedded, OFL), bilingual labels `Total | الإجمالي`.

## Shape and space

- Radius 6px (`--radius: 0.375rem`); stamps 3px; logo seal 5/32.
- Containers: bordered `bg-card` panels, `rounded-lg`, no shadows except the paper sheet
  (offset, soft blur). Never nested cards.
- Pages: `max-w-6xl`, PageHeader (title, one line of why, actions). Forms use a two-column
  "section label | fields" grid.

## Components

- **App shell**: collapsible cedar sidebar (Work / Settings), org switcher (saffron
  initials seal), account menu with theme switch. Active item: saffron bar + lifted field.
- **StatusStamp**, **PageHeader**, **EmptyState** (teaches the first action), **ConfirmAction**
  (protected confirm for destructive or irreversible steps), toasts for outcomes.
- **DocumentEditor**: header fields, ruled line table, live exact totals, notes/terms.
- **DocumentSheet**: the paper document with stamp; mirrored by the PDF template.
- **OnboardingRoute** "from work to paid": seven legs, done legs checked, next leg in saffron.
- **Ledger strip**: dashboard totals as one ruled band (not floating KPI cards).
- **MoneyChart**: grouped bars, rounded data-ends, 2px pair gap, dashed recessive grid,
  legend, hover tooltip, table view.

## Motion

150–220ms ease-out on state only: stamp press (`--animate-stamp`), hover fields, dialog
and toast entry. No page-load choreography.

## Accessibility

Visible focus ring (2px `--ring`, 2px offset), themed selection and caret, every field
labelled (ids unique per mount because Next keeps previous routes mounted), color never
the only signal (stamps carry words, chart has legend and table), RTL-safe logical
spacing (`ms/me/ps/pe/start/end`).
