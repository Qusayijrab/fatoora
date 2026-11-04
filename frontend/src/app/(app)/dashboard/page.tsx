import {
  Activity,
  AlarmClock,
  Building2,
  CircleDollarSign,
  FileText,
  Hourglass,
  ReceiptText,
  TrendingUp,
  UsersRound,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { Suspense, type ReactNode } from "react";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { getMe, getOverview, getPaymentProblems, getSummary } from "@/lib/api";
import { currencyDecimals } from "@/lib/currencies";
import { formatAmount, formatMoney } from "@/lib/format";
import { canManageMembers, type Money, type Overview } from "@/lib/types";
import { cn } from "@/lib/utils";
import { MoneyChart } from "./money-chart";
import { OnboardingRoute, type RouteStep } from "./onboarding-route";
import { PaymentProblems } from "./payment-problems";
import { SummaryText } from "@/components/summary-text";

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

  const [summary, overview, problems] = await Promise.all([getSummary(), getOverview(), getPaymentProblems()]);
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

      {problems.length > 0 && <PaymentProblems problems={problems} isAdmin={canManageMembers(org.role)} />}

      {routeFirst && <OnboardingRoute steps={steps} />}

      <Ledger overview={overview} />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <section className="rounded-xl border bg-card shadow-xs p-5 sm:p-6" aria-labelledby="chart-title">
          <SectionTitle id="chart-title" icon={TrendingUp} className="mb-4">
            Invoiced and collected, last 12 months
          </SectionTitle>
          <MoneyChart months={overview.months} currency={overview.baseCurrency} decimals={currencyDecimals(overview.baseCurrency)} />
        </section>
        <Aging overview={overview} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="rounded-xl border bg-card shadow-xs p-5 sm:p-6" aria-labelledby="owed-title">
          <SectionTitle id="owed-title" icon={UsersRound}>
            Who owes the most
          </SectionTitle>
          {overview.topClients.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground">Nobody owes you anything right now.</p>
          ) : (
            <ul className="mt-3 divide-y">
              {overview.topClients.map((c) => (
                <li key={c.clientId} className="flex items-center justify-between gap-4 py-2.5 text-sm">
                  <span className="flex min-w-0 items-center gap-3">
                    <span aria-hidden className="grid size-8 shrink-0 place-items-center rounded-full bg-primary/10 text-xs font-semibold text-primary">
                      {c.name.trim().charAt(0).toUpperCase()}
                    </span>
                    <Link href={`/invoices?q=${encodeURIComponent(c.name)}`} className="min-w-0 truncate font-medium hover:underline" dir="auto">
                      {c.name}
                    </Link>
                  </span>
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
        <section className="rounded-xl border bg-card shadow-xs p-5 sm:p-6" aria-labelledby="activity-title">
          <div className="flex items-baseline justify-between">
            <SectionTitle id="activity-title" icon={Activity}>
              Recent activity
            </SectionTitle>
            <Link href="/settings/audit" className="text-xs text-muted-foreground hover:text-foreground">
              Full audit log
            </Link>
          </div>
          <ul className="mt-3 divide-y">
            {overview.activity.map((a, i) => (
              <li key={i} className="flex items-baseline justify-between gap-4 py-2.5 text-sm">
                <span className="flex min-w-0 items-baseline gap-2.5">
                  <span aria-hidden className="size-1.5 shrink-0 -translate-y-px rounded-full bg-primary/60" />
                  <span className="min-w-0" dir="auto">
                    <SummaryText text={a.summary} />
                  </span>
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

/** A card heading with a small icon tile, so each panel is recognisable at a glance. */
function SectionTitle({ id, icon: Icon, className, children }: { id: string; icon: LucideIcon; className?: string; children: ReactNode }) {
  return (
    <h2 id={id} className={cn("flex items-center gap-2.5 text-base font-semibold", className)}>
      <span aria-hidden className="grid size-7 place-items-center rounded-md bg-primary/10 text-primary">
        <Icon className="size-4" strokeWidth={2} />
      </span>
      {children}
    </h2>
  );
}

/** The four headline figures as cards, each with an icon tile in its own tone. */
function Ledger({ overview }: { overview: Overview }) {
  const cells: { label: string; value: Money; note: string; icon: LucideIcon; tone: string; alert?: boolean; href: string }[] = [
    { label: "Outstanding", value: overview.outstanding, note: `${overview.openInvoices} open ${overview.openInvoices === 1 ? "invoice" : "invoices"}`, icon: Wallet, tone: "bg-primary/10 text-primary", href: "/invoices?status=ISSUED" },
    { label: "Overdue", value: overview.overdue, note: `${overview.overdueInvoices} past due`, icon: AlarmClock, tone: "bg-ink-overdue/10 text-ink-overdue", alert: overview.overdueInvoices > 0, href: "/invoices?status=OVERDUE" },
    { label: "Collected this month", value: overview.collectedThisMonth, note: "payments received", icon: CircleDollarSign, tone: "bg-ink-paid/12 text-ink-paid", href: "/invoices?status=PAID" },
    { label: "Invoiced this month", value: overview.invoicedThisMonth, note: `${plural(overview.drafts, "draft")} · ${plural(overview.quotesAwaitingAnswer, "quote")} awaiting`, icon: ReceiptText, tone: "bg-ink-partial/12 text-ink-partial", href: "/invoices" },
  ];
  return (
    <section aria-label="Totals" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {cells.map((c, i) => (
        <Link
          key={c.label}
          href={c.href}
          className="grid gap-2 rounded-xl border bg-card p-4 shadow-xs transition sm:gap-3 sm:p-5-[box-shadow,border-color] duration-150 hover:border-primary/30 hover:shadow-md"
        >
          <div className="flex items-center justify-between gap-3">
            <span className="text-sm font-medium text-muted-foreground">{c.label}</span>
            <span aria-hidden className={cn("grid size-9 place-items-center rounded-lg", c.tone)}>
              <c.icon className="size-[18px]" strokeWidth={2} />
            </span>
          </div>
          <span className={cn("figures text-2xl font-semibold tracking-tight", c.alert && "text-ink-overdue")} data-testid={`ledger-${i}`}>
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
    <section className="rounded-xl border bg-card shadow-xs p-5 sm:p-6" aria-labelledby="aging-title">
      <SectionTitle id="aging-title" icon={Hourglass}>
        Unpaid, by how late
      </SectionTitle>
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

/** "1 draft", "2 drafts". */
function plural(n: number, word: string) {
  return `${n} ${word}${n === 1 ? "" : "s"}`;
}
