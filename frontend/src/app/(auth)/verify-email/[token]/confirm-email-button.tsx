"use client";

import { CircleCheck } from "lucide-react";
import Link from "next/link";
import { useState, useTransition } from "react";
import { Button } from "@/components/ui/button";
import type { FormState } from "@/lib/types";
import { verifyEmail } from "../../actions";

export function ConfirmEmailButton({ token }: { token: string }) {
  const [state, setState] = useState<FormState>({});
  const [pending, startTransition] = useTransition();

  if (state.ok) {
    return (
      <div className="grid gap-4" role="status">
        <p className="flex items-center gap-2 font-medium">
          <CircleCheck className="size-5 text-primary" aria-hidden /> Your email address is confirmed.
        </p>
        <Button asChild className="justify-self-start">
          <Link href="/dashboard">Go to Fatoora</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="grid gap-4">
      {state.error && (
        <p role="alert" className="text-sm text-destructive">
          {state.error}
        </p>
      )}
      <Button
        className="justify-self-start"
        disabled={pending}
        onClick={() => startTransition(async () => setState(await verifyEmail(token)))}
      >
        {pending ? "Confirming…" : "Confirm my email"}
      </Button>
    </div>
  );
}
