"use client";

import { Lock } from "lucide-react";
import { useTransition } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { startCheckout } from "../actions";

export function PayButton({ token, amount, provider }: { token: string; amount: string; provider: string }) {
  const [pending, startTransition] = useTransition();
  return (
    <div className="grid gap-2">
      <Button
        size="lg"
        className="h-12 bg-saffron text-base text-saffron-foreground hover:bg-saffron/90"
        disabled={pending}
        onClick={() =>
          startTransition(async () => {
            const result = await startCheckout(token);
            if (result?.error) toast.error(result.error);
          })
        }
      >
        <Lock /> {pending ? "Opening secure checkout…" : `Pay ${amount}`}
      </Button>
      <p className="text-center text-xs text-muted-foreground">
        {provider === "mock" ? "Test mode: no real money is charged." : "You will be taken to our payment provider's secure page."}
      </p>
    </div>
  );
}
