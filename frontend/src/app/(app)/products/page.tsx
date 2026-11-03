import { Package, Plus } from "lucide-react";
import Link from "next/link";
import { Suspense } from "react";
import { EmptyState } from "@/components/empty-state";
import { ListSearch } from "@/components/list-search";
import { PageHeader } from "@/components/page-header";
import { StatusStamp } from "@/components/status-stamp";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getProducts } from "@/lib/api";
import { formatMoney } from "@/lib/format";

export const metadata = { title: "Products" };

type Search = { q?: string; archived?: string };

export default function ProductsPage({ searchParams }: PageProps<"/products">) {
  return (
    <>
      <PageHeader
        title="Products"
        description="What you sell, priced once. Lines on quotes and invoices copy the price, so changing it later never touches a sent document."
        actions={
          <Button asChild>
            <Link href="/products/new">
              <Plus /> New product
            </Link>
          </Button>
        }
      />
      <Suspense fallback={<Skeleton className="h-72 w-full" />}>
        <ProductList searchParams={searchParams as Promise<Search>} />
      </Suspense>
    </>
  );
}

async function ProductList({ searchParams }: { searchParams: Promise<Search> }) {
  const { q, archived } = await searchParams;
  const includeArchived = archived === "1";
  const page = await getProducts({ q, includeArchived, size: 100 });

  if (page.total === 0 && !q && !includeArchived) {
    return (
      <EmptyState
        icon={Package}
        title="No products yet"
        action={
          <Button asChild>
            <Link href="/products/new">Add your first product</Link>
          </Button>
        }
      >
        Services by the hour, monthly retainers, physical goods: set the price and tax once and pick them from a list
        when you write a quote or invoice.
      </EmptyState>
    );
  }

  return (
    <>
      <ListSearch action="/products" q={q} includeArchived={includeArchived} placeholder="Search name or SKU" />
      <div className="overflow-hidden rounded-lg border bg-card">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/50 hover:bg-muted/50">
              <TableHead className="ps-5">Product</TableHead>
              <TableHead className="hidden sm:table-cell">Unit</TableHead>
              <TableHead className="hidden md:table-cell">Tax</TableHead>
              <TableHead className="pe-5 text-end">Price</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {page.items.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} className="py-10 text-center text-muted-foreground">
                  No products match “{q}”.
                </TableCell>
              </TableRow>
            )}
            {page.items.map((p) => (
              <TableRow key={p.id} data-testid="product-row" className={p.archived ? "opacity-60" : undefined}>
                <TableCell className="py-3 ps-5">
                  <Link href={`/products/${p.id}`} className="font-medium hover:underline">
                    {p.name}
                  </Link>
                  {p.archived && <StatusStamp status="VOID" label="Archived" className="ms-2" />}
                  {p.sku && <p className="figures text-xs text-muted-foreground">{p.sku}</p>}
                </TableCell>
                <TableCell className="hidden text-muted-foreground sm:table-cell">per {p.unit}</TableCell>
                <TableCell className="hidden text-muted-foreground md:table-cell">
                  {p.taxRate ? `${p.taxRate.name} ${p.taxRate.rate}%` : "No tax"}
                </TableCell>
                <TableCell className="figures pe-5 text-end font-medium">{formatMoney(p.unitPrice)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <p className="figures mt-3 text-xs text-muted-foreground">
        {page.total} {page.total === 1 ? "product" : "products"}
      </p>
    </>
  );
}
