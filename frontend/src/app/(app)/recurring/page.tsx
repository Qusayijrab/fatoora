import { Plus, Repeat } from "lucide-react";
import Link from "next/link";
import { Suspense } from "react";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getRecurringList } from "@/lib/api";
import { formatDate, formatMoney } from "@/lib/format";
import { describeFrequency } from "@/lib/types";

export const metadata = { title: "Recurring invoices" };

export default function RecurringPage() {
  return (
    <>
      <PageHeader
        title="Recurring invoices"
        description="Rent, retainers and subscriptions that bill themselves on schedule, in your time zone. Each period is generated exactly once."
        actions={
          <Button asChild>
            <Link href="/recurring/new">
              <Plus /> New recurring invoice
            </Link>
          </Button>
        }
      />
      <Suspense fallback={<Skeleton className="h-64 w-full" />}>
        <RecurringList />
      </Suspense>
    </>
  );
}

async function RecurringList() {
  const list = await getRecurringList();
  if (list.length === 0) {
    return (
      <EmptyState
        icon={Repeat}
        title="Nothing repeats yet"
        action={
          <Button asChild>
            <Link href="/recurring/new">Set one up</Link>
          </Button>
        }
      >
        Write the invoice once, pick how often it repeats, and Fatoora issues it on schedule. It can email the client
        too.
      </EmptyState>
    );
  }
  return (
    <div className="overflow-hidden rounded-xl border bg-card shadow-xs">
      <Table>
        <TableHeader>
          <TableRow className="bg-muted/50 hover:bg-muted/50">
            <TableHead className="ps-5">Name</TableHead>
            <TableHead className="hidden md:table-cell">Repeats</TableHead>
            <TableHead>Next invoice</TableHead>
            <TableHead className="text-end">Amount</TableHead>
            <TableHead className="pe-5 text-end">Status</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {list.map((r) => (
            <TableRow key={r.id} data-testid="recurring-row">
              <TableCell className="py-3 ps-5">
                <Link href={`/recurring/${r.id}`} className="font-medium hover:underline">
                  {r.name}
                </Link>
                <p className="text-xs text-muted-foreground" dir="auto">
                  {r.client.name}
                </p>
              </TableCell>
              <TableCell className="hidden text-muted-foreground md:table-cell">{describeFrequency(r.frequency, r.intervalCount)}</TableCell>
              <TableCell className="figures">{r.nextRunDate ? formatDate(r.nextRunDate) : "—"}</TableCell>
              <TableCell className="figures text-end whitespace-nowrap">{formatMoney(r.total)}</TableCell>
              <TableCell className="pe-5 text-end">
                <StatusBadge status={r.status} />
                {r.pausedByFailure && (
                  <span className="mt-1 block text-xs text-ink-overdue" title={r.lastError ?? undefined}>
                    Failed {r.failureCount}×
                  </span>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
