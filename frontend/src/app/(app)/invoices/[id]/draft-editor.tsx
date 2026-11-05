"use client";

import { Eye, Stamp, Trash2 } from "lucide-react";
import { useState, useTransition } from "react";
import { toast } from "sonner";
import { ConfirmAction } from "@/components/confirm-action";
import { DocumentEditor, type EditorData } from "@/components/document-editor";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { formatDate } from "@/lib/format";
import type { Invoice } from "@/lib/types";
import { deleteInvoice, issueInvoice, saveInvoice } from "../actions";

export function DraftInvoiceEditor({ invoice, data, timeZone }: { invoice: Invoice; data: EditorData; timeZone: string }) {
  return (
    <DocumentEditor
      kind="invoice"
      data={data}
      initial={{
        clientId: invoice.client.id,
        clientName: invoice.client.name,
        currency: invoice.currency,
        issueDate: invoice.issueDate,
        endDate: invoice.dueDate,
        notes: invoice.notes,
        terms: invoice.terms,
        lines: invoice.lines,
      }}
      save={(input) => saveInvoice(invoice.id, input)}
      footer={
        <>
          <ConfirmAction
            trigger={
              <Button type="button" variant="ghost" className="me-auto text-destructive">
                <Trash2 /> Delete draft
              </Button>
            }
            title="Delete this draft?"
            description="Drafts have no number yet, so deleting one leaves no gap in your invoice sequence."
            confirmLabel="Delete"
            action={() => deleteInvoice(invoice.id)}
          />
          <Button asChild type="button" variant="ghost">
            <a href={`/invoices/${invoice.id}/pdf`} target="_blank" rel="noopener">
              <Eye /> Preview PDF
            </a>
          </Button>
          <IssueDialog invoice={invoice} timeZone={timeZone} />
        </>
      }
    />
  );
}

/** YYYY-MM-DD for today in the organization's time zone (the backend uses the same day). */
function todayIn(timeZone: string): string {
  try {
    return new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
  } catch {
    return new Date().toISOString().slice(0, 10);
  }
}

function addDays(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000);
}

/**
 * Issuing dates the invoice today and moves the due date by the same terms, so an old
 * draft never goes out backdated and already overdue. When the draft carries an earlier
 * date, the user sees both and can deliberately keep the draft's dates.
 */
function IssueDialog({ invoice, timeZone }: { invoice: Invoice; timeZone: string }) {
  const [open, setOpen] = useState(false);
  const [pending, startTransition] = useTransition();
  const today = todayIn(timeZone);
  const redated = invoice.issueDate !== today;
  const terms = invoice.dueDate ? daysBetween(invoice.issueDate, invoice.dueDate) : null;
  const newDue = terms === null ? null : addDays(today, terms);
  const canKeep = invoice.issueDate < today;
  const keptYear = invoice.issueDate.slice(0, 4);

  function issue(keepDates: boolean) {
    startTransition(async () => {
      const result = await issueInvoice(invoice.id, keepDates);
      setOpen(false);
      if (result.error) toast.error(result.error);
      else toast.success("Invoice issued");
    });
  }

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger asChild>
        <Button type="button" variant="outline">
          <Stamp /> Issue invoice
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent className="data-[size=default]:sm:max-w-md">
        <AlertDialogHeader>
          <AlertDialogTitle>Issue this invoice?</AlertDialogTitle>
          <AlertDialogDescription>
            It gets the next invoice number and is locked: no more edits, only payments, a credit note or a void. Save
            your changes first.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {redated && (
          <p className="rounded-md border bg-muted/40 p-3 text-sm" data-testid="issue-redate">
            This draft is dated <span className="figures font-medium">{formatDate(invoice.issueDate)}</span>. It will be
            issued dated today, <span className="figures font-medium">{formatDate(today)}</span>
            {newDue && (
              <>
                , due <span className="figures font-medium">{formatDate(newDue)}</span> (same {terms}-day terms)
              </>
            )}
            .
            {canKeep && keptYear !== today.slice(0, 4) && (
              <> Keeping the old date numbers it in the {keptYear} sequence.</>
            )}
          </p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>Cancel</AlertDialogCancel>
          {canKeep && (
            <Button type="button" variant="outline" disabled={pending} onClick={() => issue(true)}>
              Keep {formatDate(invoice.issueDate)}
            </Button>
          )}
          <Button type="button" disabled={pending} onClick={() => issue(false)}>
            {pending ? "Working…" : redated ? "Issue dated today" : "Issue"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
