import { Plus, ReceiptText, Search } from "lucide-react";
import Form from "next/form";
import Link from "next/link";
import { Suspense } from "react";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { StatusTabs } from "@/components/status-tabs";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getInvoices } from "@/lib/api";
import { formatAmount, formatDate } from "@/lib/format";

export const metadata = { title: "Invoices" };

type Search = { status?: string; q?: string; kind?: string };

const TABS = [
  { label: "All" },
  { value: "DRAFT", label: "Drafts" },
  { value: "ISSUED", label: "Issued" },
  { value: "PARTIALLY_PAID", label: "Partly paid" },
  { value: "OVERDUE", label: "Overdue" },
  { value: "PAID", label: "Paid" },
  { value: "VOID", label: "Void" },
];

export default function InvoicesPage({ searchParams }: PageProps<"/invoices">) {
  return (
    <>
      <PageHeader
        title="Invoices"
        description="Drafts can be edited freely. Once issued, an invoice is numbered and locked: corrections are a void or a credit note."
        actions={
          <Button asChild>
            <Link href="/invoices/new">
              <Plus /> New invoice
            </Link>
          </Button>
        }
      />
      <Suspense fallback={<Skeleton className="h-80 w-full" />}>
        <InvoiceList searchParams={searchParams as Promise<Search>} />
      </Suspense>
    </>
  );
}

async function InvoiceList({ searchParams }: { searchParams: Promise<Search> }) {
  const { status, q, kind } = await searchParams;
  const creditNotes = kind === "CREDIT_NOTE";
  const page = await getInvoices({ status, q, kind: creditNotes ? "CREDIT_NOTE" : undefined, size: 100 });

  if (page.total === 0 && !status && !q && !creditNotes) {
    return (
      <EmptyState
        icon={ReceiptText}
        title="No invoices yet"
        action={
          <Button asChild>
            <Link href="/invoices/new">Write your first invoice</Link>
          </Button>
        }
      >
        Start from a product list or type lines by hand. Totals and tax are calculated as you type, exactly to the
        fils, and nothing is numbered until you issue it.
      </EmptyState>
    );
  }

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        {creditNotes ? (
          <p className="mb-4 text-sm text-muted-foreground">
            Showing credit notes ·{" "}
            <Link href="/invoices" className="text-primary hover:underline">
              back to invoices
            </Link>
          </p>
        ) : (
          <StatusTabs base="/invoices" current={status} tabs={TABS} q={q} />
        )}
        <Form action="/invoices" className="mb-4 flex items-center gap-2">
          {status && <input type="hidden" name="status" value={status} />}
          <div className="relative w-64">
            <Search className="pointer-events-none absolute start-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input name="q" type="search" defaultValue={q} placeholder="Number or client" className="h-9 ps-8" aria-label="Search invoices" />
          </div>
        </Form>
      </div>
      <div className="overflow-hidden rounded-xl border bg-card shadow-xs">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/50 hover:bg-muted/50">
              <TableHead className="ps-5">Number</TableHead>
              <TableHead>Client</TableHead>
              <TableHead className="hidden md:table-cell">Issued</TableHead>
              <TableHead className="hidden md:table-cell">Due</TableHead>
              <TableHead className="text-end">Total</TableHead>
              <TableHead className="hidden text-end sm:table-cell">Balance</TableHead>
              <TableHead className="pe-5 text-end">Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {page.items.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} className="py-10 text-center text-muted-foreground">
                  Nothing here{q ? ` for “${q}”` : ""}.
                </TableCell>
              </TableRow>
            )}
            {page.items.map((inv) => (
              <TableRow key={inv.id} data-testid="invoice-row">
                <TableCell className="figures py-3 ps-5 font-mono">
                  <Link href={`/invoices/${inv.id}`} className="font-medium hover:underline">
                    {inv.number ?? <span className="font-sans text-muted-foreground">Draft</span>}
                  </Link>
                </TableCell>
                <TableCell className="max-w-56 truncate" dir="auto">
                  {inv.client.name}
                </TableCell>
                <TableCell className="figures hidden text-muted-foreground md:table-cell">{formatDate(inv.issueDate)}</TableCell>
                <TableCell className="figures hidden text-muted-foreground md:table-cell">{formatDate(inv.dueDate)}</TableCell>
                <TableCell className="figures text-end whitespace-nowrap">
                  <span className="text-muted-foreground">{inv.currency}</span> {formatAmount(inv.total.amount)}
                </TableCell>
                <TableCell className="figures hidden text-end whitespace-nowrap sm:table-cell">
                  {inv.status === "VOID" || inv.status === "DRAFT" ? "—" : formatAmount(inv.balanceDue.amount)}
                </TableCell>
                <TableCell className="pe-5 text-end">
                  <StatusBadge status={inv.status} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <p className="figures mt-3 text-xs text-muted-foreground">
        {page.total} {creditNotes ? "credit notes" : page.total === 1 ? "invoice" : "invoices"}
        {!creditNotes && (
          <>
            {" · "}
            <Link href="/invoices?kind=CREDIT_NOTE" className="hover:text-foreground hover:underline">
              Credit notes
            </Link>
          </>
        )}
      </p>
    </>
  );
}
