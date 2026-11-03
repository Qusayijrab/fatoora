"use client";

import { FormField, selectClassName } from "@/components/form-field";
import { Input } from "@/components/ui/input";

const CURRENCIES = ["JOD", "USD", "EUR", "GBP", "SAR", "AED", "KWD", "QAR", "BHD", "OMR", "EGP", "JPY"];

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
        <Input id={nameField} name={nameField} required defaultValue={values?.[nameField]} />
      </FormField>
      <div className="grid grid-cols-2 gap-4">
        <FormField id="defaultCurrency" label="Currency" error={errors?.defaultCurrency}>
          <select
            id="defaultCurrency"
            name="defaultCurrency"
            className={selectClassName}
            defaultValue={values?.defaultCurrency ?? "JOD"}
          >
            {CURRENCIES.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </FormField>
        <FormField id="timeZone" label="Time zone" error={errors?.timeZone}>
          <Input
            id="timeZone"
            name="timeZone"
            required
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
