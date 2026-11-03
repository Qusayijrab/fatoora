"use client";

import { Check, Eye, ReceiptText, Send, Trash2, X } from "lucide-react";
import Link from "next/link";
import { useTransition } from "react";
import { toast } from "sonner";
import { ConfirmAction } from "@/components/confirm-action";
import { DocumentEditor, type EditorData } from "@/components/document-editor";
import { Button } from "@/components/ui/button";
import { formatDate, formatMoney } from "@/lib/format";
import type { Quote } from "@/lib/types";
import { convertQuote, deleteQuote, quoteTransition, saveQuote } from "../actions";

export function QuoteDraftEditor({ quote, data }: { quote: Quote; data: EditorData }) {
  return (
    <DocumentEditor
      kind="quote"
      data={data}
      initial={{
        clientId: quote.client.id,
        currency: quote.currency,
        issueDate: quote.issueDate,
        endDate: quote.validUntil,
        notes: quote.notes,
        terms: quote.terms,
        lines: quote.lines,
      }}
      save={(input) => saveQuote(quote.id, input)}
      footer={
        <>
          <ConfirmAction
            trigger={
              <Button type="button" variant="ghost" className="me-auto text-destructive">
                <Trash2 /> Delete draft
              </Button>
            }
            title="Delete this draft quote?"
            description="It has no number yet, so nothing is lost from your quote sequence."
            confirmLabel="Delete"
            action={() => deleteQuote(quote.id)}
          />
          <Button asChild type="button" variant="ghost">
            <a href={`/quotes/${quote.id}/pdf`} target="_blank" rel="noopener">
              <Eye /> Preview PDF
            </a>
          </Button>
          <ConfirmAction
            trigger={
              <Button type="button" variant="outline">
                <Send /> Send quote
              </Button>
            }
            title="Send this quote?"
            description="It gets the next quote number and its prices are locked. Save your changes first."
            confirmLabel="Send"
            destructive={false}
            successMessage="Quote sent"
            action={() => quoteTransition(quote.id, "send")}
          />
        </>
      }
    />
  );
}

export function QuotePanel({ quote }: { quote: Quote }) {
  const [pending, startTransition] = useTransition();

  function run(action: "accept" | "reject", done: string) {
    startTransition(async () => {
      const r = await quoteTransition(quote.id, action);
      if (r.error) toast.error(r.error);
      else toast.success(done);
    });
  }

  return (
    <aside className="grid gap-4">
      <section className="rounded-lg border bg-card p-5">
        <p className="text-xs font-medium uppercase tracking-[0.1em] text-muted-foreground">Quoted</p>
        <p className="figures mt-1 text-2xl font-semibold">{formatMoney(quote.total)}</p>
        <p className="mt-1 text-sm text-muted-foreground">Valid until {formatDate(quote.validUntil)}</p>

        {quote.status === "SENT" && (
          <div className="mt-4 grid gap-2">
            <p className="text-sm text-muted-foreground">Did the client answer?</p>
            <Button disabled={pending} onClick={() => run("accept", "Quote accepted")}>
              <Check /> Mark accepted
            </Button>
            <Button variant="outline" disabled={pending} onClick={() => run("reject", "Quote marked rejected")}>
              <X /> Mark rejected
            </Button>
          </div>
        )}

        {quote.status === "ACCEPTED" && !quote.convertedInvoiceId && (
          <div className="mt-4 grid gap-2">
            <p className="text-sm text-muted-foreground">Accepted {formatDate(quote.acceptedAt)}. Ready to bill.</p>
            <ConfirmAction
              trigger={
                <Button>
                  <ReceiptText /> Create invoice
                </Button>
              }
              title="Turn this quote into an invoice?"
              description="A new invoice draft is created with the same client, currency and lines. You can review it before issuing."
              confirmLabel="Create invoice"
              destructive={false}
              action={() => convertQuote(quote.id)}
            />
          </div>
        )}

        {quote.convertedInvoiceId && (
          <p className="mt-4 text-sm">
            Invoiced:{" "}
            <Link href={`/invoices/${quote.convertedInvoiceId}`} className="font-medium text-primary hover:underline">
              open the invoice
            </Link>
          </p>
        )}
      </section>
    </aside>
  );
}
