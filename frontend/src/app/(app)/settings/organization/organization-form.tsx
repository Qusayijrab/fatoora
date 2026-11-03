"use client";

import type { ReactNode } from "react";
import { useActionState } from "react";
import { CountryField, CurrencyField, TextField } from "@/components/fields";
import { FormError } from "@/components/form-error";
import { Button } from "@/components/ui/button";
import { useActionFeedback } from "@/hooks/use-action-feedback";
import type { FormState, OrganizationDetails } from "@/lib/types";
import { saveOrganization } from "../actions";

export function OrganizationForm({ org, canEdit }: { org: OrganizationDetails; canEdit: boolean }) {
  const [state, action, pending] = useActionState<FormState, FormData>(saveOrganization, {});
  useActionFeedback(state, "Organization saved");

  return (
    <form action={action} className="grid gap-8">
      {state.error && <FormError message={state.fieldErrors ? "Some fields need attention." : state.error} />}
      <fieldset disabled={!canEdit} className="grid gap-8">
        <Section title="Business" description="The trading name shows in the app; the legal name and tax number go on invoices.">
          <TextField name="name" label="Trading name" state={state} defaultValue={org.name} required />
          <TextField name="legalName" label="Legal name" state={state} defaultValue={org.legalName} />
          <TextField name="taxId" label="Tax number" state={state} defaultValue={org.taxId} className="figures" />
          <TextField name="email" label="Billing email" type="email" state={state} defaultValue={org.email} />
          <TextField name="phone" label="Phone" type="tel" state={state} defaultValue={org.phone} />
        </Section>
        <Section title="Address" description="Printed under your name on every invoice.">
          <div className="sm:col-span-2">
            <TextField name="addressLine1" label="Address" state={state} defaultValue={org.addressLine1} />
          </div>
          <div className="sm:col-span-2">
            <TextField name="addressLine2" label="Address line 2" state={state} defaultValue={org.addressLine2} />
          </div>
          <TextField name="city" label="City" state={state} defaultValue={org.city} />
          <CountryField state={state} defaultValue={org.country} />
        </Section>
        <Section title="Defaults" description="Changing these affects new documents only. Issued invoices keep their currency and dates.">
          <CurrencyField name="defaultCurrency" label="Default currency" state={state} defaultValue={org.defaultCurrency} />
          <TextField name="timeZone" label="Time zone" state={state} defaultValue={org.timeZone} hint="Due dates and reminders use this zone." />
        </Section>
      </fieldset>
      {canEdit ? (
        <div className="flex justify-end border-t pt-5">
          <Button type="submit" disabled={pending} className="min-w-32">
            {pending ? "Saving…" : "Save changes"}
          </Button>
        </div>
      ) : (
        <p className="border-t pt-5 text-sm text-muted-foreground">Only owners and admins can change these details.</p>
      )}
    </form>
  );
}

function Section({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <section className="grid gap-6 border-t pt-6 first:border-t-0 first:pt-0 md:grid-cols-[14rem_1fr] md:gap-10">
      <div>
        <h2 className="text-sm font-semibold">{title}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">{children}</div>
    </section>
  );
}
