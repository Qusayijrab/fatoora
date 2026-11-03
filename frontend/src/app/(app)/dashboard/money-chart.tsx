"use client";

import { useState } from "react";
import { formatAmount } from "@/lib/format";
import type { Money } from "@/lib/types";

type Point = { month: string; invoiced: Money; collected: Money };

const H = 200;
const PAD_TOP = 12;
const PAD_BOTTOM = 24;
const PAD_LEFT = 52;

function monthLabel(ym: string, long = false) {
  const [y, m] = ym.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, 1)).toLocaleDateString("en-GB", {
    month: "short",
    year: long ? "numeric" : undefined,
    timeZone: "UTC",
  });
}

/** A round axis maximum (1, 2 or 5 x 10^n) above the data. */
function niceMax(v: number) {
  if (v <= 0) return 1;
  const p = 10 ** Math.floor(Math.log10(v));
  for (const m of [1, 2, 5, 10]) if (m * p >= v) return m * p;
  return 10 * p;
}

function compact(minor: number, decimals: number) {
  const major = minor / 10 ** decimals;
  return new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(major);
}

/**
 * Invoiced vs collected per month: grouped bars on one axis (same unit, same currency).
 * Thin bars with rounded data-ends on the baseline, a 2px gap inside each pair, recessive
 * grid, a legend, a per-month hover tooltip, and the same numbers as a table.
 */
export function MoneyChart({ months, currency, decimals }: { months: Point[]; currency: string; decimals: number }) {
  const [hover, setHover] = useState<number | null>(null);
  const max = niceMax(Math.max(...months.flatMap((m) => [m.invoiced.amountMinor, m.collected.amountMinor])));
  const W = 720;
  const plotW = W - PAD_LEFT - 8;
  const slot = plotW / months.length;
  const bar = Math.min(14, (slot - 10) / 2);
  const y = (v: number) => PAD_TOP + (H - PAD_TOP - PAD_BOTTOM) * (1 - v / max);
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((t) => t * max);
  const empty = months.every((m) => m.invoiced.amountMinor === 0 && m.collected.amountMinor === 0);
  const active = hover === null ? null : months[hover];

  return (
    <figure className="grid gap-3">
      <div className="flex flex-wrap items-center gap-x-5 gap-y-1 text-xs text-muted-foreground">
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden className="size-2.5 rounded-[2px] bg-series-1" /> Invoiced
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden className="size-2.5 rounded-[2px] bg-series-2" /> Collected
        </span>
        <span className="ms-auto">{currency}, at the rate frozen on each invoice</span>
      </div>

      <div className="relative">
        <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img" aria-label="Invoiced and collected per month, last 12 months">
          {ticks.map((t) => (
            <g key={t}>
              <line x1={PAD_LEFT} x2={W - 8} y1={y(t)} y2={y(t)} className="stroke-border" strokeWidth={1} strokeDasharray={t === 0 ? undefined : "2 4"} />
              <text x={PAD_LEFT - 8} y={y(t) + 4} textAnchor="end" className="figures fill-muted-foreground text-[11px]">
                {compact(t, decimals)}
              </text>
            </g>
          ))}
          {months.map((m, i) => {
            const cx = PAD_LEFT + slot * i + slot / 2;
            const vals = [m.invoiced.amountMinor, m.collected.amountMinor];
            return (
              <g key={m.month} onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
                {/* hit target: the whole month column */}
                <rect x={cx - slot / 2} y={PAD_TOP} width={slot} height={H - PAD_TOP - PAD_BOTTOM} className={i === hover ? "fill-muted" : "fill-transparent"} />
                {vals.map((v, k) => {
                  const x = k === 0 ? cx - bar - 1 : cx + 1;
                  const top = y(v);
                  const h = Math.max(0, y(0) - top);
                  if (h === 0) return null;
                  const r = Math.min(4, h, bar / 2);
                  // Rounded top (data end), square on the baseline.
                  const d = `M${x},${y(0)} V${top + r} Q${x},${top} ${x + r},${top} H${x + bar - r} Q${x + bar},${top} ${x + bar},${top + r} V${y(0)} Z`;
                  return <path key={k} d={d} className={k === 0 ? "fill-series-1" : "fill-series-2"} />;
                })}
                <text x={cx} y={H - 6} textAnchor="middle" className="fill-muted-foreground text-[11px]">
                  {monthLabel(m.month)}
                </text>
              </g>
            );
          })}
        </svg>
        {active && hover !== null && (
          <div
            role="status"
            className="pointer-events-none absolute top-1 z-10 min-w-44 rounded-md border bg-popover px-3 py-2 text-xs shadow-md"
            style={{ left: `clamp(0%, ${((PAD_LEFT + slot * hover + slot / 2) / W) * 100}% - 5.5rem, calc(100% - 11rem))` }}
          >
            <p className="mb-1 font-medium">{monthLabel(active.month, true)}</p>
            <p className="figures flex justify-between gap-4">
              <span className="inline-flex items-center gap-1.5 text-muted-foreground">
                <span aria-hidden className="size-2 rounded-[2px] bg-series-1" /> Invoiced
              </span>
              {formatAmount(active.invoiced.amount)}
            </p>
            <p className="figures flex justify-between gap-4">
              <span className="inline-flex items-center gap-1.5 text-muted-foreground">
                <span aria-hidden className="size-2 rounded-[2px] bg-series-2" /> Collected
              </span>
              {formatAmount(active.collected.amount)}
            </p>
          </div>
        )}
        {empty && (
          <p className="absolute inset-0 grid place-items-center text-sm text-muted-foreground">
            Nothing issued or paid in the last 12 months yet.
          </p>
        )}
      </div>

      <details className="text-sm">
        <summary className="cursor-pointer text-xs text-muted-foreground hover:text-foreground">Show as a table</summary>
        <table className="figures mt-2 w-full text-xs">
          <thead>
            <tr className="border-b text-muted-foreground">
              <th className="py-1.5 text-start font-medium">Month</th>
              <th className="py-1.5 text-end font-medium">Invoiced</th>
              <th className="py-1.5 text-end font-medium">Collected</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {months.map((m) => (
              <tr key={m.month}>
                <td className="py-1.5">{monthLabel(m.month, true)}</td>
                <td className="py-1.5 text-end">{formatAmount(m.invoiced.amount)}</td>
                <td className="py-1.5 text-end">{formatAmount(m.collected.amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}
