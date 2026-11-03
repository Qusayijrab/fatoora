import Link from "next/link";
import { PageHeader } from "@/components/page-header";
import { ClientForm } from "../client-form";

export const metadata = { title: "New client" };

export default function NewClientPage() {
  return (
    <div className="max-w-4xl">
      <Link href="/clients" className="text-sm text-muted-foreground hover:text-foreground">
        ← Clients
      </Link>
      <div className="mt-3">
        <PageHeader title="New client" />
      </div>
      <div className="rounded-lg border bg-card p-5 sm:p-7">
        <ClientForm />
      </div>
    </div>
  );
}
