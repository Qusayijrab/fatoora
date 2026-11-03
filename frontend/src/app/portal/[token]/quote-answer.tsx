"use client";

import { Check, X } from "lucide-react";
import { useTransition } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { answerQuote } from "../actions";

export function QuoteAnswer({ token, quoteId, number }: { token: string; quoteId: string; number: string }) {
  const [pending, startTransition] = useTransition();
  function answer(accept: boolean) {
    startTransition(async () => {
      const r = await answerQuote(token, quoteId, accept);
      if (r.error) toast.error(r.error);
      else toast.success(accept ? `You accepted ${number}. Thank you!` : `You declined ${number}.`);
    });
  }
  return (
    <>
      <Button size="sm" disabled={pending} onClick={() => answer(true)} aria-label={`Accept ${number}`}>
        <Check /> Accept
      </Button>
      <Button size="sm" variant="outline" disabled={pending} onClick={() => answer(false)} aria-label={`Decline ${number}`}>
        <X /> Decline
      </Button>
    </>
  );
}
