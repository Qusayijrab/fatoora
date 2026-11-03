import Link from "next/link";
import { Suspense } from "react";
import { DocumentSheet } from "@/components/document-sheet";
import { PageHeader } from "@/components/page-header";
import { StatusStamp } from "@/components/status-stamp";
import { Skeleton } from "@/components/ui/skeleton";
import { getEditorData, getQuote } from "@/lib/api";
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
                Draft quote <StatusStamp status="DRAFT" />
              </span>
            }
            description={`For ${quote.client.name}. Sending numbers it and locks the prices.`}
          />
        </div>
        <div className="rounded-lg border bg-card p-5 sm:p-7">
          <QuoteDraftEditor quote={quote} data={data} />
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
              {quote.number}
              <StatusStamp status={quote.status} />
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
          seller={null}
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
        <QuotePanel quote={quote} />
      </div>
    </>
  );
}
