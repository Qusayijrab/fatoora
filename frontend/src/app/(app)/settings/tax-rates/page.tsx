import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { getMe, getTaxRates } from "@/lib/api";
import { canManageMembers } from "@/lib/types";
import { TaxRateRows } from "./tax-rate-rows";

export const metadata = { title: "Tax rates" };

export default function TaxRatesPage() {
  return (
    <div className="max-w-3xl">
      <PageHeader
        title="Tax rates"
        description="Sales tax applied per invoice line and rounded per line, half up. Archive a rate instead of deleting it: past invoices keep the rate they were issued with."
      />
      <Suspense fallback={<Skeleton className="h-64 w-full" />}>
        <TaxRates />
      </Suspense>
    </div>
  );
}

async function TaxRates() {
  const [rates, me] = await Promise.all([getTaxRates(true), getMe()]);
  return <TaxRateRows rates={rates} canEdit={canManageMembers(me.organization?.role)} />;
}
