import Link from "next/link";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { StatusTabs } from "@/components/status-tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiError, getAudit } from "@/lib/api";
import { formatDate } from "@/lib/format";

export const metadata = { title: "Audit log" };

const TYPES = [
  { label: "Everything" },
  { value: "invoice", label: "Invoices" },
  { value: "quote", label: "Quotes" },
  { value: "client", label: "Clients" },
  { value: "membership", label: "Members" },
  { value: "recurring", label: "Recurring" },
  { value: "organization", label: "Organization" },
];

const LINKS: Record<string, string> = { invoice: "/invoices", quote: "/quotes", client: "/clients", recurring: "/recurring", product: "/products" };

export default function AuditPage({ searchParams }: PageProps<"/settings/audit">) {
  return (
    <>
      <PageHeader
        title="Audit log"
        description="Every create, issue, payment, void and change, with who did it. Entries can never be edited or deleted; the database refuses."
      />
      <Suspense fallback={<Skeleton className="h-96 w-full" />}>
        <Log searchParams={searchParams as Promise<{ status?: string; page?: string }>} />
      </Suspense>
    </>
  );
}

async function Log({ searchParams }: { searchParams: Promise<{ status?: string; page?: string }> }) {
  const { status: type, page } = await searchParams;
  const pageNo = Math.max(0, Number(page ?? 0) || 0);
  let log;
  try {
    log = await getAudit({ entityType: type, page: pageNo, size: 50 });
  } catch (e) {
    if (e instanceof ApiError && e.status === 403) {
      return <p className="text-sm text-muted-foreground">Only owners and admins can read the full audit log.</p>;
    }
    throw e;
  }
  const pages = Math.ceil(log.total / log.size);

  return (
    <>
      <StatusTabs base="/settings/audit" current={type} tabs={TYPES} />
      <div className="overflow-hidden rounded-xl border bg-card shadow-xs">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/50 hover:bg-muted/50">
              <TableHead className="ps-5">When</TableHead>
              <TableHead>What happened</TableHead>
              <TableHead className="pe-5">Who</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {log.items.map((e) => (
              <TableRow key={e.id} data-testid="audit-row">
                <TableCell className="figures whitespace-nowrap py-2.5 ps-5 text-muted-foreground">
                  {formatDate(e.at)} {new Date(e.at).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}
                </TableCell>
                <TableCell dir="auto">
                  {LINKS[e.entityType] && e.action !== "deleted" ? (
                    <Link href={`${LINKS[e.entityType]}/${e.entityId}`} className="hover:underline">
                      {e.summary}
                    </Link>
                  ) : (
                    e.summary
                  )}
                </TableCell>
                <TableCell className="pe-5 text-muted-foreground">{e.actorName ?? "Fatoora (automatic)"}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      {pages > 1 && (
        <nav className="figures mt-3 flex items-center gap-3 text-sm" aria-label="Pages">
          {pageNo > 0 && (
            <Link href={`/settings/audit?${new URLSearchParams({ ...(type ? { status: type } : {}), page: String(pageNo - 1) })}`} className="text-primary hover:underline">
              ← Newer
            </Link>
          )}
          <span className="text-muted-foreground">
            Page {pageNo + 1} of {pages}
          </span>
          {pageNo + 1 < pages && (
            <Link href={`/settings/audit?${new URLSearchParams({ ...(type ? { status: type } : {}), page: String(pageNo + 1) })}`} className="text-primary hover:underline">
              Older →
            </Link>
          )}
        </nav>
      )}
    </>
  );
}
