"use client";

import { Archive, ArchiveRestore, Pencil, Plus } from "lucide-react";
import { useActionState, useState, useTransition } from "react";
import { toast } from "sonner";
import { FormError } from "@/components/form-error";
import { FormField } from "@/components/form-field";
import { StatusStamp } from "@/components/status-stamp";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useActionFeedback } from "@/hooks/use-action-feedback";
import type { FormState, TaxRate } from "@/lib/types";
import { cn } from "@/lib/utils";
import { saveTaxRate, setTaxRateArchived } from "../actions";

/** Rates as an editable ruled list: edit happens in place, never in a modal. */
export function TaxRateRows({ rates, canEdit }: { rates: TaxRate[]; canEdit: boolean }) {
  const [editing, setEditing] = useState<string | null>(rates.length === 0 && canEdit ? "new" : null);

  return (
    <div className="grid gap-4">
      <ul className="divide-y overflow-hidden rounded-lg border bg-card">
        {rates.length === 0 && editing !== "new" && (
          <li className="px-5 py-8 text-sm text-muted-foreground">
            No tax rates yet. In Jordan the general sales tax is 16%; add the rates you charge.
          </li>
        )}
        {rates.map((rate) =>
          editing === rate.id ? (
            <li key={rate.id} className="bg-muted/40 px-5 py-4">
              <RateForm rate={rate} onDone={() => setEditing(null)} />
            </li>
          ) : (
            <li
              key={rate.id}
              data-testid="tax-rate-row"
              className={cn("flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3.5", rate.archived && "opacity-60")}
            >
              <span className="figures w-16 text-lg font-semibold tabular-nums">{rate.rate}%</span>
              <span className="min-w-0 flex-1 font-medium">{rate.name}</span>
              {rate.isDefault && <StatusStamp status="ACCEPTED" label="Default" />}
              {rate.archived && <StatusStamp status="VOID" label="Archived" />}
              {canEdit && (
                <span className="flex gap-1">
                  {!rate.archived && (
                    <Button variant="ghost" size="icon-sm" aria-label={`Edit ${rate.name}`} onClick={() => setEditing(rate.id)}>
                      <Pencil />
                    </Button>
                  )}
                  <ArchiveToggle rate={rate} />
                </span>
              )}
            </li>
          ),
        )}
        {editing === "new" && (
          <li className="bg-muted/40 px-5 py-4">
            <RateForm onDone={() => setEditing(null)} />
          </li>
        )}
      </ul>
      {canEdit && editing !== "new" && (
        <div>
          <Button variant="outline" onClick={() => setEditing("new")}>
            <Plus /> Add tax rate
          </Button>
        </div>
      )}
      {!canEdit && <p className="text-sm text-muted-foreground">Only owners and admins can change tax rates.</p>}
    </div>
  );
}

function RateForm({ rate, onDone }: { rate?: TaxRate; onDone: () => void }) {
  const [state, action, pending] = useActionState<FormState, FormData>(
    async (prev: FormState, data: FormData) => {
      const result = await saveTaxRate(rate?.id ?? null, prev, data);
      if (result.ok) onDone();
      return result;
    },
    {},
  );
  useActionFeedback(state, rate ? "Tax rate updated" : "Tax rate added");

  return (
    <form action={action} className="grid gap-3">
      {state.error && !state.fieldErrors && <FormError message={state.error} />}
      <div className="grid gap-3 sm:grid-cols-[1fr_8rem]">
        <FormField id={`name-${rate?.id ?? "new"}`} label="Name" error={state.fieldErrors?.name}>
          <Input
            id={`name-${rate?.id ?? "new"}`}
            name="name"
            required
            autoFocus
            placeholder="General sales tax"
            defaultValue={state.values?.name ?? rate?.name}
          />
        </FormField>
        <FormField id={`rate-${rate?.id ?? "new"}`} label="Rate %" error={state.fieldErrors?.rate}>
          <Input
            id={`rate-${rate?.id ?? "new"}`}
            name="rate"
            required
            inputMode="decimal"
            className="figures"
            placeholder="16"
            defaultValue={state.values?.rate ?? rate?.rate}
          />
        </FormField>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" name="isDefault" defaultChecked={rate?.isDefault} className="size-4 accent-primary" />
          Default for new products and lines
        </label>
        <div className="flex gap-2">
          <Button type="button" variant="ghost" onClick={onDone}>
            Cancel
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? "Saving…" : "Save"}
          </Button>
        </div>
      </div>
    </form>
  );
}

function ArchiveToggle({ rate }: { rate: TaxRate }) {
  const [pending, startTransition] = useTransition();
  return (
    <Button
      variant="ghost"
      size="icon-sm"
      disabled={pending}
      aria-label={rate.archived ? `Restore ${rate.name}` : `Archive ${rate.name}`}
      onClick={() =>
        startTransition(async () => {
          const r = await setTaxRateArchived(rate.id, !rate.archived);
          if (r.error) toast.error(r.error);
          else toast.success(rate.archived ? `${rate.name} restored` : `${rate.name} archived`);
        })
      }
    >
      {rate.archived ? <ArchiveRestore /> : <Archive />}
    </Button>
  );
}
