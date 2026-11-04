import { FlaskConical } from "lucide-react";
import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { api, ApiError } from "@/lib/api";
import { formatMoney } from "@/lib/format";
import type { MockCheckout } from "@/lib/types";
import { MockPayForm } from "./mock-pay-form";

export const metadata: Metadata = { title: "Test checkout", robots: { index: false, follow: false } };

/**
 * Stands in for a payment provider's hosted page during development. Clearly labelled:
 * no card details are asked for and no money moves. Confirming makes the mock provider
 * send a signed webhook, exactly like a real one.
 */
export default function MockCheckoutPage({ params }: PageProps<"/pay/mock/[sessionId]">) {
  return (
    <Suspense fallback={<Skeleton className="h-80 w-full" />}>
      <Checkout params={params} />
    </Suspense>
  );
}

async function Checkout({ params }: { params: Promise<{ sessionId: string }> }) {
  const { sessionId } = await params;
  let checkout: MockCheckout;
  try {
    checkout = await api<MockCheckout>(`/public/mock-checkout/${encodeURIComponent(sessionId)}`, { auth: false });
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      return <p className="rounded-xl border bg-card shadow-xs p-8 text-center">This checkout does not exist.</p>;
    }
    throw e;
  }

  return (
    <section className="overflow-hidden rounded-lg border-2 border-dashed border-ink-expired/50 bg-card">
      <div className="flex items-center gap-2 bg-ink-expired/10 px-6 py-3 text-sm font-medium text-ink-expired">
        <FlaskConical className="size-4" /> Test checkout · no real money moves
      </div>
      <div className="grid gap-6 p-6 sm:p-8">
        <div>
          <p className="text-sm text-muted-foreground" dir="auto">
            Paying {checkout.organizationName}
          </p>
          <p className="figures mt-1 text-3xl font-semibold" data-testid="mock-amount">
            {formatMoney(checkout.amount)}
          </p>
          <p className="figures mt-1 text-sm text-muted-foreground">Invoice {checkout.invoiceNumber}</p>
        </div>
        {checkout.status === "OPEN" ? (
          <MockPayForm sessionId={sessionId} amount={formatMoney(checkout.amount)} cancelHref={`/pay/${checkout.payToken}?status=cancelled`} />
        ) : (
          <p className="text-sm text-muted-foreground">
            {checkout.status === "EXPIRED"
              ? "This checkout has expired. Go back to the invoice and press Pay again."
              : "This checkout is already finished."}
          </p>
        )}
      </div>
    </section>
  );
}
