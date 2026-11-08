import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Skeleton } from "@/components/ui/skeleton";
import { getClient, getMe } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { ClientForm } from "../client-form";
import { ArchiveClientButton } from "./archive-button";
import { PortalBox } from "./portal-box";
import { StatementBox } from "./statement-box";

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
  const [client, me] = await Promise.all([getClient(id), getMe()]);
  // "Today" in the organization's time zone, for the statement's default period.
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: me.organization?.timeZone ?? "UTC" }).format(new Date());

  return (
    <>
      <div className="mt-3">
        <PageHeader
          title={
            <span className="inline-flex flex-wrap items-center gap-3">
              {client.name}
              {client.archived && <StatusBadge status="VOID" label="Archived" />}
            </span>
          }
          description={`Client since ${formatDate(client.createdAt)}`}
          actions={<ArchiveClientButton id={client.id} name={client.name} archived={client.archived} />}
        />
      </div>
      <div className="grid gap-6">
        {!client.archived && <PortalBox clientId={client.id} />}
        <StatementBox clientId={client.id} today={today} />
        <div className="rounded-xl border bg-card shadow-xs p-5 sm:p-7">
          <ClientForm key={client.id} client={client} />
        </div>
      </div>
    </>
  );
}
