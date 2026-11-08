import { Download } from "lucide-react";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { getAgingReport, getTaxReport } from "@/lib/api";
import { formatAmount, formatDate } from "@/lib/format";

export const metadata = { title: "Reports" };

const ISO = /^\d{4}-\d{2}-\d{2}$/;

export default function ReportsPage({ searchParams }: PageProps<"/reports">) {
  return (
    <div className="grid gap-8">
      <PageHeader
        title="Reports"
        description="For you and your accountant. Amounts are in your base currency, at the rate frozen on each invoice."
      />
      <Suspense fallback={<Skeleton className="h-64 w-full" />}>
        <Aging />
      </Suspense>
      <Suspense fallback={<Skeleton className="h-64 w-full" />}>
        <Tax searchParams={searchParams as Promise<{ from?: string; to?: string }>} />
      </Suspense>
    </div>
  );
}

const th = "py-2 text-end font-medium";
const td = "figures py-2 text-end";

async function Aging() {
  const report = await getAgingReport();
  const t = report.totals;
  return (
    <section className="rounded-xl border bg-card p-5 shadow-xs sm:p-6" aria-labelledby="aging-title" data-testid="aging-report">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h2 id="aging-title" className="text-base font-semibold">
            Aging: who owes what, and how late
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Open invoices as of {formatDate(report.asOf)}, by days past the due date ({report.baseCurrency}).
          </p>
        </div>
        <Button asChild variant="outline" size="sm">
          <a href="/reports/export/aging" download>
            <Download /> CSV
          </a>
        </Button>
      </div>
      {report.rows.length === 0 ? (
        <p className="mt-4 text-sm text-muted-foreground">Nothing is owed right now.</p>
      ) : (
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[40rem] text-sm">
            <thead className="border-b text-xs text-muted-foreground">
              <tr>
                <th className="py-2 text-start font-medium">Client</th>
                <th className={th}>Not yet due</th>
                <th className={th}>1–30</th>
                <th className={th}>31–60</th>
                <th className={th}>61–90</th>
                <th className={th}>Over 90</th>
                <th className={th}>Total</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {report.rows.map((r) => (
                <tr key={r.clientId}>
                  <td className="py-2" dir="auto">
                    {r.client}
                  </td>
                  <td className={td}>{formatAmount(r.notDue.amount)}</td>
                  <td className={td}>{formatAmount(r.days1to30.amount)}</td>
                  <td className={td}>{formatAmount(r.days31to60.amount)}</td>
                  <td className={td}>{formatAmount(r.days61to90.amount)}</td>
                  <td className={`${td} ${r.over90.amountMinor > 0 ? "text-ink-overdue" : ""}`}>{formatAmount(r.over90.amount)}</td>
                  <td className={`${td} font-medium`}>{formatAmount(r.total.amount)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="border-t-2 font-semibold">
              <tr>
                <td className="py-2">Total</td>
                <td className={td}>{formatAmount(t.notDue.amount)}</td>
                <td className={td}>{formatAmount(t.days1to30.amount)}</td>
                <td className={td}>{formatAmount(t.days31to60.amount)}</td>
                <td className={td}>{formatAmount(t.days61to90.amount)}</td>
                <td className={td}>{formatAmount(t.over90.amount)}</td>
                <td className={td}>{formatAmount(t.total.amount)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </section>
  );
}

async function Tax({ searchParams }: { searchParams: Promise<{ from?: string; to?: string }> }) {
  const { from, to } = await searchParams;
  const report = await getTaxReport(from && ISO.test(from) ? from : undefined, to && ISO.test(to) ? to : undefined);
  const csv = `/reports/export/tax?from=${report.from}&to=${report.to}`;
  return (
    <section className="rounded-xl border bg-card p-5 shadow-xs sm:p-6" aria-labelledby="tax-title" data-testid="tax-report">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h2 id="tax-title" className="text-base font-semibold">
            Tax summary
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {report.invoices} {report.invoices === 1 ? "invoice" : "invoices"} and {report.creditNotes}{" "}
            {report.creditNotes === 1 ? "credit note" : "credit notes"} issued {formatDate(report.from)} to {formatDate(report.to)}.
            Credit notes are subtracted.
          </p>
        </div>
        <Button asChild variant="outline" size="sm">
          <a href={csv} download>
            <Download /> CSV
          </a>
        </Button>
      </div>
      <form className="mt-4 flex flex-wrap items-end gap-3" action="/reports">
        <div className="grid gap-1.5">
          <Label htmlFor="tax-from">From</Label>
          <Input id="tax-from" name="from" type="date" className="figures" defaultValue={report.from} />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="tax-to">To</Label>
          <Input id="tax-to" name="to" type="date" className="figures" defaultValue={report.to} />
        </div>
        <Button type="submit" variant="outline">
          Show
        </Button>
      </form>
      {report.rows.length === 0 ? (
        <p className="mt-4 text-sm text-muted-foreground">Nothing was issued in this period.</p>
      ) : (
        <table className="mt-4 w-full text-sm">
          <thead className="border-b text-xs text-muted-foreground">
            <tr>
              <th className="py-2 text-start font-medium">Rate</th>
              <th className={th}>Taxable ({report.baseCurrency})</th>
              <th className={th}>Tax ({report.baseCurrency})</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {report.rows.map((r) => (
              <tr key={r.rate}>
                <td className="py-2">
                  {r.name}
                  {!r.name.includes(`${r.rate}%`) && <span className="figures ms-1.5 text-muted-foreground">{r.rate}%</span>}
                </td>
                <td className={td}>{formatAmount(r.taxable.amount)}</td>
                <td className={td}>{formatAmount(r.tax.amount)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot className="border-t-2 font-semibold">
            <tr>
              <td className="py-2">Total</td>
              <td className={td}>{formatAmount(report.taxable.amount)}</td>
              <td className={td}>{formatAmount(report.tax.amount)}</td>
            </tr>
          </tfoot>
        </table>
      )}
    </section>
  );
}
