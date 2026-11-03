"use client";

import { Trash2 } from "lucide-react";
import { useActionState, useTransition } from "react";
import { toast } from "sonner";
import { CurrencyField, TextField } from "@/components/fields";
import { Button } from "@/components/ui/button";
import { useActionFeedback } from "@/hooks/use-action-feedback";
import { formatDate } from "@/lib/format";
import type { ExchangeRate, FormState } from "@/lib/types";
import { addExchangeRate, deleteExchangeRate } from "../actions";

export function RatesEditor({ rates, base, canEdit }: { rates: ExchangeRate[]; base: string; canEdit: boolean }) {
  const [state, action, pending] = useActionState<FormState, FormData>(addExchangeRate, {});
  useActionFeedback(state, "Rate saved");
  const [deleting, startDelete] = useTransition();

  return (
    <div className="grid gap-6">
      {canEdit && (
        <form action={action} className="grid gap-4 rounded-lg border bg-card p-5 sm:grid-cols-[1fr_1fr_1fr_auto] sm:items-end">
          <CurrencyField state={state} label="Currency" defaultValue={base === "USD" ? "EUR" : "USD"} />
          <TextField name="rate" label={`1 unit = how many ${base}`} state={state} inputMode="decimal" className="figures" required />
          <TextField name="effectiveDate" label="From (optional)" type="date" state={state} className="figures" />
          <Button type="submit" disabled={pending}>
            {pending ? "Saving…" : "Add rate"}
          </Button>
          {state.error && !state.fieldErrors && <p className="text-sm text-destructive sm:col-span-4">{state.error}</p>}
        </form>
      )}

      <div className="overflow-hidden rounded-lg border bg-card">
        {rates.length === 0 ? (
          <p className="px-5 py-8 text-sm text-muted-foreground">
            No rates of your own. Pegged Gulf currencies and USD work out of the box; add a rate here to use your bank&apos;s
            rate, or for currencies without a peg.
          </p>
        ) : (
          <ul className="divide-y">
            {rates.map((r) => (
              <li key={r.id} className="flex items-center gap-4 px-5 py-3 text-sm" data-testid="rate-row">
                <span className="figures flex-1">
                  1 <span className="font-medium">{r.currency}</span> = {r.rate} {r.baseCurrency}
                </span>
                <span className="figures text-xs text-muted-foreground">from {formatDate(r.effectiveDate)}</span>
                {canEdit && (
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`Remove ${r.currency} rate`}
                    disabled={deleting}
                    onClick={() =>
                      startDelete(async () => {
                        const res = await deleteExchangeRate(r.id);
                        if (res.error) toast.error(res.error);
                        else toast.success("Rate removed. Issued invoices keep theirs.");
                      })
                    }
                  >
                    <Trash2 />
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
