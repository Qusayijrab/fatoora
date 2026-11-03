import { FileDown } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { StatusStamp } from "@/components/status-stamp";
import { Skeleton } from "@/components/ui/skeleton";
import { api, ApiError } from "@/lib/api";
import { formatAmount, formatDate, formatMoney } from "@/lib/format";
import type { PortalView } from "@/lib/types";
import { QuoteAnswer } from "./quote-answer";

export const metadata: Metadata = { title: "Your documents", robots: { index: false, follow: false } };

export default function PortalPage({ params }: PageProps<"/portal/[token]">) {
  return (
    <Suspense fallback={<Skeleton className="h-[30rem] w-full" />}>
      <Portal params={params} />
    </Suspense>
  );
}

async function Portal({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  let view: PortalView;
  try {
    view = await api<PortalView>(`/public/portal/${encodeURIComponent(token)}`, { auth: false });
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      return (
        <div className="rounded-lg border bg-card p-8 text-center">
          <h1 className="text-xl font-semibold">This link is not valid</h1>
          <p className="mt-2 text-sm text-muted-foreground">It may have been replaced. Ask the business for a new one.</p>
        </div>
      );
    }
    throw e;
  }

  const invoices = view.invoices.filter((i) => i.kind === "INVOICE");
  const credits = view.invoices.filter((i) => i.kind === "CREDIT_NOTE");

  return (
    <div className="grid gap-6">
      <header className="overflow-hidden rounded-lg border bg-card">
        <div className="bg-cedar px-6 py-6 text-cedar-foreground sm:px-8">
          <p className="text-sm opacity-80" dir="auto">
            {view.organizationName}
          </p>
          <h1 className="mt-1 text-xl font-semibold" dir="auto">
            Documents for {view.clientName}
          </h1>
        </div>
        <div className="h-1 bg-saffron" />
        <div className="flex flex-wrap items-end justify-between gap-4 px-6 py-5 sm:px-8">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.1em] text-muted-foreground">You owe</p>
            <p className="figures mt-1 text-2xl font-semibold" data-testid="portal-owed">
              {view.outstanding.length === 0 ? "Nothing. Thank you!" : view.outstanding.map(formatMoney).join(" + ")}
            </p>
          </div>
          {view.organizationEmail && (
            <p className="text-sm text-muted-foreground">
              Questions? <span className="text-foreground">{view.organizationEmail}</span>
            </p>
          )}
        </div>
      </header>

      <section className="rounded-lg border bg-card" aria-labelledby="inv-title">
        <h2 id="inv-title" className="border-b px-6 py-4 text-base font-semibold sm:px-8">
          Invoices
        </h2>
        {invoices.length === 0 ? (
          <p className="px-6 py-6 text-sm text-muted-foreground sm:px-8">No invoices yet.</p>
        ) : (
          <ul className="divide-y">
            {invoices.map((inv) => (
              <li key={inv.id} className="flex flex-wrap items-center gap-x-6 gap-y-2 px-6 py-4 sm:px-8" data-testid="portal-invoice">
                <div className="min-w-40 flex-1">
                  <p className="figures font-mono font-medium">{inv.number}</p>
                  <p className="figures text-xs text-muted-foreground">
                    Issued {formatDate(inv.issueDate)}
                    {inv.dueDate && ` · due ${formatDate(inv.dueDate)}`}
                  </p>
                </div>
                <StatusStamp status={inv.status} />
                <p className="figures w-36 text-end">
                  {formatMoney(inv.total)}
                  {inv.payUrl && inv.balanceDue.amount !== inv.total.amount && (
                    <span className="block text-xs text-muted-foreground">{formatAmount(inv.balanceDue.amount)} left</span>
                  )}
                </p>
                <div className="flex gap-2">
                  <a
                    href={`/portal/${token}/invoices/${inv.id}/pdf`}
                    target="_blank"
                    rel="noopener"
                    className="inline-flex h-8 items-center gap-1.5 rounded-md border px-3 text-sm hover:bg-muted"
                    aria-label={`Download ${inv.number}`}
                  >
                    <FileDown className="size-4" /> PDF
                  </a>
                  {inv.payUrl && (
                    <Link
                      href={new URL(inv.payUrl).pathname}
                      className="inline-flex h-8 items-center rounded-md bg-saffron px-3 text-sm font-medium text-saffron-foreground hover:bg-saffron/90"
                    >
                      Pay
                    </Link>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {view.quotes.length > 0 && (
        <section className="rounded-lg border bg-card" aria-labelledby="quo-title">
          <h2 id="quo-title" className="border-b px-6 py-4 text-base font-semibold sm:px-8">
            Quotes
          </h2>
          <ul className="divide-y">
            {view.quotes.map((q) => (
              <li key={q.id} className="flex flex-wrap items-center gap-x-6 gap-y-2 px-6 py-4 sm:px-8" data-testid="portal-quote">
                <div className="min-w-40 flex-1">
                  <p className="figures font-mono font-medium">{q.number}</p>
                  <p className="figures text-xs text-muted-foreground">
                    {formatDate(q.issueDate)}
                    {q.validUntil && ` · valid until ${formatDate(q.validUntil)}`}
                  </p>
                </div>
                <StatusStamp status={q.status} />
                <p className="figures w-36 text-end">{formatMoney(q.total)}</p>
                <div className="flex gap-2">
                  <a
                    href={`/portal/${token}/quotes/${q.id}/pdf`}
                    target="_blank"
                    rel="noopener"
                    className="inline-flex h-8 items-center gap-1.5 rounded-md border px-3 text-sm hover:bg-muted"
                    aria-label={`Download ${q.number}`}
                  >
                    <FileDown className="size-4" /> PDF
                  </a>
                  {q.status === "SENT" && <QuoteAnswer token={token} quoteId={q.id} number={q.number} />}
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {credits.length > 0 && (
        <section className="rounded-lg border bg-card px-6 py-4 text-sm sm:px-8">
          <h2 className="font-semibold">Credit notes</h2>
          <ul className="mt-2 grid gap-1">
            {credits.map((c) => (
              <li key={c.id} className="figures flex justify-between gap-4">
                <a href={`/portal/${token}/invoices/${c.id}/pdf`} target="_blank" rel="noopener" className="font-mono hover:underline">
                  {c.number}
                </a>
                <span>− {formatMoney(c.total)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
