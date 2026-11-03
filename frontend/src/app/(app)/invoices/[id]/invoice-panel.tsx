"use client";

import { Ban, CircleDollarSign, Copy, ExternalLink, FileMinus, Link2 } from "lucide-react";
import Link from "next/link";
import { type ReactNode, useActionState, useId, useState, useTransition } from "react";
import { toast } from "sonner";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useActionFeedback } from "@/hooks/use-action-feedback";
import { formatAmount, formatDate, formatMoney } from "@/lib/format";
import type { FormState, Invoice, PaymentMethod } from "@/lib/types";
import { createCreditNote, getPaymentLink, recordPayment, voidInvoice } from "../actions";

const METHODS: { value: PaymentMethod; label: string }[] = [
  { value: "BANK_TRANSFER", label: "Bank transfer" },
  { value: "CASH", label: "Cash" },
  { value: "CHEQUE", label: "Cheque" },
  { value: "CARD", label: "Card" },
  { value: "ONLINE", label: "Online" },
  { value: "OTHER", label: "Other" },
];

const methodLabel = (m: PaymentMethod) => METHODS.find((x) => x.value === m)?.label ?? m;

/** Money state and the actions that are legal right now. The backend re-checks every one. */
export function InvoicePanel({ invoice, isAdmin, extra }: { invoice: Invoice; isAdmin: boolean; extra?: ReactNode }) {
  const open = invoice.status === "ISSUED" || invoice.status === "PARTIALLY_PAID" || invoice.status === "OVERDUE";
  const isInvoice = invoice.kind === "INVOICE";

  return (
    <aside className="grid gap-4 lg:sticky lg:top-6">
      {isInvoice && (
        <section className="rounded-lg border bg-card p-5">
          <p className="text-xs font-medium uppercase tracking-[0.1em] text-muted-foreground">Balance due</p>
          <p className="figures mt-1 text-2xl font-semibold" data-testid="balance-due">
            {invoice.status === "VOID" ? "—" : formatMoney(invoice.balanceDue)}
          </p>
          <dl className="figures mt-3 grid grid-cols-[1fr_auto] gap-y-1 text-sm">
            <dt className="text-muted-foreground">Total</dt>
            <dd className="text-end">{formatAmount(invoice.total.amount)}</dd>
            <dt className="text-muted-foreground">Paid</dt>
            <dd className="text-end">{formatAmount(invoice.amountPaid.amount)}</dd>
            {invoice.amountCredited.amountMinor > 0 && (
              <>
                <dt className="text-muted-foreground">Credited</dt>
                <dd className="text-end">{formatAmount(invoice.amountCredited.amount)}</dd>
              </>
            )}
            {invoice.dueDate && (
              <>
                <dt className="text-muted-foreground">Due</dt>
                <dd className="text-end">{formatDate(invoice.dueDate)}</dd>
              </>
            )}
          </dl>
          {invoice.totalBase && invoice.baseCurrency !== invoice.currency && (
            <p className="figures mt-3 border-t pt-3 text-xs text-muted-foreground" data-testid="frozen-rate">
              1 {invoice.currency} = {invoice.exchangeRate} {invoice.baseCurrency}
              {invoice.rateSource === "PEG" ? " (official peg)" : invoice.rateSource === "MANUAL" ? " (your rate)" : invoice.rateSource === "ECB" ? " (ECB)" : ""}
              , frozen on issue. Counts as {formatMoney(invoice.totalBase)} in reports.
            </p>
          )}
          {open && (
            <div className="mt-4 grid gap-2">
              <PaymentDialog invoice={invoice} />
              {isAdmin && <CreditNoteDialog invoice={invoice} />}
            </div>
          )}
        </section>
      )}

      {extra}

      {isInvoice && open && <PaymentLinkBox invoiceId={invoice.id} />}

      {invoice.payments.length > 0 && (
        <section className="rounded-lg border bg-card p-5">
          <h2 className="text-sm font-semibold">Payments</h2>
          <ul className="mt-3 divide-y text-sm">
            {invoice.payments.map((p) => (
              <li key={p.id} className="flex items-baseline justify-between gap-3 py-2" data-testid="payment-row">
                <span>
                  <span className="block">{methodLabel(p.method)}</span>
                  <span className="figures text-xs text-muted-foreground">
                    {formatDate(p.paidOn)}
                    {p.reference && ` · ${p.reference}`}
                  </span>
                </span>
                <span className="figures font-medium">{formatAmount(p.amount.amount)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {invoice.creditNotes.length > 0 && (
        <section className="rounded-lg border bg-card p-5">
          <h2 className="text-sm font-semibold">Credit notes</h2>
          <ul className="mt-3 divide-y text-sm">
            {invoice.creditNotes.map((cn) => (
              <li key={cn.id} className="flex items-baseline justify-between gap-3 py-2">
                <span>
                  <Link href={`/invoices/${cn.id}`} className="figures block font-mono text-primary hover:underline">
                    {cn.number}
                  </Link>
                  <span className="text-xs text-muted-foreground">{cn.reason}</span>
                </span>
                <span className="figures font-medium">− {formatAmount(cn.total.amount)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {invoice.status === "VOID" && (
        <section className="rounded-lg border border-dashed bg-card p-5 text-sm">
          <p className="font-medium">Voided {formatDate(invoice.voidedAt)}</p>
          <p className="mt-1 text-muted-foreground">{invoice.voidReason}</p>
        </section>
      )}

      {isInvoice && isAdmin && open && <VoidDialog invoice={invoice} />}
    </aside>
  );
}

function PaymentDialog({ invoice }: { invoice: Invoice }) {
  const uid = useId();
  const [openDialog, setOpen] = useState(false);
  const [state, action, pending] = useActionState<FormState, FormData>(async (prev: FormState, data: FormData) => {
    const result = await recordPayment(invoice.id, prev, data);
    if (result.ok) setOpen(false);
    return result;
  }, {});
  useActionFeedback(state, "Payment recorded");

  return (
    <Dialog open={openDialog} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button className="w-full">
          <CircleDollarSign /> Record payment
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Record a payment</DialogTitle>
          <DialogDescription>
            Money you received outside Fatoora: a transfer, cash or a cheque. Up to {formatMoney(invoice.balanceDue)}.
          </DialogDescription>
        </DialogHeader>
        <form action={action} className="grid gap-4">
          {state.error && !state.fieldErrors && <p className="text-sm text-destructive">{state.error}</p>}
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id={`amount-${uid}`} label={`Amount (${invoice.currency})`} error={state.fieldErrors?.amount}>
              <Input
                id={`amount-${uid}`}
                name="amount"
                inputMode="decimal"
                required
                className="figures"
                defaultValue={state.values?.amount ?? invoice.balanceDue.amount}
              />
            </FormField>
            <FormField id={`paidOn-${uid}`} label="Received on">
              <Input id={`paidOn-${uid}`} name="paidOn" type="date" className="figures" defaultValue={state.values?.paidOn} />
            </FormField>
            <FormField id={`method-${uid}`} label="Method">
              <Select name="method" defaultValue={state.values?.method ?? "BANK_TRANSFER"}>
                <SelectTrigger id={`method-${uid}`} className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {METHODS.map((m) => (
                    <SelectItem key={m.value} value={m.value}>
                      {m.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FormField>
            <FormField id={`reference-${uid}`} label="Reference (optional)">
              <Input id={`reference-${uid}`} name="reference" placeholder="Transfer or cheque no." defaultValue={state.values?.reference} />
            </FormField>
          </div>
          <DialogFooter>
            <Button type="submit" disabled={pending}>
              {pending ? "Recording…" : "Record payment"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function CreditNoteDialog({ invoice }: { invoice: Invoice }) {
  const uid = useId();
  const [openDialog, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [amount, setAmount] = useState("");
  const [pending, startTransition] = useTransition();

  function submit() {
    startTransition(async () => {
      const result = await createCreditNote(invoice.id, reason.trim(), [
        { productId: null, description: reason.trim() || "Credit", quantity: "1", unit: null, unitPrice: amount.trim(), taxRateId: null },
      ]);
      if (result.error) toast.error(result.error);
      else {
        toast.success("Credit note issued");
        setOpen(false);
      }
    });
  }

  return (
    <Dialog open={openDialog} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" className="w-full">
          <FileMinus /> Issue credit note
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Issue a credit note</DialogTitle>
          <DialogDescription>
            Reduces what the client owes on {invoice.number} without editing it. It gets its own number (CN-…) and stays
            on record.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4">
          <FormField id={`reason-${uid}`} label="Reason (printed on the credit note)">
            <Textarea id={`reason-${uid}`} rows={2} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Discount agreed after delivery" />
          </FormField>
          <FormField id={`credit-${uid}`} label={`Amount to credit (${invoice.currency}, tax included)`}>
            <Input id={`credit-${uid}`} inputMode="decimal" className="figures" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </FormField>
        </div>
        <DialogFooter>
          <Button onClick={submit} disabled={pending || !reason.trim() || !amount.trim()}>
            {pending ? "Issuing…" : "Issue credit note"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function VoidDialog({ invoice }: { invoice: Invoice }) {
  const uid = useId();
  const [openDialog, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [pending, startTransition] = useTransition();

  return (
    <Dialog open={openDialog} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="ghost" className="justify-start text-destructive hover:text-destructive">
          <Ban /> Void invoice
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Void {invoice.number}?</DialogTitle>
          <DialogDescription>
            The invoice is cancelled but never deleted: it keeps its number, shows a VOID stamp, and the reason is kept
            in the audit log. This cannot be undone.
          </DialogDescription>
        </DialogHeader>
        <FormField id={`void-${uid}`} label="Reason">
          <Textarea id={`void-${uid}`} rows={2} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Issued to the wrong client" />
        </FormField>
        <DialogFooter>
          <Button
            className="bg-destructive text-white hover:bg-destructive/90"
            disabled={pending || !reason.trim()}
            onClick={() =>
              startTransition(async () => {
                const result = await voidInvoice(invoice.id, reason.trim());
                if (result.error) toast.error(result.error);
                else {
                  toast.success(`${invoice.number} voided`);
                  setOpen(false);
                }
              })
            }
          >
            {pending ? "Voiding…" : "Void invoice"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** The invoice's shareable payment page: anyone with the link can see and pay this one invoice. */
function PaymentLinkBox({ invoiceId }: { invoiceId: string }) {
  const [url, setUrl] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  return (
    <section className="grid gap-2 rounded-lg border bg-card p-5">
      <h2 className="text-sm font-semibold">Online payment</h2>
      {url ? (
        <>
          <div className="flex gap-1.5">
            <Input readOnly value={url} className="figures h-8 text-xs" aria-label="Payment link" onFocus={(e) => e.target.select()} />
            <Button
              size="icon-sm"
              variant="outline"
              aria-label="Copy payment link"
              onClick={async () => {
                await navigator.clipboard.writeText(url);
                toast.success("Payment link copied");
              }}
            >
              <Copy />
            </Button>
            <Button size="icon-sm" variant="outline" asChild aria-label="Open payment page">
              <a href={url} target="_blank" rel="noopener">
                <ExternalLink />
              </a>
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">Anyone with this link can view and pay this invoice only.</p>
        </>
      ) : (
        <Button
          variant="outline"
          className="justify-start"
          disabled={pending}
          onClick={() =>
            startTransition(async () => {
              const result = await getPaymentLink(invoiceId);
              if (result.url) setUrl(result.url);
              else toast.error(result.error ?? "Could not create the link");
            })
          }
        >
          <Link2 /> {pending ? "Creating…" : "Get payment link"}
        </Button>
      )}
    </section>
  );
}
