import { Plus, ReceiptText, Search } from "lucide-react";
import Form from "next/form";
import Link from "next/link";
import { Suspense } from "react";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { StatusTabs } from "@/components/status-tabs";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { getInvoices } from "@/lib/api";
import { InvoiceTable } from "./invoice-table";

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
      <InvoiceTable
        invoices={page.items}
        toolbar={<InvoiceToolbar status={status} q={q} creditNotes={creditNotes} />}
        empty={<p className="py-10 text-center text-sm text-muted-foreground">Nothing here{q ? ` for “${q}”` : ""}.</p>}
      />
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

/** Status tabs (or the credit-notes notice) and the server-side search, shown above the table. */
function InvoiceToolbar({ status, q, creditNotes }: { status?: string; q?: string; creditNotes: boolean }) {
  return (
    <div className="flex min-w-0 flex-1 flex-wrap items-center justify-between gap-3 *:mb-0">
      {creditNotes ? (
        <p className="text-sm text-muted-foreground">
          Showing credit notes ·{" "}
          <Link href="/invoices" className="text-primary hover:underline">
            back to invoices
          </Link>
        </p>
      ) : (
        <StatusTabs base="/invoices" current={status} tabs={TABS} q={q} />
      )}
      <Form action="/invoices" className="flex w-full items-center gap-2 sm:w-auto">
        {status && <input type="hidden" name="status" value={status} />}
        <div className="relative w-full sm:w-64">
          <Search className="pointer-events-none absolute start-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input name="q" type="search" defaultValue={q} placeholder="Number or client" className="h-9 ps-8" aria-label="Search invoices" />
        </div>
      </Form>
    </div>
  );
}
