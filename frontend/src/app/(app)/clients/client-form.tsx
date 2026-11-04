"use client";

import { useActionState } from "react";
import { CountryField, CurrencyField, TextAreaField, TextField } from "@/components/fields";
import { FormError } from "@/components/form-error";
import { Button } from "@/components/ui/button";
import { useActionFeedback } from "@/hooks/use-action-feedback";
import type { Client, FormState } from "@/lib/types";
import { saveClient } from "./actions";

/** One form for create and edit. Sections mirror what ends up printed on an invoice. */
export function ClientForm({ client }: { client?: Client }) {
  const [state, action, pending] = useActionState<FormState, FormData>(saveClient.bind(null, client?.id ?? null), {});
  useActionFeedback(state, "Client saved");

  return (
    <form action={action} className="grid gap-8">
      {state.error && state.fieldErrors && <FormError message="Some fields need attention." />}
      {state.error && !state.fieldErrors && <FormError message={state.error} />}

      <Section title="Details" description="The name exactly as it should appear on invoices. Arabic or English.">
        <div className="sm:col-span-2">
          <TextField name="name" label="Client name" state={state} defaultValue={client?.name} required autoFocus={!client} />
        </div>
        <TextField name="email" label="Billing email" type="email" state={state} defaultValue={client?.email} hint="Invoices and reminders go here." />
        <TextField name="phone" label="Phone" type="tel" state={state} defaultValue={client?.phone} />
        <TextField name="taxId" label="Tax number" state={state} defaultValue={client?.taxId} hint="Printed on invoices when set." />
        <CurrencyField state={state} defaultValue={client?.currency} allowNone label="Bill in" />
      </Section>

      <Section title="Address" description="Snapshotted onto each invoice when it is issued.">
        <div className="sm:col-span-2">
          <TextField name="addressLine1" label="Address" state={state} defaultValue={client?.addressLine1} />
        </div>
        <div className="sm:col-span-2">
          <TextField name="addressLine2" label="Address line 2" state={state} defaultValue={client?.addressLine2} />
        </div>
        <TextField name="city" label="City" state={state} defaultValue={client?.city} />
        <TextField name="region" label="Region / governorate" state={state} defaultValue={client?.region} />
        <TextField name="postalCode" label="Postal code" state={state} defaultValue={client?.postalCode} />
        <CountryField state={state} defaultValue={client?.country} />
      </Section>

      <Section title="Notes" description="Private. Never shown to the client.">
        <div className="sm:col-span-2">
          <TextAreaField name="notes" label="Internal notes" state={state} defaultValue={client?.notes} />
        </div>
      </Section>

      <div className="flex justify-end border-t pt-5">
        <Button type="submit" disabled={pending} className="min-w-32">
          {pending ? "Saving…" : client ? "Save changes" : "Add client"}
        </Button>
      </div>
    </form>
  );
}

function Section({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return (
    <fieldset className="grid gap-6 border-t pt-6 first-of-type:border-t-0 first-of-type:pt-0 md:grid-cols-[14rem_1fr] md:gap-10">
      <legend className="sr-only">{title}</legend>
      <div>
        <h2 className="text-sm font-semibold">{title}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">{children}</div>
    </fieldset>
  );
}
