"use client";

import { CircleCheck } from "lucide-react";
import Link from "next/link";
import { useActionState, useId } from "react";
import { FormError } from "@/components/form-error";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { FormState } from "@/lib/types";
import { resetPassword } from "../../actions";

export function ResetPasswordForm({ token }: { token: string }) {
  const uid = useId();
  const [state, action, pending] = useActionState<FormState, FormData>(resetPassword.bind(null, token), {});

  if (state.ok) {
    return (
      <div role="status" className="grid gap-4">
        <p className="flex items-center gap-2 font-medium">
          <CircleCheck className="size-5 text-primary" aria-hidden /> Your password was changed.
        </p>
        <Button asChild className="justify-self-start">
          <Link href="/login">Log in</Link>
        </Button>
      </div>
    );
  }

  return (
    <form action={action} className="grid gap-5">
      <FormError message={state.error} />
      <FormField id={`password-${uid}`} label="New password" error={state.fieldErrors?.password}>
        <Input id={`password-${uid}`} name="password" type="password" autoComplete="new-password" minLength={8} required className="h-10" />
      </FormField>
      <FormField id={`confirm-${uid}`} label="Repeat the new password" error={state.fieldErrors?.confirm}>
        <Input id={`confirm-${uid}`} name="confirm" type="password" autoComplete="new-password" minLength={8} required className="h-10" />
      </FormField>
      <Button type="submit" size="lg" className="h-10" disabled={pending}>
        {pending ? "Saving…" : "Set new password"}
      </Button>
    </form>
  );
}
