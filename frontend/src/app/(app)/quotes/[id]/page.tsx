import Link from "next/link";
import { Suspense } from "react";
import { DocumentDelivery } from "@/components/document-delivery";
import { DocumentSheet } from "@/components/document-sheet";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Skeleton } from "@/components/ui/skeleton";
import { getCurrentOrganization, getEditorData, getQuote } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { QuoteDraftEditor, QuotePanel } from "./quote-client";

export default function QuotePage({ params }: PageProps<"/quotes/[id]">) {
  return (
    <div className="max-w-6xl">
      <Link href="/quotes" className="text-sm text-muted-foreground hover:text-foreground">
        ← Quotes
      </Link>
      <Suspense fallback={<Skeleton className="mt-4 h-[32rem] w-full" />}>
        <QuoteDetail params={params} />
      </Suspense>
    </div>
  );
}

async function QuoteDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const quote = await getQuote(id);

  if (quote.status === "DRAFT") {
    const data = await getEditorData();
    return (
      <>
        <div className="mt-3">
          <PageHeader
            title={
              <span className="inline-flex items-center gap-3">
                Draft quote <StatusBadge status="DRAFT" />
              </span>
            }
            description={
              <>
                For <span dir="auto">{quote.client.name}</span>
                {quote.client.name.endsWith(".") ? " " : ". "}Sending gives it a number and locks the prices.
              </>
            }
          />
        </div>
        <div className="rounded-xl border bg-card shadow-xs p-5 sm:p-7">
          <QuoteDraftEditor quote={quote} data={data} />
        </div>
      </>
    );
  }

  // Quotes carry no seller snapshot; show the business as it is today.
  const org = await getCurrentOrganization();
  const seller = {
    name: org.legalName || org.name,
    email: org.email,
    phone: org.phone,
    taxId: org.taxId,
    addressLine1: org.addressLine1,
    addressLine2: org.addressLine2,
    city: org.city,
    region: null,
    postalCode: null,
    country: org.country,
  };

  return (
    <>
      <div className="mt-3">
        <PageHeader
          title={
            <span className="figures inline-flex flex-wrap items-center gap-3 font-mono">
              {quote.number}
              <StatusBadge status={quote.status} />
            </span>
          }
          description={
            <>
              Quote for <span dir="auto">{quote.client.name}</span> · sent {formatDate(quote.sentAt)}
            </>
          }
        />
      </div>
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <DocumentSheet
          title="Quote"
          number={quote.number}
          status={quote.status}
          seller={seller}
          billTo={quote.billTo}
          dates={[
            { label: "Date", value: quote.issueDate },
            { label: "Valid until", value: quote.validUntil },
          ]}
          lines={quote.lines}
          subtotal={quote.subtotal}
          tax={quote.tax}
          total={quote.total}
          notes={quote.notes}
          terms={quote.terms}
        />
        <div className="grid gap-4 lg:sticky lg:top-6">
          <QuotePanel quote={quote} />
          <DocumentDelivery kind="quotes" id={quote.id} number={quote.number} clientEmail={quote.billTo?.email ?? null} canEmail />
        </div>
      </div>
    </>
  );
}
