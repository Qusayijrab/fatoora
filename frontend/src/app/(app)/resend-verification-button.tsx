"use client";

import { useTransition } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { resendVerificationEmail } from "./actions";

export function ResendVerificationButton() {
  const [pending, startTransition] = useTransition();
  return (
    <Button
      size="sm"
      variant="outline"
      disabled={pending}
      onClick={() =>
        startTransition(async () => {
          const result = await resendVerificationEmail();
          if (result.error) toast.error(result.error);
          else toast.success("New link sent. Check your inbox.");
        })
      }
    >
      {pending ? "Sending…" : "Resend link"}
    </Button>
  );
}
