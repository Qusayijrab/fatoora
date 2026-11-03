import { Building2 } from "lucide-react";
import Link from "next/link";
import { Suspense } from "react";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { getMe, getMembers } from "@/lib/api";
import { OnboardingRoute, type RouteStep } from "./onboarding-route";

export const metadata = { title: "Dashboard" };

export default function DashboardPage() {
  return (
    <Suspense fallback={<DashboardSkeleton />}>
      <Dashboard />
    </Suspense>
  );
}

async function Dashboard() {
  const me = await getMe();
  const org = me.organization;
  const firstName = me.fullName.split(" ")[0];

  if (!org) {
    return (
      <>
        <PageHeader title={`Welcome, ${firstName}`} />
        <EmptyState
          icon={Building2}
          title="You are not in an organization yet"
          action={
            <Button asChild>
              <Link href="/organizations/new">Create organization</Link>
            </Button>
          }
        >
          Everything in Fatoora (clients, quotes, invoices) belongs to an organization. Create yours, or ask a
          colleague to add you to theirs.
        </EmptyState>
      </>
    );
  }

  const members = await getMembers();
  const steps: RouteStep[] = [
    { key: "company", title: "Set up your company", detail: `${org.name}, ${org.defaultCurrency}`, href: "/settings/organization", cta: "Review", done: true },
    { key: "team", title: "Invite your team", detail: "Add a bookkeeper or partner.", href: "/settings/members", cta: "Add member", done: members.length > 1 },
    { key: "client", title: "Add a client", detail: "Who you bill, with tax id and address.", href: "/clients", cta: "Add client", done: false },
    { key: "product", title: "Add a product", detail: "What you sell, priced once.", href: "/products", cta: "Add product", done: false },
    { key: "quote", title: "Send a quote", detail: "Agree the price before the work.", href: "/quotes", cta: "New quote", done: false },
    { key: "invoice", title: "Issue an invoice", detail: "Numbered, PDF, emailed.", href: "/invoices", cta: "New invoice", done: false },
    { key: "paid", title: "Get paid", detail: "Online by payment link.", href: "/invoices", cta: "See invoices", done: false },
  ];

  return (
    <div className="grid gap-8">
      <PageHeader
        title={`Welcome, ${firstName}`}
        description={
          <>
            <span data-testid="org-name" className="font-medium text-foreground">
              {org.name}
            </span>
            {" · "}
            {org.role.toLowerCase()} · invoices in {org.defaultCurrency} · {org.timeZone.replace(/_/g, " ")}
          </>
        }
      />
      <OnboardingRoute steps={steps} />
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="grid gap-8">
      <div className="grid gap-2 pb-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Skeleton className="h-48 w-full" />
    </div>
  );
}
