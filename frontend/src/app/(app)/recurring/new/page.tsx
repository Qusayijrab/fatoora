import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { getEditorData } from "@/lib/api";
import { RecurringForm } from "../recurring-form";

export const metadata = { title: "New recurring invoice" };

export default function NewRecurringPage() {
  return (
    <div className="max-w-5xl">
      <Link href="/recurring" className="text-sm text-muted-foreground hover:text-foreground">
        ← Recurring invoices
      </Link>
      <div className="mt-3">
        <PageHeader title="New recurring invoice" />
      </div>
      <div className="rounded-lg border bg-card p-5 sm:p-7">
        <Suspense fallback={<Skeleton className="h-96 w-full" />}>
          <Form />
        </Suspense>
      </div>
    </div>
  );
}

async function Form() {
  return <RecurringForm data={await getEditorData()} />;
}
