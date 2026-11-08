"use client";

import { Copy } from "lucide-react";
import { useTransition } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import type { Invoice } from "@/lib/types";
import { duplicateInvoice } from "../actions";

/** Actions in the invoice page header. */
export function InvoiceHeaderActions({ invoice }: { invoice: Invoice }) {
  const [pending, startTransition] = useTransition();
  if (invoice.kind !== "INVOICE") return null;
  return (
    <Button
      variant="outline"
      disabled={pending}
      onClick={() =>
        startTransition(async () => {
          const result = await duplicateInvoice(invoice.id);
          if (result?.error) toast.error(result.error);
        })
      }
    >
      <Copy /> {pending ? "Copying…" : "Duplicate"}
    </Button>
  );
}
