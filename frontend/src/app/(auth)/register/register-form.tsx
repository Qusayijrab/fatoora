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
    <form action={action} className="grid gap-4">
      <FormError message={state.error} />
      <FormField id="fullName" label="Your name" error={errors?.fullName}>
        <Input id="fullName" name="fullName" autoComplete="name" required defaultValue={state.values?.fullName} />
      </FormField>
      <FormField id="email" label="Email" error={errors?.email}>
        <Input id="email" name="email" type="email" autoComplete="email" required defaultValue={state.values?.email} />
      </FormField>
      <FormField id="password" label="Password (8+ characters)" error={errors?.password}>
        <Input id="password" name="password" type="password" autoComplete="new-password" minLength={8} required />
      </FormField>
      <OrganizationFields values={state.values} errors={errors} />
      <Button type="submit" disabled={pending}>
        {pending ? "Creating account…" : "Create account"}
      </Button>
    </form>
  );
}
