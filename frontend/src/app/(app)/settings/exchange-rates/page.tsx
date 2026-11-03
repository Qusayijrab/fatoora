import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { getCurrentOrganization, getExchangeRates } from "@/lib/api";
import { canManageMembers } from "@/lib/types";
import { RatesEditor } from "./rates-editor";

export const metadata = { title: "Exchange rates" };

export default function ExchangeRatesPage() {
  return (
    <div className="max-w-3xl">
      <PageHeader
        title="Exchange rates"
        description="When you issue an invoice in another currency, its rate is frozen on it, so reports never change afterwards. Fatoora uses your own rate first, then official US-dollar pegs (JOD, SAR, AED, QAR, BHD, OMR), then European Central Bank reference rates."
      />
      <Suspense fallback={<Skeleton className="h-64 w-full" />}>
        <Rates />
      </Suspense>
    </div>
  );
}

async function Rates() {
  const [rates, org] = await Promise.all([getExchangeRates(), getCurrentOrganization()]);
  return <RatesEditor rates={rates} base={org.defaultCurrency} canEdit={canManageMembers(org.role)} />;
}
