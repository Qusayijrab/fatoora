import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { getMe, getTaxRates } from "@/lib/api";
import { ProductForm } from "../product-form";

export const metadata = { title: "New product" };

export default function NewProductPage() {
  return (
    <div className="max-w-2xl">
      <Link href="/products" className="text-sm text-muted-foreground hover:text-foreground">
        ← Products
      </Link>
      <div className="mt-3">
        <PageHeader title="New product" />
      </div>
      <div className="rounded-lg border bg-card p-5 sm:p-7">
        <Suspense fallback={<Skeleton className="h-80 w-full" />}>
          <NewProduct />
        </Suspense>
      </div>
    </div>
  );
}

async function NewProduct() {
  const [me, taxRates] = await Promise.all([getMe(), getTaxRates()]);
  return <ProductForm taxRates={taxRates} defaultCurrency={me.organization?.defaultCurrency ?? "JOD"} />;
}
