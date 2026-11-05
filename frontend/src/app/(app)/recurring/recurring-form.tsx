"use client";

import { useId, useState } from "react";
import { DocumentEditor, type EditorData } from "@/components/document-editor";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { formatDate } from "@/lib/format";
import { describeFrequency, type Frequency, type Recurring } from "@/lib/types";
import { cn } from "@/lib/utils";
import { saveRecurring } from "./actions";

const FREQUENCIES: Frequency[] = ["WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY"];
const UNITS: Record<Frequency, string> = { WEEKLY: "week", MONTHLY: "month", QUARTERLY: "quarter", YEARLY: "year" };

/** The invoice editor plus a schedule: what to bill, and when. */
export function RecurringForm({ data, recurring }: { data: EditorData; recurring?: Recurring }) {
  const uid = useId();
  const [name, setName] = useState(recurring?.name ?? "");
  const [frequency, setFrequency] = useState<Frequency>(recurring?.frequency ?? "MONTHLY");
  const [interval, setInterval] = useState(String(recurring?.intervalCount ?? 1));
  const [startDate, setStartDate] = useState(recurring?.startDate ?? data.today);
  const [endDate, setEndDate] = useState(recurring?.endDate ?? "");
  const [terms, setTerms] = useState(String(recurring?.paymentTermsDays ?? 30));
  const [autoIssue, setAutoIssue] = useState(recurring?.autoIssue ?? true);
  const [autoEmail, setAutoEmail] = useState(recurring?.autoEmail ?? false);
  const n = Math.max(1, Number.parseInt(interval, 10) || 1);

  const schedule = (
    <fieldset className="grid gap-4 rounded-lg border bg-muted/30 p-4 sm:p-5">
      <legend className="px-1 text-sm font-semibold">Schedule</legend>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div className="grid content-start gap-1.5 sm:col-span-2 lg:col-span-1">
          <Label htmlFor={`name-${uid}`}>Name</Label>
          <Input id={`name-${uid}`} value={name} onChange={(e) => setName(e.target.value)} placeholder="Office rent, monthly retainer…" required />
        </div>
        <div className="grid content-start gap-1.5">
          <Label htmlFor={`interval-${uid}`}>Repeats every</Label>
          <div className="flex gap-2">
            <Input
              id={`interval-${uid}`}
              type="number"
              min={1}
              max={12}
              value={interval}
              onChange={(e) => setInterval(e.target.value)}
              className="figures w-16 shrink-0"
            />
            <Select value={frequency} onValueChange={(v) => setFrequency(v as Frequency)}>
              <SelectTrigger className="w-full" aria-label="Period">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {FREQUENCIES.map((f) => (
                  <SelectItem key={f} value={f}>
                    {UNITS[f]}
                    {n === 1 ? "" : "s"}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <div className="grid content-start gap-1.5">
          <Label htmlFor={`terms-${uid}`}>Due after (days)</Label>
          <Input id={`terms-${uid}`} type="number" min={0} max={365} value={terms} onChange={(e) => setTerms(e.target.value)} className="figures" />
        </div>
        <div className="grid content-start gap-1.5">
          <Label htmlFor={`start-${uid}`}>First invoice on</Label>
          <Input id={`start-${uid}`} type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required className="figures" />
        </div>
        <div className="grid content-start gap-1.5">
          <Label htmlFor={`until-${uid}`}>Last invoice by</Label>
          <Input id={`until-${uid}`} type="date" value={endDate} min={startDate} onChange={(e) => setEndDate(e.target.value)} className="figures" />
          <p className="text-xs text-muted-foreground">Optional. Leave empty to keep going.</p>
        </div>
        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm sm:col-span-2 lg:col-span-3">
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={autoIssue} onChange={(e) => setAutoIssue(e.target.checked)} className="size-4 accent-primary" />
            Issue automatically
          </label>
          <label className={cn("flex items-center gap-2", !autoIssue && "text-muted-foreground")}>
            <input
              type="checkbox"
              checked={autoEmail && autoIssue}
              disabled={!autoIssue}
              onChange={(e) => setAutoEmail(e.target.checked)}
              className="size-4 accent-primary"
            />
            Email it to the client
          </label>
        </div>
      </div>
      <p className="text-xs text-muted-foreground">
        {describeFrequency(frequency, n)}, starting {startDate ? formatDate(startDate) : "…"}. Each run creates{" "}
        {autoIssue ? "an issued, numbered invoice" : "a draft for you to review"}
        {autoIssue && autoEmail ? " and emails it with the PDF" : ""}. A start date in the past never back-bills.
      </p>
    </fieldset>
  );

  return (
    <DocumentEditor
      kind="recurring"
      data={data}
      hideDates
      extra={schedule}
      submitLabel={recurring ? "Save changes" : "Create recurring invoice"}
      initial={
        recurring
          ? {
              clientId: recurring.client.id,
              clientName: recurring.client.name,
              currency: recurring.currency,
              issueDate: recurring.startDate,
              endDate: null,
              notes: recurring.notes,
              terms: recurring.terms,
              lines: recurring.lines,
            }
          : undefined
      }
      save={(input) =>
        saveRecurring(recurring?.id ?? null, {
          ...input,
          name: name.trim(),
          frequency,
          intervalCount: n,
          startDate,
          endDate: endDate || null,
          paymentTermsDays: Math.max(0, Number.parseInt(terms, 10) || 0),
          autoIssue,
          autoEmail: autoIssue && autoEmail,
        })
      }
    />
  );
}
