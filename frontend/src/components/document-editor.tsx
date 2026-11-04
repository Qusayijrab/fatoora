"use client";

import { Plus, Trash2 } from "lucide-react";
import Link from "next/link";
import { type ReactNode, useId, useState, useTransition } from "react";
import { toast } from "sonner";
import { FormError } from "@/components/form-error";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { CURRENCY_GROUPS } from "@/lib/currencies";
import { taxLabel } from "@/lib/format";
import { amountPlaceholder, formatMinor, lineAmounts } from "@/lib/money";
import type { Client, DocumentInput, FormState, Line, Product, TaxRate } from "@/lib/types";
import { cn } from "@/lib/utils";

const NO_TAX = "__none__";
const CUSTOM = "__custom__";

type EditorLine = {
  key: string;
  productId: string | null;
  description: string;
  quantity: string;
  unit: string;
  unitPrice: string;
  taxRateId: string | null;
};

export type EditorData = {
  orgCurrency: string;
  today: string;
  clients: Client[];
  products: Product[];
  taxRates: TaxRate[];
};

type Initial = {
  clientId: string;
  currency: string;
  issueDate: string;
  endDate: string | null;
  notes: string | null;
  terms: string | null;
  lines: Line[];
};

let keySeq = 0;
const nextKey = () => `l${++keySeq}`;

function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/**
 * The quote and invoice editor. Totals are previewed live with exact BigInt math using
 * the same rounding as the server; the server recalculates and is the authority.
 */
export function DocumentEditor({
  kind,
  data,
  initial,
  presetClientId,
  save,
  footer,
  extra,
  hideDates = false,
  submitLabel = "Save draft",
}: {
  kind: "invoice" | "quote" | "recurring";
  data: EditorData;
  initial?: Initial;
  presetClientId?: string;
  save: (input: DocumentInput) => Promise<FormState>;
  footer?: ReactNode;
  /** Extra fields rendered under the header (e.g. a recurring schedule). */
  extra?: ReactNode;
  /** Recurring templates have no issue/due date of their own. */
  hideDates?: boolean;
  submitLabel?: string;
}) {
  const uid = useId();
  const defaultTax = data.taxRates.find((t) => t.isDefault)?.id ?? null;
  const blankLine = (): EditorLine => ({
    key: nextKey(),
    productId: null,
    description: "",
    quantity: "1",
    unit: "",
    unitPrice: "",
    taxRateId: defaultTax,
  });

  const presetClient = data.clients.find((c) => c.id === (initial?.clientId ?? presetClientId));
  const [clientId, setClientId] = useState(initial?.clientId ?? presetClient?.id ?? "");
  const [currency, setCurrency] = useState(initial?.currency ?? presetClient?.currency ?? data.orgCurrency);
  const [currencyTouched, setCurrencyTouched] = useState(!!initial);
  const [issueDate, setIssueDate] = useState(initial?.issueDate ?? data.today);
  const [endDate, setEndDate] = useState(initial?.endDate ?? addDays(initial?.issueDate ?? data.today, 30));
  const [endTouched, setEndTouched] = useState(!!initial);
  const [notes, setNotes] = useState(initial?.notes ?? "");
  const [terms, setTerms] = useState(initial?.terms ?? "");
  const [lines, setLines] = useState<EditorLine[]>(
    initial?.lines.length
      ? initial.lines.map((l) => ({
          key: nextKey(),
          productId: l.productId,
          description: l.description,
          quantity: l.quantity,
          unit: l.unit ?? "",
          unitPrice: l.unitPrice.amount,
          taxRateId: l.taxRateId,
        }))
      : [blankLine()],
  );
  const [state, setState] = useState<FormState>({});
  const [pending, startTransition] = useTransition();

  const rateOf = (id: string | null) => data.taxRates.find((t) => t.id === id)?.rate ?? "0";
  const computed = lines.map((l) => lineAmounts(l.unitPrice, l.quantity, rateOf(l.taxRateId), currency));
  const totals = computed.reduce<{ subtotal: bigint; tax: bigint; total: bigint }>(
    (acc, a) => (a ? { subtotal: acc.subtotal + a.subtotal, tax: acc.tax + a.tax, total: acc.total + a.total } : acc),
    { subtotal: 0n, tax: 0n, total: 0n },
  );
  const incomplete = computed.some((a) => a === null);

  function update(key: string, patch: Partial<EditorLine>) {
    setLines((ls) => ls.map((l) => (l.key === key ? { ...l, ...patch } : l)));
  }

  function pickProduct(key: string, productId: string) {
    if (productId === CUSTOM) {
      update(key, { productId: null });
      return;
    }
    const p = data.products.find((x) => x.id === productId);
    if (!p) return;
    const samePrice = p.unitPrice.currency === currency;
    if (!samePrice) toast.info(`${p.name} is priced in ${p.unitPrice.currency}. Enter the ${currency} price.`);
    update(key, {
      productId: p.id,
      description: p.description ? `${p.name} — ${p.description}` : p.name,
      unit: p.unit,
      unitPrice: samePrice ? p.unitPrice.amount : "",
      taxRateId: p.taxRate?.id ?? null,
    });
  }

  function chooseClient(id: string) {
    setClientId(id);
    const c = data.clients.find((x) => x.id === id);
    if (!currencyTouched) setCurrency(c?.currency ?? data.orgCurrency);
  }

  function changeIssueDate(value: string) {
    setIssueDate(value);
    if (!endTouched && value) setEndDate(addDays(value, 30));
  }

  function submit() {
    const input: DocumentInput = {
      clientId,
      currency,
      issueDate,
      ...(kind === "invoice" ? { dueDate: endDate || null } : { validUntil: endDate || null }),
      notes: notes.trim() || null,
      terms: terms.trim() || null,
      lines: lines.map((l) => ({
        productId: l.productId,
        description: l.description.trim(),
        quantity: l.quantity.trim(),
        unit: l.unit.trim() || null,
        unitPrice: l.unitPrice.trim(),
        taxRateId: l.taxRateId,
      })),
    };
    startTransition(async () => {
      const result = await save(input);
      setState(result);
      if (result.ok) toast.success(kind === "invoice" ? "Invoice saved" : kind === "quote" ? "Quote saved" : "Recurring invoice saved");
      else if (result.error && !result.fieldErrors) toast.error(result.error);
    });
  }

  const err = (field: string) => state.fieldErrors?.[field];
  const endLabel = kind === "invoice" ? "Due date" : "Valid until";

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
      className="grid gap-8"
    >
      {state.fieldErrors && <FormError message="Some fields need attention." />}

      {/* Header fields */}
      <div className={cn("grid gap-4 sm:grid-cols-2", hideDates ? "lg:grid-cols-[2fr_1fr]" : "lg:grid-cols-[2fr_1.4fr_1fr_1fr]")}>
        <div className="grid content-start gap-1.5 sm:col-span-2 lg:col-span-1">
          <Label htmlFor={`client-${uid}`}>Client</Label>
          {data.clients.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No clients yet.{" "}
              <Link href="/clients/new" className="font-medium text-primary underline-offset-4 hover:underline">
                Add one first
              </Link>
              .
            </p>
          ) : (
            <Select value={clientId} onValueChange={chooseClient}>
              <SelectTrigger id={`client-${uid}`} className="w-full" aria-invalid={!!err("clientId") || undefined}>
                <SelectValue placeholder="Choose a client" />
              </SelectTrigger>
              <SelectContent>
                {data.clients.map((c) => (
                  <SelectItem key={c.id} value={c.id}>
                    {c.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
          {err("clientId") && <p className="text-sm text-destructive">Choose a client</p>}
        </div>
        <div className="grid content-start gap-1.5 sm:col-span-2 lg:col-span-1">
          <Label htmlFor={`currency-${uid}`}>Currency</Label>
          <Select
            value={currency}
            onValueChange={(v) => {
              setCurrency(v);
              setCurrencyTouched(true);
            }}
          >
            <SelectTrigger id={`currency-${uid}`} className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {CURRENCY_GROUPS.map((g) => (
                <SelectGroup key={g.label}>
                  <SelectLabel>{g.label}</SelectLabel>
                  {g.currencies.map((c) => (
                    <SelectItem key={c.code} value={c.code}>
                      <span className="figures w-9 font-medium">{c.code}</span>
                      <span className="text-muted-foreground">{c.name}</span>
                    </SelectItem>
                  ))}
                </SelectGroup>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className={cn("grid content-start gap-1.5", hideDates && "hidden")}>
          <Label htmlFor={`issue-${uid}`}>{kind === "invoice" ? "Issue date" : "Quote date"}</Label>
          <Input id={`issue-${uid}`} type="date" value={issueDate} onChange={(e) => changeIssueDate(e.target.value)} required className="figures" />
        </div>
        <div className={cn("grid content-start gap-1.5", hideDates && "hidden")}>
          <Label htmlFor={`end-${uid}`}>{endLabel}</Label>
          <Input
            id={`end-${uid}`}
            type="date"
            value={endDate}
            min={issueDate}
            onChange={(e) => {
              setEndDate(e.target.value);
              setEndTouched(true);
            }}
            className="figures"
          />
        </div>
      </div>

      {extra}

      {/* Lines */}
      <div className="relative rounded-lg border sm:overflow-x-auto">
        <table className="w-full text-sm max-sm:block sm:min-w-[46rem]">
          <thead className="max-sm:hidden">
            <tr className="border-b bg-muted/50 text-[11px] uppercase tracking-[0.08em] text-muted-foreground">
              <th className="px-3 py-2.5 text-start font-medium">Item</th>
              <th className="w-24 px-2 py-2.5 text-end font-medium">Qty</th>
              <th className="w-32 px-2 py-2.5 text-end font-medium">Unit price</th>
              <th className="w-40 px-2 py-2.5 text-start font-medium">Tax</th>
              <th className="w-32 px-3 py-2.5 text-end font-medium">Amount</th>
              <th className="w-10" />
            </tr>
          </thead>
          <tbody className="divide-y max-sm:block">
            {lines.map((line, i) => {
              const amounts = computed[i];
              const lineErr = (f: string) => err(`lines[${i}].${f}`);
              return (
                <tr key={line.key} data-testid="editor-line" className="align-top max-sm:relative max-sm:grid max-sm:grid-cols-2 max-sm:gap-x-3 max-sm:gap-y-3 max-sm:p-3">
                  <td className="px-3 py-2.5 max-sm:col-span-2 max-sm:p-0 max-sm:pe-10">
                    <div className="grid gap-1.5">
                      {data.products.length > 0 && (
                        <Select value={line.productId ?? CUSTOM} onValueChange={(v) => pickProduct(line.key, v)}>
                          <SelectTrigger size="sm" className="h-7 w-full text-xs" aria-label={`Product for line ${i + 1}`}>
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value={CUSTOM}>Custom item</SelectItem>
                            {data.products.map((p) => (
                              <SelectItem key={p.id} value={p.id}>
                                {p.name}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      )}
                      <Textarea
                        rows={1}
                        value={line.description}
                        onChange={(e) => update(line.key, { description: e.target.value })}
                        placeholder="Description, printed on the document"
                        aria-label={`Description for line ${i + 1}`}
                        aria-invalid={!!lineErr("description") || undefined}
                        className="min-h-9 w-full resize-y"
                      />
                    </div>
                  </td>
                  <td className="px-2 py-2.5 max-sm:p-0">
                    <MobileLabel>Qty</MobileLabel>
                    <Input
                      value={line.quantity}
                      onChange={(e) => update(line.key, { quantity: e.target.value })}
                      inputMode="decimal"
                      aria-label={`Quantity for line ${i + 1}`}
                      aria-invalid={!!lineErr("quantity") || undefined}
                      className="figures text-end"
                    />
                    {line.unit && <p className="mt-1 text-end text-xs text-muted-foreground">{line.unit}</p>}
                  </td>
                  <td className="px-2 py-2.5 max-sm:p-0">
                    <MobileLabel>Unit price</MobileLabel>
                    <Input
                      value={line.unitPrice}
                      onChange={(e) => update(line.key, { unitPrice: e.target.value })}
                      inputMode="decimal"
                      placeholder={amountPlaceholder(currency)}
                      aria-label={`Unit price for line ${i + 1}`}
                      aria-invalid={!!lineErr("unitPrice") || (line.unitPrice !== "" && !amounts) || undefined}
                      className="figures text-end"
                    />
                  </td>
                  <td className="px-2 py-2.5 max-sm:p-0">
                    <MobileLabel>Tax</MobileLabel>
                    <Select value={line.taxRateId ?? NO_TAX} onValueChange={(v) => update(line.key, { taxRateId: v === NO_TAX ? null : v })}>
                      <SelectTrigger className="w-full" aria-label={`Tax for line ${i + 1}`}>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NO_TAX}>No tax</SelectItem>
                        {data.taxRates.map((t) => (
                          <SelectItem key={t.id} value={t.id}>
                            {taxLabel(t)}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </td>
                  <td className="figures px-3 py-3.5 text-end font-medium max-sm:p-0">
                    <MobileLabel>Amount</MobileLabel>
                    {amounts ? formatMinor(amounts.subtotal, currency) : <span className="text-muted-foreground">—</span>}
                    {amounts && amounts.tax > 0n && (
                      <p className="text-xs font-normal text-muted-foreground">+ {formatMinor(amounts.tax, currency)} tax</p>
                    )}
                  </td>
                  <td className="py-2.5 pe-2 max-sm:absolute max-sm:end-1.5 max-sm:top-1.5 max-sm:p-0">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label={`Remove line ${i + 1}`}
                      disabled={lines.length === 1}
                      onClick={() => setLines((ls) => ls.filter((l) => l.key !== line.key))}
                    >
                      <Trash2 />
                    </Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <div className="flex flex-wrap items-start justify-between gap-6 border-t bg-muted/30 px-3 py-3">
          <Button type="button" variant="outline" size="sm" onClick={() => setLines((ls) => [...ls, blankLine()])}>
            <Plus /> Add line
          </Button>
          <dl className="figures grid min-w-56 grid-cols-[1fr_auto] gap-x-8 gap-y-1 text-sm max-sm:w-full">
            <dt className="text-muted-foreground">Subtotal</dt>
            <dd className="text-end">{formatMinor(totals.subtotal, currency)}</dd>
            <dt className="text-muted-foreground">Tax</dt>
            <dd className="text-end">{formatMinor(totals.tax, currency)}</dd>
            <dt className="border-t pt-1.5 font-semibold">Total</dt>
            <dd className="border-t pt-1.5 text-end text-base font-semibold" data-testid="editor-total">
              {currency} {formatMinor(totals.total, currency)}
            </dd>
          </dl>
        </div>
      </div>
      {incomplete && (
        <p className={cn("-mt-5 text-xs text-muted-foreground", state.fieldErrors && "text-destructive")}>
          Each line needs a description, a quantity and a price in {currency} ({amountPlaceholder(currency)}).
        </p>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        <div className="grid gap-1.5">
          <Label htmlFor={`notes-${uid}`}>Notes to the client</Label>
          <Textarea id={`notes-${uid}`} rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Thank you for your business." />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor={`terms-${uid}`}>Payment terms</Label>
          <Textarea id={`terms-${uid}`} rows={3} value={terms} onChange={(e) => setTerms(e.target.value)} placeholder="Bank transfer to … within 30 days." />
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-end gap-2 border-t pt-5">
        {footer}
        <Button type="submit" disabled={pending || !clientId} className="min-w-32">
          {pending ? "Saving…" : submitLabel}
        </Button>
      </div>
    </form>
  );
}

/** Column name shown above a line's field on phones, where the table header is hidden. */
function MobileLabel({ children }: { children: ReactNode }) {
  return (
    <span aria-hidden className="mb-1 block text-[11px] uppercase tracking-[0.08em] text-muted-foreground sm:hidden">
      {children}
    </span>
  );
}
