import { Contact, FileUp, Plus } from "lucide-react";
import Link from "next/link";
import { Suspense } from "react";
import { EmptyState } from "@/components/empty-state";
import { ListSearch } from "@/components/list-search";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getClients, getMe } from "@/lib/api";
import { countryName } from "@/lib/countries";

export const metadata = { title: "Clients" };

type Search = { q?: string; archived?: string };

export default function ClientsPage({ searchParams }: PageProps<"/clients">) {
  return (
    <>
      <PageHeader
        title="Clients"
        description="The businesses and people you bill. Their details are copied onto each invoice when it is issued."
        actions={
          <>
            <Button asChild variant="outline">
              <Link href="/clients/import">
                <FileUp /> Import CSV
              </Link>
            </Button>
            <Button asChild>
              <Link href="/clients/new">
                <Plus /> New client
              </Link>
            </Button>
          </>
        }
      />
      <Suspense fallback={<Skeleton className="h-72 w-full" />}>
        <ClientList searchParams={searchParams as Promise<Search>} />
      </Suspense>
    </>
  );
}

async function ClientList({ searchParams }: { searchParams: Promise<Search> }) {
  const { q, archived } = await searchParams;
  const includeArchived = archived === "1";
  const [page, me] = await Promise.all([getClients({ q, includeArchived, size: 100 }), getMe()]);
  const orgCurrency = me.organization?.defaultCurrency;

  if (page.total === 0 && !q && !includeArchived) {
    return (
      <EmptyState
        icon={Contact}
        title="No clients yet"
        action={
          <Button asChild>
            <Link href="/clients/new">Add your first client</Link>
          </Button>
        }
      >
        Add the businesses you work with once: name, tax number, address and billing email. Every quote and invoice
        starts from here.
      </EmptyState>
    );
  }

  return (
    <>
      <ListSearch action="/clients" q={q} includeArchived={includeArchived} placeholder="Search name, email or tax number" />
      <div className="overflow-hidden rounded-xl border bg-card shadow-xs">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/50 hover:bg-muted/50">
              <TableHead className="ps-5">Client</TableHead>
              <TableHead className="hidden md:table-cell">Tax number</TableHead>
              <TableHead className="hidden sm:table-cell">Location</TableHead>
              <TableHead className="pe-5 text-end">Bills in</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {page.items.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} className="py-10 text-center text-muted-foreground">
                  No clients match “{q}”.
                </TableCell>
              </TableRow>
            )}
            {page.items.map((c) => (
              <TableRow key={c.id} data-testid="client-row" className={c.archived ? "opacity-60" : undefined}>
                <TableCell className="py-3 ps-5">
                  <Link href={`/clients/${c.id}`} className="font-medium hover:underline">
                    {c.name}
                  </Link>
                  {c.archived && <StatusBadge status="VOID" label="Archived" className="ms-2" />}
                  {c.email && <p className="text-xs text-muted-foreground">{c.email}</p>}
                </TableCell>
                <TableCell className="figures hidden text-muted-foreground md:table-cell">{c.taxId ?? "—"}</TableCell>
                <TableCell className="hidden text-muted-foreground sm:table-cell">
                  {[c.city, countryName(c.country)].filter(Boolean).join(", ") || "—"}
                </TableCell>
                <TableCell className="figures pe-5 text-end text-muted-foreground">{c.currency ?? (orgCurrency ? `${orgCurrency} (default)` : "Default")}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <p className="figures mt-3 text-xs text-muted-foreground">
        {page.total} {page.total === 1 ? "client" : "clients"}
      </p>
    </>
  );
}
