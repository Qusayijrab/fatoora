import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { getEditorData } from "@/lib/api";
import { NewQuoteEditor } from "./new-quote-editor";

export const metadata = { title: "New quote" };

export default function NewQuotePage() {
  return (
    <div className="max-w-5xl">
      <Link href="/quotes" className="text-sm text-muted-foreground hover:text-foreground">
        ← Quotes
      </Link>
      <div className="mt-3">
        <PageHeader title="New quote" description="Saved as a draft. It is numbered when you send it." />
      </div>
      <div className="rounded-xl border bg-card shadow-xs p-5 sm:p-7">
        <Suspense fallback={<Skeleton className="h-96 w-full" />}>
          <Editor />
        </Suspense>
      </div>
    </div>
  );
}

async function Editor() {
  return <NewQuoteEditor data={await getEditorData()} />;
}
