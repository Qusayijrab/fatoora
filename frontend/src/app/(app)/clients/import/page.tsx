import Link from "next/link";
import { CsvImport } from "@/components/csv-import";
import { PageHeader } from "@/components/page-header";

export const metadata = { title: "Import clients" };

export default function ImportClientsPage() {
  return (
    <div className="max-w-3xl">
      <Link href="/clients" className="text-sm text-muted-foreground hover:text-foreground">
        ← Clients
      </Link>
      <div className="mt-3">
        <PageHeader title="Import clients" description="Bring your existing clients over from a spreadsheet." />
      </div>
      <CsvImport kind="clients" />
    </div>
  );
}
