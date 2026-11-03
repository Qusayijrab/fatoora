import { CircleCheck, FileDown, TriangleAlert } from "lucide-react";
import type { Metadata } from "next";
import { Suspense } from "react";
import { StatusBadge } from "@/components/status-badge";
import { Skeleton } from "@/components/ui/skeleton";
import { api, ApiError } from "@/lib/api";
import { formatAmount, formatDate, formatMoney } from "@/lib/format";
import type { PublicInvoice } from "@/lib/types";
import { PayButton } from "./pay-button";

export const metadata: Metadata = { title: "Pay invoice", robots: { index: false, follow: false } };

export default function PayPage({ params, searchParams }: PageProps<"/pay/[token]">) {
  return (
    <Suspense fallback={<Skeleton className="h-[30rem] w-full" />}>
      <Pay params={params} searchParams={searchParams as Promise<{ status?: string }>} />
    </Suspense>
  );
}

async function Pay({ params, searchParams }: { params: Promise<{ token: string }>; searchParams: Promise<{ status?: string }> }) {
  const [{ token }, { status }] = await Promise.all([params, searchParams]);
  let invoice: PublicInvoice;
  try {
    invoice = await api<PublicInvoice>(`/public/pay/${encodeURIComponent(token)}`, { auth: false });
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      return (
        <div className="rounded-xl border bg-card shadow-xs p-8 text-center">
          <h1 className="text-xl font-semibold">This link is not valid</h1>
          <p className="mt-2 text-sm text-muted-foreground">Ask the business that sent it for a new one.</p>
        </div>
      );
    }
    throw e;
  }

  const paid = invoice.status === "PAID";
  return (
    <article className="overflow-hidden rounded-xl border bg-card shadow-xs shadow-[0_20px_50px_-24px_oklch(0.15_0.03_170/0.35)]">
      <header className="bg-brand px-6 py-6 text-brand-foreground sm:px-8">
        <p className="text-sm opacity-80" dir="auto">
          {invoice.organizationName}
        </p>
        <h1 className="mt-1 text-lg font-semibold">
          Invoice <span className="figures font-mono">{invoice.number}</span>
        </h1>
        <p className="mt-1 text-sm opacity-80">
          For <span dir="auto">{invoice.clientName}</span> · issued {formatDate(invoice.issueDate)}
          {invoice.dueDate && <> · due {formatDate(invoice.dueDate)}</>}
        </p>
      </header>
      <div className="h-1 bg-primary" />

      <div className="grid gap-6 p-6 sm:p-8">
        {status === "success" && (
          <p role="status" className="flex items-center gap-2 rounded-md bg-ink-paid/10 px-4 py-3 text-sm font-medium text-ink-paid">
            <CircleCheck className="size-4" /> Payment received. Thank you!
          </p>
        )}
        {status === "cancelled" && !paid && (
          <p role="status" className="flex items-center gap-2 rounded-md bg-muted px-4 py-3 text-sm">
            <TriangleAlert className="size-4" /> Payment was cancelled. You can try again below.
          </p>
        )}

        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.1em] text-muted-foreground">
              {paid ? "Paid in full" : "Amount due"}
            </p>
            <p className="figures mt-1 text-3xl font-semibold tracking-tight" data-testid="pay-amount">
              {paid ? formatMoney(invoice.total) : formatMoney(invoice.balanceDue)}
            </p>
          </div>
          <StatusBadge status={invoice.status} size="lg" />
        </div>

        {invoice.payable && <PayButton token={token} amount={formatMoney(invoice.balanceDue)} provider={invoice.provider} />}

        <table className="figures w-full text-sm">
          <tbody className="divide-y">
            {invoice.lines.map((l) => (
              <tr key={l.id}>
                <td className="py-2.5 pe-4" dir="auto">
                  {l.description}
                  <span className="block text-xs text-muted-foreground">
                    {formatAmount(l.quantity)} {l.unit ?? ""} × {formatAmount(l.unitPrice.amount)}
                    {l.taxRate !== "0" && ` · ${l.taxRate}% tax`}
                  </span>
                </td>
                <td className="py-2.5 text-end">{formatAmount(l.total.amount)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t">
              <td className="pt-3 font-semibold">Total</td>
              <td className="pt-3 text-end font-semibold">{formatMoney(invoice.total)}</td>
            </tr>
            {invoice.amountPaid.amountMinor > 0 && (
              <tr>
                <td className="pt-1 text-muted-foreground">Already paid</td>
                <td className="pt-1 text-end text-muted-foreground">− {formatAmount(invoice.amountPaid.amount)}</td>
              </tr>
            )}
          </tfoot>
        </table>

        <a
          href={`/pay/${token}/pdf`}
          target="_blank"
          rel="noopener"
          className="inline-flex items-center gap-2 text-sm font-medium text-primary underline-offset-4 hover:underline"
        >
          <FileDown className="size-4" /> Download the invoice (PDF)
        </a>
      </div>
    </article>
  );
}
