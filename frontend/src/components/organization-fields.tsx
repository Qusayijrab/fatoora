"use client";

import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from "@/components/ui/select";
import { CURRENCY_GROUPS } from "@/lib/currencies";

/** Organization name, currency and time zone: shared by sign-up and "new organization". */
export function OrganizationFields({
  values,
  errors,
  nameField = "organizationName",
}: {
  values?: Record<string, string>;
  errors?: Record<string, string>;
  nameField?: string;
}) {
  return (
    <>
      <FormField id={nameField} label="Business name" error={errors?.[nameField]}>
        <Input id={nameField} name={nameField} required className="h-10" defaultValue={values?.[nameField]} />
      </FormField>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="defaultCurrency" label="Currency" error={errors?.defaultCurrency}>
          <Select name="defaultCurrency" defaultValue={values?.defaultCurrency ?? "JOD"}>
            <SelectTrigger id="defaultCurrency" className="h-10! w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
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
        <FormField id="timeZone" label="Time zone" error={errors?.timeZone}>
          <Input
            id="timeZone"
            name="timeZone"
            required
            className="h-10"
            defaultValue={values?.timeZone ?? "UTC"}
            // Pre-fill with the browser's zone (e.g. Asia/Amman) once, without a re-render.
            ref={(el) => {
              if (el && !values?.timeZone && !el.dataset.filled) {
                el.value = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
                el.dataset.filled = "1";
              }
            }}
          />
        </FormField>
      </div>
    </>
  );
}
