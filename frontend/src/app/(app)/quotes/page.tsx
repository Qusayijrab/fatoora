import { FileText, Plus } from "lucide-react";
import Link from "next/link";
import { Suspense } from "react";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { StatusTabs } from "@/components/status-tabs";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getQuotes } from "@/lib/api";
import { formatAmount, formatDate } from "@/lib/format";

export const metadata = { title: "Quotes" };

const TABS = [
  { label: "All" },
  { value: "DRAFT", label: "Drafts" },
  { value: "SENT", label: "Sent" },
  { value: "ACCEPTED", label: "Accepted" },
  { value: "REJECTED", label: "Rejected" },
  { value: "EXPIRED", label: "Expired" },
];

export default function QuotesPage({ searchParams }: PageProps<"/quotes">) {
  return (
    <>
      <PageHeader
        title="Quotes"
        description="Agree the price before the work. An accepted quote becomes an invoice draft in one click."
        actions={
          <Button asChild>
            <Link href="/quotes/new">
              <Plus /> New quote
            </Link>
          </Button>
        }
      />
      <Suspense fallback={<Skeleton className="h-80 w-full" />}>
        <QuoteList searchParams={searchParams as Promise<{ status?: string }>} />
      </Suspense>
    </>
  );
}

async function QuoteList({ searchParams }: { searchParams: Promise<{ status?: string }> }) {
  const { status } = await searchParams;
  const page = await getQuotes({ status, size: 100 });

  if (page.total === 0 && !status) {
    return (
      <EmptyState
        icon={FileText}
        title="No quotes yet"
        action={
          <Button asChild>
            <Link href="/quotes/new">Write a quote</Link>
          </Button>
        }
      >
        Send a price before you start. When the client accepts, turn the quote into an invoice without retyping a line.
      </EmptyState>
    );
  }

  return (
    <>
      <StatusTabs base="/quotes" current={status} tabs={TABS} />
      <div className="overflow-hidden rounded-xl border bg-card shadow-xs">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/50 hover:bg-muted/50">
              <TableHead className="ps-5">Number</TableHead>
              <TableHead>Client</TableHead>
              <TableHead className="hidden md:table-cell">Date</TableHead>
              <TableHead className="hidden md:table-cell">Valid until</TableHead>
              <TableHead className="text-end">Total</TableHead>
              <TableHead className="pe-5 text-end">Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {page.items.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
                  No quotes with this status.
                </TableCell>
              </TableRow>
            )}
            {page.items.map((q) => (
              <TableRow key={q.id} data-testid="quote-row">
                <TableCell className="figures py-3 ps-5 font-mono">
                  <Link href={`/quotes/${q.id}`} className="font-medium hover:underline">
                    {q.number ?? <span className="font-sans text-muted-foreground">Draft</span>}
                  </Link>
                </TableCell>
                <TableCell className="max-w-56 truncate" dir="auto">
                  {q.client.name}
                </TableCell>
                <TableCell className="figures hidden text-muted-foreground md:table-cell">{formatDate(q.issueDate)}</TableCell>
                <TableCell className="figures hidden text-muted-foreground md:table-cell">{formatDate(q.validUntil)}</TableCell>
                <TableCell className="figures text-end whitespace-nowrap">
                  <span className="text-muted-foreground">{q.currency}</span> {formatAmount(q.total.amount)}
                </TableCell>
                <TableCell className="pe-5 text-end">
                  <StatusBadge status={q.convertedInvoiceId ? "CONVERTED" : q.status} label={q.convertedInvoiceId ? "Invoiced" : undefined} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </>
  );
}
