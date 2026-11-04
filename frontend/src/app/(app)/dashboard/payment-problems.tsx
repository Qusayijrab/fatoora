"use client";

import { TriangleAlert } from "lucide-react";
import Link from "next/link";
import { ConfirmAction } from "@/components/confirm-action";
import { Button } from "@/components/ui/button";
import { formatDate } from "@/lib/format";
import type { PaymentProblem } from "@/lib/types";
import { resolvePaymentProblem } from "../invoices/actions";

/**
 * Online payments the provider took but that could not be applied (the invoice was
 * already paid or voided, or the amount was over the balance). The client was charged,
 * so these stay here until someone refunds them and marks them handled.
 */
export function PaymentProblems({ problems, isAdmin }: { problems: PaymentProblem[]; isAdmin: boolean }) {
  return (
    <section
      className="rounded-xl border border-ink-overdue/40 bg-ink-overdue/5 p-5 sm:p-6"
      aria-labelledby="payment-problems-title"
      data-testid="payment-problems"
    >
      <h2 id="payment-problems-title" className="flex items-center gap-2.5 text-base font-semibold">
        <span aria-hidden className="grid size-7 place-items-center rounded-md bg-ink-overdue/10 text-ink-overdue">
          <TriangleAlert className="size-4" strokeWidth={2} />
        </span>
        {problems.length === 1 ? "An online payment needs attention" : `${problems.length} online payments need attention`}
      </h2>
      <p className="mt-2 text-sm text-muted-foreground">
        The client was charged, but the money could not be applied to the invoice. Refund it in your payment provider,
        then mark it handled.
      </p>
      <ul className="mt-3 divide-y">
        {problems.map((p) => (
          <li key={p.id} className="flex flex-wrap items-center justify-between gap-3 py-2.5 text-sm">
            <span className="min-w-0">
              <Link href={`/invoices/${p.invoiceId}`} className="figures font-mono text-primary hover:underline">
                {p.invoiceNumber}
              </Link>
              <span className="ms-2" dir="auto">
                {p.clientName}
              </span>
              <span className="block text-xs text-muted-foreground">
                {formatDate(p.receivedAt)} · {p.provider} · {p.detail}
              </span>
            </span>
            {isAdmin && (
              <ConfirmAction
                trigger={
                  <Button size="sm" variant="outline">
                    Mark handled
                  </Button>
                }
                title="Mark this payment as handled?"
                description="Do this after you have refunded the client in your payment provider. It is kept in the invoice's history."
                confirmLabel="Mark handled"
                destructive={false}
                successMessage="Marked as handled"
                action={() => resolvePaymentProblem(p.id)}
              />
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
