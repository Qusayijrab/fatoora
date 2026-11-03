"use client";

import { type ComponentProps, useId } from "react";
import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { COUNTRY_GROUPS, countryName } from "@/lib/countries";
import { CURRENCY_GROUPS } from "@/lib/currencies";
import { type FormState, NONE_VALUE as NONE } from "@/lib/types";

/** Label + input + inline error, wired to a FormState from useActionState. */
export function TextField({
  name,
  label,
  state,
  defaultValue,
  hint,
  ...props
}: { name: string; label: string; state: FormState; defaultValue?: string | null; hint?: string } & Omit<
  ComponentProps<typeof Input>,
  "name" | "defaultValue"
>) {
  const id = `${name}-${useId()}`;
  const error = state.fieldErrors?.[name];
  return (
    <FormField id={id} label={label} error={error}>
      <Input
        id={id}
        name={name}
        defaultValue={state.values?.[name] ?? defaultValue ?? ""}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        {...props}
      />
      {hint && !error && <p className="text-xs text-muted-foreground">{hint}</p>}
    </FormField>
  );
}

export function TextAreaField({
  name,
  label,
  state,
  defaultValue,
  rows = 3,
}: {
  name: string;
  label: string;
  state: FormState;
  defaultValue?: string | null;
  rows?: number;
}) {
  const id = `${name}-${useId()}`;
  return (
    <FormField id={id} label={label} error={state.fieldErrors?.[name]}>
      <Textarea id={id} name={name} rows={rows} defaultValue={state.values?.[name] ?? defaultValue ?? ""} />
    </FormField>
  );
}

/** ISO currency picker. With `allowNone`, an empty choice means "use the organization default". */
export function CurrencyField({
  name = "currency",
  label = "Currency",
  state,
  defaultValue,
  allowNone,
  noneLabel = "Organization default",
}: {
  name?: string;
  label?: string;
  state: FormState;
  defaultValue?: string | null;
  allowNone?: boolean;
  noneLabel?: string;
}) {
  const id = `${name}-${useId()}`;
  const initial = state.values?.[name] || defaultValue || (allowNone ? NONE : "JOD");
  return (
    <FormField id={id} label={label} error={state.fieldErrors?.[name]}>
      <Select name={name} defaultValue={initial}>
        <SelectTrigger id={id} className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {allowNone && <SelectItem value={NONE}>{noneLabel}</SelectItem>}
          {CURRENCY_GROUPS.map((group) => (
            <SelectGroup key={group.label}>
              <SelectLabel>{group.label}</SelectLabel>
              {group.currencies.map((c) => (
                <SelectItem key={c.code} value={c.code}>
                  <span className="figures w-9 font-medium">{c.code}</span>
                  <span className="text-muted-foreground">{c.name}</span>
                </SelectItem>
              ))}
            </SelectGroup>
          ))}
        </SelectContent>
      </Select>
    </FormField>
  );
}

export function CountryField({
  name = "country",
  label = "Country",
  state,
  defaultValue,
}: {
  name?: string;
  label?: string;
  state: FormState;
  defaultValue?: string | null;
}) {
  const id = `${name}-${useId()}`;
  return (
    <FormField id={id} label={label} error={state.fieldErrors?.[name]}>
      <Select name={name} defaultValue={state.values?.[name] || defaultValue || NONE}>
        <SelectTrigger id={id} className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={NONE}>Not set</SelectItem>
          {COUNTRY_GROUPS.map((group) => (
            <SelectGroup key={group.label}>
              <SelectLabel>{group.label}</SelectLabel>
              {group.codes.map((code) => (
                <SelectItem key={code} value={code}>
                  <span className="figures w-7 font-medium">{code}</span>
                  <span className="text-muted-foreground">{countryName(code)}</span>
                </SelectItem>
              ))}
            </SelectGroup>
          ))}
        </SelectContent>
      </Select>
    </FormField>
  );
}
