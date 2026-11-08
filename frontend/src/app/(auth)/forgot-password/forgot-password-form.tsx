"use client";

import { MailCheck } from "lucide-react";
import { useActionState, useId } from "react";
import { FormError } from "@/components/form-error";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { FormState } from "@/lib/types";
import { forgotPassword } from "../actions";

export function ForgotPasswordForm() {
  const uid = useId();
  const [state, action, pending] = useActionState<FormState, FormData>(forgotPassword, {});

  if (state.ok) {
    return (
      <div role="status" className="grid gap-3 text-sm">
        <p className="flex items-center gap-2 font-medium">
          <MailCheck className="size-5 text-primary" aria-hidden /> Check your inbox
        </p>
        <p className="text-muted-foreground">
          If <span dir="ltr">{state.values?.email}</span> has a Fatoora account, we sent it a link to choose a new password.
          It works once and expires in 1 hour. Nothing there? Check the spam folder.
        </p>
      </div>
    );
  }

  return (
    <form action={action} className="grid gap-5">
      <FormError message={state.error} />
      <FormField id={`email-${uid}`} label="Email" error={state.fieldErrors?.email}>
        <Input id={`email-${uid}`} name="email" type="email" autoComplete="email" required className="h-10" defaultValue={state.values?.email} />
      </FormField>
      <Button type="submit" size="lg" className="h-10" disabled={pending}>
        {pending ? "Sending…" : "Send reset link"}
      </Button>
    </form>
  );
}
