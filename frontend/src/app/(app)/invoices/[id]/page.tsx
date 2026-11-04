import Link from "next/link";
import { Suspense } from "react";
import { DocumentDelivery } from "@/components/document-delivery";
import { DocumentSheet } from "@/components/document-sheet";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Skeleton } from "@/components/ui/skeleton";
import { History } from "@/components/history";
import { getAudit, getEditorData, getInvoice, getMe } from "@/lib/api";
import { formatAmount, formatDate, formatMoney } from "@/lib/format";
import { canManageMembers } from "@/lib/types";
import { DraftInvoiceEditor } from "./draft-editor";
import { InvoicePanel } from "./invoice-panel";

export default function InvoicePage({ params }: PageProps<"/invoices/[id]">) {
  return (
    <div className="max-w-6xl">
      <Link href="/invoices" className="text-sm text-muted-foreground hover:text-foreground">
        ← Invoices
      </Link>
      <Suspense fallback={<Skeleton className="mt-4 h-[32rem] w-full" />}>
        <InvoiceDetail params={params} />
      </Suspense>
    </div>
  );
}

async function InvoiceDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [invoice, me, history] = await Promise.all([getInvoice(id), getMe(), getAudit({ entityId: id, size: 30 })]);
  const isAdmin = canManageMembers(me.organization?.role);
  const isCreditNote = invoice.kind === "CREDIT_NOTE";

  if (invoice.status === "DRAFT") {
    const data = await getEditorData();
    return (
      <>
        <div className="mt-3">
          <PageHeader
            title={
              <span className="inline-flex items-center gap-3">
                Draft invoice <StatusBadge status="DRAFT" />
              </span>
            }
            description={
              <>
                For <span dir="auto">{invoice.client.name}</span>
                {invoice.client.name.endsWith(".") ? " " : ". "}Edit freely; issuing gives it the next number and locks it.
              </>
            }
          />
        </div>
        <div className="rounded-xl border bg-card shadow-xs p-5 sm:p-7">
          <DraftInvoiceEditor invoice={invoice} data={data} timeZone={me.organization?.timeZone ?? "UTC"} />
        </div>
      </>
    );
  }

  return (
    <>
      <div className="mt-3">
        <PageHeader
          title={
            <span className="figures inline-flex flex-wrap items-center gap-3 font-mono">
              {invoice.number}
              <StatusBadge status={invoice.status} />
            </span>
          }
          description={
            <>
              {isCreditNote ? "Credit note" : "Invoice"} for <span dir="auto">{invoice.client.name}</span> · issued{" "}
              {formatDate(invoice.issueDate)}
              {invoice.creditedInvoice && (
                <>
                  {" · against "}
                  <Link href={`/invoices/${invoice.creditedInvoice.id}`} className="font-mono text-primary hover:underline">
                    {invoice.creditedInvoice.number}
                  </Link>
                </>
              )}
            </>
          }
        />
      </div>
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <DocumentSheet
          title={isCreditNote ? "Credit note" : "Invoice"}
          number={invoice.number}
          status={invoice.status}
          seller={invoice.seller}
          billTo={invoice.billTo}
          dates={[
            { label: "Issued", value: invoice.issueDate },
            ...(invoice.dueDate ? [{ label: "Due", value: invoice.dueDate }] : []),
          ]}
          lines={invoice.lines}
          subtotal={invoice.subtotal}
          tax={invoice.tax}
          total={invoice.total}
          footerRows={
            isCreditNote
              ? []
              : [
                  ...(invoice.amountPaid.amountMinor > 0 ? [{ label: "Paid", value: `− ${formatAmount(invoice.amountPaid.amount)}` }] : []),
                  ...(invoice.amountCredited.amountMinor > 0
                    ? [{ label: "Credited", value: `− ${formatAmount(invoice.amountCredited.amount)}` }]
                    : []),
                  { label: "Balance due", value: formatMoney(invoice.balanceDue), strong: true },
                ]
          }
          notes={invoice.notes}
          terms={invoice.terms}
        />
        <InvoicePanel
          invoice={invoice}
          isAdmin={isAdmin}
          extra={
            <DocumentDelivery
              kind="invoices"
              id={invoice.id}
              number={invoice.number}
              clientEmail={invoice.billTo?.email ?? null}
              canEmail={invoice.status !== "VOID"}
            />
          }
          history={<History entries={history.items} />}
        />
      </div>
    </>
  );
}
