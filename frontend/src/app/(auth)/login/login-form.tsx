"use client";

import { useActionState } from "react";
import { FormError } from "@/components/form-error";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { FormState } from "@/lib/types";
import { login } from "../actions";

export function LoginForm() {
  const [state, action, pending] = useActionState<FormState, FormData>(login, {});

  return (
    <form action={action} className="grid gap-5">
      <FormError message={state.error} />
      <FormField id="email" label="Email" error={state.fieldErrors?.email}>
        <Input id="email" name="email" type="email" autoComplete="email" required className="h-10" defaultValue={state.values?.email} />
      </FormField>
      <FormField id="password" label="Password" error={state.fieldErrors?.password}>
        <Input id="password" name="password" type="password" autoComplete="current-password" required className="h-10" />
      </FormField>
      <Button type="submit" size="lg" className="h-10" disabled={pending}>
        {pending ? "Logging in…" : "Log in"}
      </Button>
    </form>
  );
}
