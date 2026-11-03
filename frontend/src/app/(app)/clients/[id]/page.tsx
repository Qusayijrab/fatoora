import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { StatusStamp } from "@/components/status-stamp";
import { Skeleton } from "@/components/ui/skeleton";
import { getClient } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { ClientForm } from "../client-form";
import { ArchiveClientButton } from "./archive-button";

export default function ClientPage({ params }: PageProps<"/clients/[id]">) {
  return (
    <div className="max-w-4xl">
      <Link href="/clients" className="text-sm text-muted-foreground hover:text-foreground">
        ← Clients
      </Link>
      <Suspense fallback={<Skeleton className="mt-4 h-96 w-full" />}>
        <ClientDetail params={params} />
      </Suspense>
    </div>
  );
}

async function ClientDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const client = await getClient(id);

  return (
    <>
      <div className="mt-3">
        <PageHeader
          title={
            <span className="inline-flex flex-wrap items-center gap-3">
              {client.name}
              {client.archived && <StatusStamp status="VOID" label="Archived" />}
            </span>
          }
          description={`Client since ${formatDate(client.createdAt)}`}
          actions={<ArchiveClientButton id={client.id} name={client.name} archived={client.archived} />}
        />
      </div>
      <div className="rounded-lg border bg-card p-5 sm:p-7">
        <ClientForm key={client.id} client={client} />
      </div>
    </>
  );
}
