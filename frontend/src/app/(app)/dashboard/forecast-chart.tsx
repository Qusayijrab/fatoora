"use client";

import { useState } from "react";
import { formatAmount, formatMoney } from "@/lib/format";
import type { Forecast } from "@/lib/types";

const H = 180;
const PAD_TOP = 12;
const PAD_BOTTOM = 24;
const PAD_LEFT = 52;

function weekLabel(iso: string, long = false) {
  return new Date(`${iso}T00:00:00Z`).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: long ? "numeric" : undefined,
    timeZone: "UTC",
  });
}

function niceMax(v: number) {
  if (v <= 0) return 1;
  const p = 10 ** Math.floor(Math.log10(v));
  for (const m of [1, 2, 5, 10]) if (m * p >= v) return m * p;
  return 10 * p;
}

function compact(minor: number, decimals: number) {
  return new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(minor / 10 ** decimals);
}

/**
 * Expected cash in per week for the next 12 weeks: one stacked bar per week (open
 * invoices below, recurring invoices not yet issued above), the same style and tooltip
 * as the monthly chart, and the numbers as a table.
 */
export function ForecastChart({ forecast, decimals }: { forecast: Forecast; decimals: number }) {
  const [hover, setHover] = useState<number | null>(null);
  const weeks = forecast.weeks;
  const totals = weeks.map((w) => w.invoiced.amountMinor + w.recurring.amountMinor);
  const max = niceMax(Math.max(...totals));
  const W = 720;
  const plotW = W - PAD_LEFT - 8;
  const slot = plotW / weeks.length;
  const bar = Math.min(26, slot - 12);
  const y = (v: number) => PAD_TOP + (H - PAD_TOP - PAD_BOTTOM) * (1 - v / max);
  const ticks = [0, 0.5, 1].map((t) => t * max);
  const empty = totals.every((t) => t === 0);
  const active = hover === null ? null : weeks[hover];

  return (
    <figure className="grid gap-3">
      <div className="flex flex-wrap items-center gap-x-5 gap-y-1 text-xs text-muted-foreground">
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden className="size-2.5 rounded-[2px] bg-series-1" /> Open invoices
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden className="size-2.5 rounded-[2px] bg-series-2" /> Recurring, not yet issued
        </span>
        <span className="ms-auto">{forecast.baseCurrency}</span>
      </div>

      <div className="relative">
        <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img" aria-label="Expected cash in per week, next 12 weeks">
          {ticks.map((t) => (
            <g key={t}>
              <line x1={PAD_LEFT} x2={W - 8} y1={y(t)} y2={y(t)} className="stroke-border" strokeWidth={1} strokeDasharray={t === 0 ? undefined : "2 4"} />
              <text x={PAD_LEFT - 8} y={y(t) + 4} textAnchor="end" className="figures fill-muted-foreground text-[11px]">
                {compact(t, decimals)}
              </text>
            </g>
          ))}
          {weeks.map((w, i) => {
            const x = PAD_LEFT + slot * i + (slot - bar) / 2;
            const inv = w.invoiced.amountMinor;
            const rec = w.recurring.amountMinor;
            const invTop = y(inv);
            const recTop = y(inv + rec);
            const r = Math.min(4, bar / 2);
            // The top segment gets the rounded data end; the bottom one is square.
            const rounded = (top: number, bottom: number) =>
              bottom - top <= 0
                ? null
                : `M${x},${bottom} V${top + Math.min(r, bottom - top)} Q${x},${top} ${x + r},${top} H${x + bar - r} Q${x + bar},${top} ${x + bar},${top + Math.min(r, bottom - top)} V${bottom} Z`;
            return (
              <g key={w.start} onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
                <rect x={PAD_LEFT + slot * i} y={PAD_TOP} width={slot} height={H - PAD_TOP - PAD_BOTTOM} className={i === hover ? "fill-muted" : "fill-transparent"} />
                {inv > 0 && (rec > 0 ? <rect x={x} y={invTop} width={bar} height={y(0) - invTop} className="fill-series-1" /> : <path d={rounded(invTop, y(0)) ?? ""} className="fill-series-1" />)}
                {rec > 0 && <path d={rounded(recTop, invTop - (inv > 0 ? 1 : 0)) ?? ""} className="fill-series-2" />}
                {i % 2 === 0 && (
                  <text x={PAD_LEFT + slot * i + slot / 2} y={H - 6} textAnchor="middle" className="fill-muted-foreground text-[11px]">
                    {i === 0 ? "This week" : weekLabel(w.start)}
                  </text>
                )}
              </g>
            );
          })}
        </svg>
        {active && hover !== null && (
          <div
            role="status"
            className="pointer-events-none absolute top-1 z-10 min-w-48 rounded-md border bg-popover px-3 py-2 text-xs shadow-md"
            style={{ left: `clamp(0%, ${((PAD_LEFT + slot * hover + slot / 2) / W) * 100}% - 6rem, calc(100% - 12rem))` }}
          >
            <p className="mb-1 font-medium">Week of {weekLabel(active.start, true)}</p>
            <p className="figures flex justify-between gap-4">
              <span className="inline-flex items-center gap-1.5 text-muted-foreground">
                <span aria-hidden className="size-2 rounded-[2px] bg-series-1" /> Open invoices
              </span>
              {formatAmount(active.invoiced.amount)}
            </p>
            <p className="figures flex justify-between gap-4">
              <span className="inline-flex items-center gap-1.5 text-muted-foreground">
                <span aria-hidden className="size-2 rounded-[2px] bg-series-2" /> Recurring
              </span>
              {formatAmount(active.recurring.amount)}
            </p>
          </div>
        )}
        {empty && (
          <p className="absolute inset-0 grid place-items-center text-sm text-muted-foreground">
            Nothing open or scheduled for the next 12 weeks.
          </p>
        )}
      </div>

      <p className="text-xs text-muted-foreground">
        {formatMoney(forecast.total)} expected
        {forecast.alreadyLate.amountMinor > 0 && <>, including {formatMoney(forecast.alreadyLate)} already late (counted this week)</>}.
        {" "}Each invoice is expected on its due date
        {forecast.clientsWithHistory > 0
          ? `, moved by how late that client usually pays (${forecast.clientsWithHistory} ${forecast.clientsWithHistory === 1 ? "client has" : "clients have"} enough history)`
          : "; once a client has 3 paid invoices, their usual delay is taken into account"}
        .{forecast.skippedRecurring > 0 && ` ${forecast.skippedRecurring} recurring runs are left out: no exchange rate for their currency.`}
      </p>

      <details className="text-sm">
        <summary className="cursor-pointer text-xs text-muted-foreground hover:text-foreground">Show as a table</summary>
        <table className="figures mt-2 w-full text-xs">
          <thead>
            <tr className="border-b text-muted-foreground">
              <th className="py-1.5 text-start font-medium">Week of</th>
              <th className="py-1.5 text-end font-medium">Open invoices</th>
              <th className="py-1.5 text-end font-medium">Recurring</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {weeks.map((w) => (
              <tr key={w.start}>
                <td className="py-1.5">{weekLabel(w.start, true)}</td>
                <td className="py-1.5 text-end">{formatAmount(w.invoiced.amount)}</td>
                <td className="py-1.5 text-end">{formatAmount(w.recurring.amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}
