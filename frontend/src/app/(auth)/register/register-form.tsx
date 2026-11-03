"use client";

import { useActionState } from "react";
import { FormError } from "@/components/form-error";
import { FormField } from "@/components/form-field";
import { OrganizationFields } from "@/components/organization-fields";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { FormState } from "@/lib/types";
import { register } from "../actions";

export function RegisterForm() {
  const [state, action, pending] = useActionState<FormState, FormData>(register, {});
  const errors = state.fieldErrors;

  return (
    <form action={action} className="grid gap-5">
      <FormError message={state.error} />
      <fieldset className="grid gap-4">
        <legend className="mb-1 text-xs font-medium uppercase tracking-[0.1em] text-muted-foreground">You</legend>
        <FormField id="fullName" label="Your name" error={errors?.fullName}>
          <Input id="fullName" name="fullName" autoComplete="name" required className="h-10" defaultValue={state.values?.fullName} />
        </FormField>
        <FormField id="email" label="Email" error={errors?.email}>
          <Input id="email" name="email" type="email" autoComplete="email" required className="h-10" defaultValue={state.values?.email} />
        </FormField>
        <FormField id="password" label="Password (8+ characters)" error={errors?.password}>
          <Input id="password" name="password" type="password" autoComplete="new-password" minLength={8} required className="h-10" />
        </FormField>
      </fieldset>
      <fieldset className="grid gap-4 border-t pt-5">
        <legend className="mb-1 text-xs font-medium uppercase tracking-[0.1em] text-muted-foreground">Your business</legend>
        <OrganizationFields values={state.values} errors={errors} />
      </fieldset>
      <Button type="submit" size="lg" className="h-10" disabled={pending}>
        {pending ? "Creating account…" : "Create account"}
      </Button>
    </form>
  );
}
