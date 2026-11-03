"use client";

import { useActionState, useId } from "react";
import { CurrencyField, TextAreaField, TextField } from "@/components/fields";
import { FormError } from "@/components/form-error";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useActionFeedback } from "@/hooks/use-action-feedback";
import { type FormState, NONE_VALUE, type Product, type TaxRate } from "@/lib/types";
import { saveProduct } from "./actions";

const UNITS = ["item", "hour", "day", "month", "project", "kg", "m²", "license"];

export function ProductForm({
  product,
  taxRates,
  defaultCurrency,
}: {
  product?: Product;
  taxRates: TaxRate[];
  defaultCurrency: string;
}) {
  const [state, action, pending] = useActionState<FormState, FormData>(saveProduct.bind(null, product?.id ?? null), {});
  useActionFeedback(state, "Product saved");
  const uid = useId();
  const defaultTax = product?.taxRate?.id ?? (product ? undefined : taxRates.find((t) => t.isDefault)?.id);

  return (
    <form action={action} className="grid gap-6">
      {state.error && <FormError message={state.fieldErrors ? "Some fields need attention." : state.error} />}
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <TextField name="name" label="Name" state={state} defaultValue={product?.name} required autoFocus={!product} />
        </div>
        <div className="sm:col-span-2">
          <TextAreaField name="description" label="Description (printed on the line)" state={state} defaultValue={product?.description} rows={2} />
        </div>
        <TextField
          name="unitPrice"
          label="Unit price"
          inputMode="decimal"
          state={state}
          defaultValue={product?.unitPrice.amount}
          required
          className="figures"
          hint="Use the currency's decimals: 25.500 for JOD, 19.99 for USD."
        />
        <CurrencyField state={state} defaultValue={product?.unitPrice.currency ?? defaultCurrency} />
        <FormField id={`unit-${uid}`} label="Unit" error={state.fieldErrors?.unit}>
          <Select name="unit" defaultValue={state.values?.unit || product?.unit || "item"}>
            <SelectTrigger id={`unit-${uid}`} className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Array.from(new Set([...UNITS, product?.unit ?? "item"])).map((u) => (
                <SelectItem key={u} value={u}>
                  {u}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </FormField>
        <FormField id={`tax-${uid}`} label="Tax" error={state.fieldErrors?.taxRateId}>
          <Select name="taxRateId" defaultValue={state.values?.taxRateId || defaultTax || NONE_VALUE}>
            <SelectTrigger id={`tax-${uid}`} className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NONE_VALUE}>No tax</SelectItem>
              {taxRates.map((t) => (
                <SelectItem key={t.id} value={t.id}>
                  {t.name} · <span className="figures">{t.rate}%</span>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </FormField>
        <TextField name="sku" label="SKU / code (optional)" state={state} defaultValue={product?.sku} />
      </div>
      <div className="flex justify-end border-t pt-5">
        <Button type="submit" disabled={pending} className="min-w-32">
          {pending ? "Saving…" : product ? "Save changes" : "Add product"}
        </Button>
      </div>
    </form>
  );
}
