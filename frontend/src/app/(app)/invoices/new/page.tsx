import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { getEditorData } from "@/lib/api";
import { NewInvoiceEditor } from "./new-invoice-editor";

export const metadata = { title: "New invoice" };

export default function NewInvoicePage({ searchParams }: PageProps<"/invoices/new">) {
  return (
    <div className="max-w-5xl">
      <Link href="/invoices" className="text-sm text-muted-foreground hover:text-foreground">
        ← Invoices
      </Link>
      <div className="mt-3">
        <PageHeader title="New invoice" description="Saved as a draft. It gets its number when you issue it." />
      </div>
      <div className="rounded-xl border bg-card shadow-xs p-5 sm:p-7">
        <Suspense fallback={<Skeleton className="h-96 w-full" />}>
          <Editor searchParams={searchParams as Promise<{ client?: string }>} />
        </Suspense>
      </div>
    </div>
  );
}

async function Editor({ searchParams }: { searchParams: Promise<{ client?: string }> }) {
  const { client } = await searchParams;
  const data = await getEditorData(client);
  return <NewInvoiceEditor data={data} presetClientId={client} />;
}
