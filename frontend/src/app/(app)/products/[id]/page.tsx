import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { StatusStamp } from "@/components/status-stamp";
import { Skeleton } from "@/components/ui/skeleton";
import { getMe, getProduct, getTaxRates } from "@/lib/api";
import { formatMoney } from "@/lib/format";
import { ProductForm } from "../product-form";
import { ArchiveProductButton } from "./archive-button";

export default function ProductPage({ params }: PageProps<"/products/[id]">) {
  return (
    <div className="max-w-2xl">
      <Link href="/products" className="text-sm text-muted-foreground hover:text-foreground">
        ← Products
      </Link>
      <Suspense fallback={<Skeleton className="mt-4 h-96 w-full" />}>
        <ProductDetail params={params} />
      </Suspense>
    </div>
  );
}

async function ProductDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [product, taxRates, me] = await Promise.all([getProduct(id), getTaxRates(), getMe()]);

  return (
    <>
      <div className="mt-3">
        <PageHeader
          title={
            <span className="inline-flex flex-wrap items-center gap-3">
              {product.name}
              {product.archived && <StatusStamp status="VOID" label="Archived" />}
            </span>
          }
          description={
            <span className="figures">
              {formatMoney(product.unitPrice)} per {product.unit}
            </span>
          }
          actions={<ArchiveProductButton id={product.id} name={product.name} archived={product.archived} />}
        />
      </div>
      <div className="rounded-lg border bg-card p-5 sm:p-7">
        <ProductForm
          key={product.id}
          product={product}
          taxRates={taxRates}
          defaultCurrency={me.organization?.defaultCurrency ?? "JOD"}
        />
      </div>
    </>
  );
}
