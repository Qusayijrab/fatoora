"use client";

import { useState } from "react";
import { StatusBadge } from "@/components/status-badge";
import { cn } from "@/lib/utils";

const STATES = [
  { status: "DRAFT", label: "Draft", note: "Editable. No number yet." },
  { status: "ISSUED", label: "Issue", note: "Numbered INV-2026-0042. Locked from here on." },
  { status: "PARTIALLY_PAID", label: "Part-pay", note: "JOD 500.000 received by payment link." },
  { status: "PAID", label: "Pay", note: "Settled. The client gets a receipt." },
  { status: "VOID", label: "Void", note: "Cancelled, never deleted. The record stays." },
] as const;

/**
 * The landing page's signature interaction: an example invoice the visitor walks
 * through its real lifecycle. Each press re-mounts the badge so it pops in again.
 */
export function InvoiceSpecimen() {
  const [index, setIndex] = useState(3);
  const [presses, setPresses] = useState(0);
  const state = STATES[index];
  const numbered = state.status !== "DRAFT";

  return (
    <figure className="relative">
      <div className="paper relative overflow-hidden rounded-xl bg-card text-card-foreground shadow-[0_30px_60px_-20px_oklch(0.1_0.08_277/0.6),0_2px_6px_oklch(0.1_0.08_277/0.25)]">
        <div className="h-1.5 bg-primary" />
        <div className="grid gap-6 p-6 sm:p-8">
          <header className="flex items-start justify-between gap-4">
            <div>
              <p className="text-sm font-semibold">Al-Nada Trading</p>
              <p lang="ar" dir="rtl" className="text-end text-sm text-muted-foreground">
                شركة الندى للتجارة
              </p>
              <p className="mt-1 text-xs text-muted-foreground">Amman · Tax no. 0123456</p>
            </div>
            <div className="text-end">
              <p className="text-[11px] font-medium uppercase tracking-[0.12em] text-muted-foreground">Invoice</p>
              <p className="figures mt-0.5 font-mono text-sm font-medium">{numbered ? "INV-2026-0042" : "— draft —"}</p>
              <p className="figures mt-0.5 text-xs text-muted-foreground">Due 30 Nov 2026</p>
              <StatusBadge key={`${state.status}-${presses}`} status={state.status} fresh className="mt-2" />
            </div>
          </header>

          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <p className="text-[11px] font-medium uppercase tracking-[0.12em] text-muted-foreground">Bill to</p>
              <p className="mt-1 font-medium">Petra Design Studio</p>
              <p lang="ar" dir="rtl" className="text-end text-muted-foreground">
                استوديو البتراء للتصميم
              </p>
            </div>
          </div>

          <table className="figures w-full text-sm">
            <thead>
              <tr className="border-b text-start text-[11px] uppercase tracking-[0.1em] text-muted-foreground">
                <th className="pb-2 text-start font-medium">Item</th>
                <th className="pb-2 text-end font-medium">Qty</th>
                <th className="pb-2 text-end font-medium">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              <tr>
                <td className="py-2.5">Brand identity design</td>
                <td className="py-2.5 text-end">1</td>
                <td className="py-2.5 text-end">850.000</td>
              </tr>
              <tr>
                <td className="py-2.5">Bilingual website copy</td>
                <td className="py-2.5 text-end">12 h</td>
                <td className="py-2.5 text-end">300.000</td>
              </tr>
            </tbody>
            <tfoot>
              <tr className="border-t">
                <td colSpan={2} className="pt-3 text-muted-foreground">Subtotal</td>
                <td className="pt-3 text-end">1,150.000</td>
              </tr>
              <tr>
                <td colSpan={2} className="pt-1 text-muted-foreground">Sales tax 16%</td>
                <td className="pt-1 text-end">184.000</td>
              </tr>
              <tr>
                <td colSpan={2} className="pt-3 text-base font-semibold">Total</td>
                <td className="pt-3 text-end text-base font-semibold">JOD 1,334.000</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      <figcaption className="mt-4">
        <div role="group" aria-label="Move the example invoice through its states" className="flex flex-wrap gap-1.5">
          {STATES.map((s, i) => (
            <button
              key={s.status}
              type="button"
              aria-pressed={i === index}
              onClick={() => {
                setIndex(i);
                setPresses((p) => p + 1);
              }}
              className={cn(
                "rounded-full border border-brand-foreground/25 px-3.5 py-1.5 text-xs font-medium text-brand-foreground transition-colors duration-150 hover:bg-brand-foreground/10",
                i === index && "border-brand-foreground bg-brand-foreground text-brand hover:bg-brand-foreground",
              )}
            >
              {s.label}
            </button>
          ))}
        </div>
        <p className="mt-2 min-h-5 text-xs text-brand-foreground/75" aria-live="polite">
          {state.note} <span className="opacity-70">Example invoice.</span>
        </p>
      </figcaption>
    </figure>
  );
}
