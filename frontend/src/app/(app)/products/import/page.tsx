import Link from "next/link";
import { CsvImport } from "@/components/csv-import";
import { PageHeader } from "@/components/page-header";

export const metadata = { title: "Import products" };

export default function ImportProductsPage() {
  return (
    <div className="max-w-3xl">
      <Link href="/products" className="text-sm text-muted-foreground hover:text-foreground">
        ← Products
      </Link>
      <div className="mt-3">
        <PageHeader title="Import products" description="Bring your existing products over from a spreadsheet." />
      </div>
      <CsvImport kind="products" />
    </div>
  );
}
