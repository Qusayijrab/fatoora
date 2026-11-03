import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Skeleton } from "@/components/ui/skeleton";
import { getEditorData, getRecurring, getRecurringRuns } from "@/lib/api";
import { formatDate, formatMoney } from "@/lib/format";
import { describeFrequency } from "@/lib/types";
import { RecurringForm } from "../recurring-form";
import { RecurringControls } from "./recurring-controls";

export default function RecurringDetailPage({ params }: PageProps<"/recurring/[id]">) {
  return (
    <div className="max-w-6xl">
      <Link href="/recurring" className="text-sm text-muted-foreground hover:text-foreground">
        ← Recurring invoices
      </Link>
      <Suspense fallback={<Skeleton className="mt-4 h-[30rem] w-full" />}>
        <Detail params={params} />
      </Suspense>
    </div>
  );
}

async function Detail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [recurring, runs, data] = await Promise.all([getRecurring(id), getRecurringRuns(id), getEditorData()]);

  return (
    <>
      <div className="mt-3">
        <PageHeader
          title={
            <span className="inline-flex flex-wrap items-center gap-3">
              {recurring.name}
              <StatusBadge status={recurring.status} />
            </span>
          }
          description={
            <>
              {describeFrequency(recurring.frequency, recurring.intervalCount)} for <span dir="auto">{recurring.client.name}</span> ·{" "}
              {formatMoney(recurring.total)}
            </>
          }
        />
      </div>
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_19rem]">
        <div className="rounded-xl border bg-card shadow-xs p-5 sm:p-7">
          {recurring.status === "ENDED" ? (
            <p className="text-sm text-muted-foreground">This schedule has ended. Its invoices are listed on the right.</p>
          ) : (
            <RecurringForm data={data} recurring={recurring} key={recurring.id} />
          )}
        </div>
        <aside className="grid gap-4 lg:sticky lg:top-6">
          <section className="rounded-xl border bg-card shadow-xs p-5">
            <p className="text-xs font-medium uppercase tracking-[0.1em] text-muted-foreground">Next invoice</p>
            <p className="figures mt-1 text-2xl font-semibold" data-testid="next-run">
              {recurring.nextRunDate ? formatDate(recurring.nextRunDate) : "—"}
            </p>
            <p className="mt-1 text-sm text-muted-foreground">
              {recurring.runCount} generated so far
              {recurring.endDate && <> · ends {formatDate(recurring.endDate)}</>}
            </p>
            <RecurringControls id={recurring.id} status={recurring.status} name={recurring.name} />
          </section>
          <section className="rounded-xl border bg-card shadow-xs p-5">
            <h2 className="text-sm font-semibold">Generated invoices</h2>
            {runs.length === 0 ? (
              <p className="mt-2 text-sm text-muted-foreground">None yet.</p>
            ) : (
              <ul className="mt-3 divide-y text-sm">
                {runs.map((run) => (
                  <li key={run.periodDate} className="flex items-center justify-between gap-3 py-2" data-testid="run-row">
                    <span className="figures text-muted-foreground">{formatDate(run.periodDate)}</span>
                    {run.invoiceId ? (
                      <Link href={`/invoices/${run.invoiceId}`} className="figures inline-flex items-center gap-2 font-mono hover:underline">
                        {run.invoiceNumber ?? "Draft"}
                        {run.invoiceStatus && <StatusBadge status={run.invoiceStatus} />}
                      </Link>
                    ) : (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </aside>
      </div>
    </>
  );
}
