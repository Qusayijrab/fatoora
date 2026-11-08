"use client";

import { Copy, MessageCircle } from "lucide-react";
import { useTransition } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { formatDate, formatMoney } from "@/lib/format";
import type { Invoice } from "@/lib/types";
import { whatsappNumber, whatsappUrl } from "@/lib/whatsapp";
import { duplicateInvoice, getPaymentLink } from "../actions";

/** Actions in the invoice page header. */
export function InvoiceHeaderActions({ invoice }: { invoice: Invoice }) {
  const [pending, startTransition] = useTransition();
  const [sending, startSending] = useTransition();
  if (invoice.kind !== "INVOICE") return null;
  const open =
    invoice.status === "ISSUED" ||
    invoice.status === "PARTIALLY_PAID" ||
    invoice.status === "OVERDUE";
  const number = whatsappNumber(invoice.billTo?.phone, invoice.billTo?.country);

  function sendOnWhatsApp() {
    // Opened right away (inside the click), then pointed at WhatsApp, so popup blockers allow it.
    const tab = window.open("about:blank", "_blank");
    startSending(async () => {
      const result = await getPaymentLink(invoice.id);
      if (!result.url) {
        tab?.close();
        toast.error(result.error ?? "Could not create the payment link");
        return;
      }
      const seller = invoice.seller?.name ?? "us";
      const text =
        `Hello ${invoice.client.name}, here is invoice ${invoice.number} from ${seller} for ${formatMoney(invoice.balanceDue)}` +
        (invoice.dueDate ? `, due ${formatDate(invoice.dueDate)}` : "") +
        `. You can view and pay it here: ${result.url}

` +
        `مرحباً، هذه الفاتورة ${invoice.number} من ${seller}. يمكنكم عرضها ودفعها من الرابط: ${result.url}`;
      const url = whatsappUrl(number, text);
      if (tab) tab.location.href = url;
      else window.location.href = url;
    });
  }

  return (
    <>
      {open && (
        <Button
          variant="outline"
          disabled={sending}
          onClick={sendOnWhatsApp}
          data-testid="whatsapp"
        >
          <MessageCircle />{" "}
          {sending
            ? "Opening…"
            : number
              ? "Send on WhatsApp"
              : "Share on WhatsApp"}
        </Button>
      )}
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
    </>
  );
}
