import { Building2, FileText, ReceiptText } from "lucide-react";
import Link from "next/link";
import { Suspense } from "react";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { getMe, getOverview, getSummary } from "@/lib/api";
import { currencyDecimals } from "@/lib/currencies";
import { formatAmount, formatMoney } from "@/lib/format";
import type { Money, Overview } from "@/lib/types";
import { cn } from "@/lib/utils";
import { MoneyChart } from "./money-chart";
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

  const [summary, overview] = await Promise.all([getSummary(), getOverview()]);
  const count = (key: string) => summary[key] ?? 0;
  const steps: RouteStep[] = [
    { key: "company", title: "Set up your company", detail: `${org.name}, ${org.defaultCurrency}`, href: "/settings/organization", cta: "Review", done: true },
    { key: "team", title: "Invite your team", detail: "Add a bookkeeper or partner.", href: "/settings/members", cta: "Add member", done: count("members") > 1 },
    { key: "client", title: "Add a client", detail: "Who you bill, with tax id and address.", href: "/clients/new", cta: "Add client", done: count("clients") > 0 },
    { key: "product", title: "Add a product", detail: "What you sell, priced once.", href: "/products/new", cta: "Add product", done: count("products") > 0 },
    { key: "quote", title: "Send a quote", detail: "Agree the price before the work.", href: "/quotes/new", cta: "New quote", done: count("quotesSent") > 0 },
    { key: "invoice", title: "Issue an invoice", detail: "Numbered, PDF, emailed.", href: "/invoices/new", cta: "New invoice", done: count("invoicesIssued") > 0 },
    { key: "paid", title: "Get paid", detail: "Online by payment link.", href: "/invoices", cta: "See invoices", done: count("payments") > 0 },
  ];
  const onboarding = steps.some((s) => !s.done);
  // New companies see the route first; once they are invoicing, money comes first.
  const routeFirst = onboarding && count("invoicesIssued") === 0;

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
            {org.role.toLowerCase()} · reporting in {overview.baseCurrency} · {org.timeZone.replace(/_/g, " ")}
          </>
        }
        actions={
          <>
            <Button asChild variant="outline">
              <Link href="/quotes/new">
                <FileText /> New quote
              </Link>
            </Button>
            <Button asChild>
              <Link href="/invoices/new">
                <ReceiptText /> New invoice
              </Link>
            </Button>
          </>
        }
      />

      {routeFirst && <OnboardingRoute steps={steps} />}

      <Ledger overview={overview} />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <section className="rounded-lg border bg-card p-5 sm:p-6" aria-labelledby="chart-title">
          <h2 id="chart-title" className="mb-4 text-base font-semibold">
            Invoiced and collected, last 12 months
          </h2>
          <MoneyChart months={overview.months} currency={overview.baseCurrency} decimals={currencyDecimals(overview.baseCurrency)} />
        </section>
        <Aging overview={overview} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="rounded-lg border bg-card p-5 sm:p-6" aria-labelledby="owed-title">
          <h2 id="owed-title" className="text-base font-semibold">
            Who owes the most
          </h2>
          {overview.topClients.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground">Nobody owes you anything right now.</p>
          ) : (
            <ul className="mt-3 divide-y">
              {overview.topClients.map((c) => (
                <li key={c.clientId} className="flex items-baseline justify-between gap-4 py-2.5 text-sm">
                  <Link href={`/invoices?q=${encodeURIComponent(c.name)}`} className="min-w-0 truncate font-medium hover:underline" dir="auto">
                    {c.name}
                  </Link>
                  <span className="figures shrink-0">
                    {formatAmount(c.outstanding.amount)}
                    <span className="ms-2 text-xs text-muted-foreground">
                      {c.invoices} {c.invoices === 1 ? "invoice" : "invoices"}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
        <section className="rounded-lg border bg-card p-5 sm:p-6" aria-labelledby="activity-title">
          <div className="flex items-baseline justify-between">
            <h2 id="activity-title" className="text-base font-semibold">
              Recent activity
            </h2>
            <Link href="/settings/audit" className="text-xs text-muted-foreground hover:text-foreground">
              Full audit log
            </Link>
          </div>
          <ul className="mt-3 divide-y">
            {overview.activity.map((a, i) => (
              <li key={i} className="flex items-baseline justify-between gap-4 py-2.5 text-sm">
                <span className="min-w-0" dir="auto">
                  {a.summary}
                </span>
                <time className="figures shrink-0 text-xs text-muted-foreground" dateTime={a.at}>
                  {timeAgo(a.at)}
                </time>
              </li>
            ))}
          </ul>
        </section>
      </div>

      {onboarding && !routeFirst && <OnboardingRoute steps={steps} />}
    </div>
  );
}

/** The four headline figures as one ruled strip (not four floating cards). */
function Ledger({ overview }: { overview: Overview }) {
  const cells: { label: string; value: Money; note: string; tone?: "alert"; href: string }[] = [
    { label: "Outstanding", value: overview.outstanding, note: `${overview.openInvoices} open ${overview.openInvoices === 1 ? "invoice" : "invoices"}`, href: "/invoices?status=ISSUED" },
    { label: "Overdue", value: overview.overdue, note: `${overview.overdueInvoices} past due`, tone: overview.overdueInvoices > 0 ? "alert" : undefined, href: "/invoices?status=OVERDUE" },
    { label: "Collected this month", value: overview.collectedThisMonth, note: "payments received", href: "/invoices?status=PAID" },
    { label: "Invoiced this month", value: overview.invoicedThisMonth, note: `${overview.drafts} drafts · ${overview.quotesAwaitingAnswer} quotes awaiting`, href: "/invoices" },
  ];
  return (
    <section aria-label="Totals" className="grid overflow-hidden rounded-lg border bg-card sm:grid-cols-2 lg:grid-cols-4">
      {cells.map((c, i) => (
        <Link
          key={c.label}
          href={c.href}
          className={cn(
            "group grid gap-1 px-5 py-4 transition-colors duration-150 hover:bg-muted/50",
            i > 0 && "border-t sm:border-t-0",
            i % 2 === 1 && "sm:border-s",
            i >= 2 && "sm:border-t lg:border-t-0",
            i === 2 && "lg:border-s",
          )}
        >
          <span className="text-xs font-medium text-muted-foreground">{c.label}</span>
          <span className={cn("figures text-xl font-semibold tracking-tight", c.tone === "alert" && "text-ink-overdue")} data-testid={`ledger-${i}`}>
            {formatMoney(c.value)}
          </span>
          <span className="text-xs text-muted-foreground">{c.note}</span>
        </Link>
      ))}
    </section>
  );
}

/** What is owed, by how late it is. One series, so one color; the label carries the value. */
function Aging({ overview }: { overview: Overview }) {
  const max = Math.max(1, ...overview.aging.map((b) => b.amount.amountMinor));
  return (
    <section className="rounded-lg border bg-card p-5 sm:p-6" aria-labelledby="aging-title">
      <h2 id="aging-title" className="text-base font-semibold">
        Unpaid, by how late
      </h2>
      <ul className="mt-4 grid gap-3">
        {overview.aging.map((b, i) => (
          <li key={b.label} className="grid gap-1">
            <div className="flex items-baseline justify-between gap-3 text-sm">
              <span className={cn(i >= 3 && b.count > 0 && "font-medium text-ink-overdue")}>{b.label}</span>
              <span className="figures">
                {formatAmount(b.amount.amount)}
                <span className="ms-2 text-xs text-muted-foreground">{b.count}</span>
              </span>
            </div>
            <div className="h-2 rounded-full bg-muted">
              <div
                className={cn("h-2 rounded-full", i === 0 ? "bg-series-1" : "bg-ink-overdue/80")}
                style={{ width: `${(b.amount.amountMinor / max) * 100}%`, minWidth: b.amount.amountMinor > 0 ? "4px" : 0 }}
              />
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

function timeAgo(iso: string) {
  const seconds = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return "just now";
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} h ago`;
  return `${Math.floor(seconds / 86400)} d ago`;
}

function DashboardSkeleton() {
  return (
    <div className="grid gap-8">
      <div className="grid gap-2 pb-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-72 w-full" />
    </div>
  );
}
