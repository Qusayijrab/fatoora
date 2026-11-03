"use client";

import Link from "next/link";
import { useTransition } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { completeMockCheckout } from "../../actions";

export function MockPayForm({ sessionId, amount, cancelHref }: { sessionId: string; amount: string; cancelHref: string }) {
  const [pending, startTransition] = useTransition();
  return (
    <div className="grid gap-3">
      <div className="figures rounded-md border bg-muted/40 px-4 py-3 font-mono text-sm text-muted-foreground">
        Test card 4242 4242 4242 4242 · 12/34 · 123
      </div>
      <Button
        size="lg"
        className="h-12 text-base"
        disabled={pending}
        onClick={() =>
          startTransition(async () => {
            const result = await completeMockCheckout(sessionId);
            if (result?.error) toast.error(result.error);
          })
        }
      >
        {pending ? "Processing…" : `Confirm test payment of ${amount}`}
      </Button>
      <Link href={cancelHref} className="text-center text-sm text-muted-foreground hover:text-foreground">
        Cancel
      </Link>
    </div>
  );
}
