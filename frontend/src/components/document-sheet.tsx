import type { ReactNode } from "react";
import { StatusBadge } from "@/components/status-badge";
import { countryName } from "@/lib/countries";
import { formatAmount, formatDate, formatMoney } from "@/lib/format";
import type { Line, Money, Party } from "@/lib/types";

/**
 * A document as the client sees it: a paper-white sheet that stays white in dark mode,
 * with its state shown under the number. Used for issued invoices, sent quotes, and the client
 * portal; the PDF template mirrors this layout.
 */
export function DocumentSheet({
  title,
  number,
  status,
  seller,
  billTo,
  dates,
  lines,
  subtotal,
  tax,
  total,
  footerRows,
  notes,
  terms,
}: {
  title: string;
  number: string | null;
  status: string;
  seller: Party | null;
  billTo: Party | null;
  dates: { label: string; value: string | null }[];
  lines: Line[];
  subtotal: Money;
  tax: Money;
  total: Money;
  footerRows?: { label: string; value: string; strong?: boolean }[];
  notes?: string | null;
  terms?: string | null;
}) {
  return (
    <article className="paper relative overflow-hidden rounded-xl border bg-card text-card-foreground shadow-[0_20px_50px_-24px_oklch(0.2_0.08_277/0.35)]">
      <div className="h-1.5 bg-primary" />
      <div className="grid gap-8 p-6 sm:p-10">
        <header className="flex flex-wrap items-start justify-between gap-6">
          <div className="min-w-0">
            <PartyBlock party={seller} strong />
          </div>
          <div className="text-end">
            <p className="text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">{title}</p>
            <p className="figures mt-1 font-mono text-lg font-medium">{number ?? "Draft"}</p>
            <dl className="figures mt-2 grid grid-cols-[auto_auto] justify-end gap-x-3 gap-y-0.5 text-xs">
              {dates.map((d) => (
                <Row key={d.label} label={d.label} value={formatDate(d.value)} />
              ))}
            </dl>
            {status !== "DRAFT" && <StatusBadge status={status} className="mt-3" />}
          </div>
        </header>

        <section>
          <p className="text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">Bill to</p>
          <div className="mt-1.5">
            <PartyBlock party={billTo} strong />
          </div>
        </section>

        <div className="relative overflow-x-auto">
          <table className="figures w-full text-sm sm:min-w-[30rem]">
            <thead>
              <tr className="border-b text-[11px] uppercase tracking-[0.1em] text-muted-foreground">
                <th className="pb-2 text-start font-medium">Description</th>
                <th className="whitespace-nowrap pb-2 ps-3 text-end font-medium">Qty</th>
                <th className="whitespace-nowrap pb-2 ps-3 text-end font-medium max-sm:hidden">Unit price</th>
                <th className="whitespace-nowrap pb-2 ps-3 text-end font-medium max-sm:hidden">Tax</th>
                <th className="whitespace-nowrap pb-2 ps-3 text-end font-medium">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {lines.map((l) => (
                <tr key={l.id} className="align-top">
                  <td className="py-2.5 pe-4">
                    <span dir="auto" className="whitespace-pre-line">
                      {l.description}
                    </span>
                  </td>
                  <td className="py-2.5 ps-3 text-end whitespace-nowrap">
                    {formatAmount(l.quantity)}
                    {l.unit && <span className="text-muted-foreground"> {l.unit}</span>}
                  </td>
                  <td className="py-2.5 ps-3 text-end max-sm:hidden">{formatAmount(l.unitPrice.amount)}</td>
                  <td className="py-2.5 ps-3 text-end text-muted-foreground max-sm:hidden">{l.taxRate === "0" ? "—" : `${l.taxRate}%`}</td>
                  <td className="py-2.5 ps-3 text-end">{formatAmount(l.subtotal.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex justify-end">
          <dl className="figures grid min-w-64 grid-cols-[1fr_auto] gap-x-10 gap-y-1.5 text-sm">
            <Row label="Subtotal" value={formatAmount(subtotal.amount)} />
            <Row label="Tax" value={formatAmount(tax.amount)} />
            <dt className="border-t pt-2 text-base font-semibold">Total</dt>
            <dd className="border-t pt-2 text-end text-base font-semibold">{formatMoney(total)}</dd>
            {footerRows?.map((r) => (
              <Row key={r.label} label={r.label} value={r.value} strong={r.strong} />
            ))}
          </dl>
        </div>

        {(notes || terms) && (
          <footer className="grid gap-4 border-t pt-6 text-sm sm:grid-cols-2">
            {notes && <TextBlock title="Notes">{notes}</TextBlock>}
            {terms && <TextBlock title="Payment terms">{terms}</TextBlock>}
          </footer>
        )}
      </div>
    </article>
  );
}

function PartyBlock({ party, strong }: { party: Party | null; strong?: boolean }) {
  if (!party) return <p className="text-sm text-muted-foreground">—</p>;
  const address = [party.addressLine1, party.addressLine2, [party.postalCode, party.city].filter(Boolean).join(" "), party.region, countryName(party.country)]
    .filter(Boolean)
    .join(", ");
  return (
    <div className="text-sm">
      <p dir="auto" className={strong ? "font-semibold" : undefined}>
        {party.name}
      </p>
      {address && <p className="text-muted-foreground">{address}</p>}
      {party.taxId && <p className="figures text-muted-foreground">Tax no. {party.taxId}</p>}
      {party.email && <p className="text-muted-foreground">{party.email}</p>}
    </div>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <>
      <dt className={strong ? "font-semibold" : "text-muted-foreground"}>{label}</dt>
      <dd className={strong ? "text-end font-semibold" : "text-end"}>{value}</dd>
    </>
  );
}

function TextBlock({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <p className="text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">{title}</p>
      <p dir="auto" className="mt-1 whitespace-pre-line">
        {children}
      </p>
    </div>
  );
}
