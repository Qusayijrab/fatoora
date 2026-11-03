import { ArrowRight, BellRing, Coins, Link2, Repeat, UsersRound } from "lucide-react";
import Link from "next/link";
import { LogoMark, Wordmark } from "@/components/brand";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { CURRENCIES } from "@/lib/currencies";

import { InvoiceSpecimen } from "./invoice-specimen";

const SHOWN = ["JOD", "KWD", "BHD", "OMR", "SAR", "AED", "USD", "JPY"];

const SAMPLE = 1250.5;

const LIFECYCLE = [
  { status: "DRAFT", text: "Edit freely. Drafts have no number, so nothing is wasted." },
  { status: "ISSUED", text: "Numbered in sequence for your company, with the client's details frozen onto it." },
  { status: "PARTIALLY_PAID", text: "Every payment is recorded against the invoice, online or by hand." },
  { status: "PAID", text: "Settled to the last fils." },
  { status: "OVERDUE", text: "Past due in your own time zone, and the reminder already went out." },
  { status: "VOID", text: "Mistakes are cancelled, never erased. The history stays readable." },
];

const CHORES = [
  { icon: Link2, title: "Payment links on every invoice", text: "Your client pays online from the email or the portal; the invoice updates itself." },
  { icon: BellRing, title: "Reminders that chase for you", text: "Polite emails before and after the due date, sent once, never twice." },
  { icon: Repeat, title: "Recurring invoices", text: "Monthly retainers and rent issue themselves on schedule, in your time zone." },
  { icon: UsersRound, title: "A portal for each client", text: "A private link where clients see their quotes and invoices, accept, and pay." },
  { icon: Coins, title: "Multi-currency with frozen rates", text: "Bill in USD, report in JOD. The rate is captured at issue and never recalculated." },
];

export default function LandingPage() {
  return (
    <div className="flex flex-1 flex-col">
      {/* Hero: the brand field owns the first viewport. */}
      <section className="bg-brand text-brand-foreground">
        <nav className="mx-auto flex max-w-6xl items-center justify-between px-5 py-5 sm:px-8">
          <Wordmark inverted />
          <div className="flex items-center gap-1 sm:gap-2">
            <Button asChild variant="ghost" className="text-brand-foreground hover:bg-brand-foreground/10 hover:text-brand-foreground">
              <Link href="/login">Log in</Link>
            </Button>
            <Button asChild className="bg-brand-foreground text-brand hover:bg-brand-foreground/90">
              <Link href="/register">Create account</Link>
            </Button>
          </div>
        </nav>

        <div className="mx-auto grid max-w-6xl items-center gap-12 px-5 pb-20 pt-10 sm:px-8 lg:grid-cols-[1fr_minmax(0,30rem)] lg:gap-16 lg:pb-28 lg:pt-16">
          <div>
            <h1 className="text-[clamp(2.75rem,7vw,5rem)] font-semibold leading-[0.98] tracking-[-0.035em]">
              From done
              <br />
              to paid.
            </h1>
            <p lang="ar" dir="rtl" className="mt-5 text-end text-[clamp(1.5rem,3vw,2.1rem)] font-medium text-pop">
              من الإنجاز إلى التحصيل
            </p>
            <p className="mt-7 max-w-[34rem] text-base leading-relaxed text-brand-foreground/80 sm:text-lg">
              Quotes, invoices, payment links and reminders for businesses in Jordan and the Gulf. Dinars to three
              decimals, Arabic on every PDF, and every step kept on the record.
            </p>
            <div className="mt-9 flex flex-wrap items-center gap-3">
              <Button asChild size="lg" className="h-11 bg-brand-foreground px-5 text-[0.95rem] text-brand hover:bg-brand-foreground/90">
                <Link href="/register">
                  Create your account <ArrowRight className="rtl:rotate-180" />
                </Link>
              </Button>
              <Link href="/login" className="px-2 text-sm font-medium text-brand-foreground/80 underline-offset-4 hover:text-brand-foreground hover:underline">
                I already have one
              </Link>
            </div>
          </div>
          <InvoiceSpecimen />
        </div>
      </section>

      {/* Three decimals */}
      <section className="mx-auto grid w-full max-w-6xl gap-10 px-5 py-20 sm:px-8 lg:grid-cols-[1fr_1.1fr] lg:gap-20 lg:py-28">
        <div>
          <h2 className="text-3xl font-semibold tracking-[-0.025em] sm:text-4xl">Three decimals, not two.</h2>
          <p className="mt-4 max-w-[46ch] leading-relaxed text-muted-foreground">
            A dinar has 1,000 fils. Most invoicing tools round it like a dollar and lose the last digit. Fatoora stores
            every amount as an exact whole number of the smallest unit, with each currency&apos;s own decimals, and rounds
            tax per line, half up. No floating point, anywhere.
          </p>
        </div>
        <div className="overflow-hidden rounded-xl border bg-card shadow-sm">
          <table className="figures w-full text-sm">
            <caption className="sr-only">The same amount written in each currency&apos;s own precision</caption>
            <thead>
              <tr className="border-b bg-muted/60 text-xs text-muted-foreground">
                <th className="px-5 py-3 text-start font-medium">Currency</th>
                <th className="px-5 py-3 text-end font-medium">Decimals</th>
                <th className="px-5 py-3 text-end font-medium">1,250.5 written correctly</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {CURRENCIES.filter((c) => SHOWN.includes(c.code)).map((c) => (
                <tr key={c.code} className={c.decimals === 3 ? "bg-primary/5" : undefined}>
                  <td className="px-5 py-2.5">
                    <span className="font-medium">{c.code}</span>
                    <span className="ms-2 hidden whitespace-nowrap text-muted-foreground sm:inline">{c.name}</span>
                  </td>
                  <td className="px-5 py-2.5 text-end text-muted-foreground">{c.decimals}</td>
                  <td className="px-5 py-2.5 text-end font-medium">
                    {new Intl.NumberFormat("en-US", {
                      minimumFractionDigits: c.decimals,
                      maximumFractionDigits: c.decimals,
                    }).format(c.decimals === 0 ? Math.round(SAMPLE) : SAMPLE)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Lifecycle */}
      <section className="border-y bg-card">
        <div className="mx-auto max-w-6xl px-5 py-20 sm:px-8 lg:py-28">
          <h2 className="max-w-[20ch] text-3xl font-semibold tracking-[-0.025em] sm:text-4xl">Tracked, never erased.</h2>
          <p className="mt-4 max-w-[60ch] leading-relaxed text-muted-foreground">
            Once an invoice is issued it cannot be edited, only paid, voided or credited. Every step lands on the record
            and in the audit log, so you, your accountant and the tax office read the same story.
          </p>
          <dl className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {LIFECYCLE.map((item) => (
              <div key={item.status} className="grid content-start gap-3 rounded-xl border bg-background p-5">
                <dt>
                  <StatusBadge status={item.status} />
                </dt>
                <dd className="text-sm leading-relaxed text-muted-foreground">{item.text}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      {/* Chores */}
      <section className="mx-auto w-full max-w-6xl px-5 py-20 sm:px-8 lg:py-28">
        <h2 className="text-3xl font-semibold tracking-[-0.025em] sm:text-4xl">The chores run themselves.</h2>
        <ul className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {CHORES.map((c) => (
            <li key={c.title} className="rounded-xl border bg-card p-6 shadow-sm">
              <span className="grid size-10 place-items-center rounded-lg bg-primary/10 text-primary">
                <c.icon className="size-5" strokeWidth={1.75} />
              </span>
              <p className="mt-4 font-medium">{c.title}</p>
              <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{c.text}</p>
            </li>
          ))}
        </ul>
      </section>

      {/* Close */}
      <section className="relative overflow-hidden bg-brand text-brand-foreground">
        <div className="mx-auto flex max-w-6xl flex-col items-start gap-6 px-5 py-16 sm:px-8 lg:flex-row lg:items-center lg:justify-between lg:py-20">
          <div>
            <p className="text-3xl font-semibold tracking-[-0.025em] sm:text-4xl">Send your first invoice today.</p>
            <p lang="ar" dir="rtl" className="mt-2 text-end text-xl text-pop">
              أرسل فاتورتك الأولى اليوم
            </p>
          </div>
          <Button asChild size="lg" className="h-11 bg-brand-foreground px-5 text-[0.95rem] text-brand hover:bg-brand-foreground/90">
            <Link href="/register">
              Create your account <ArrowRight className="rtl:rotate-180" />
            </Link>
          </Button>
        </div>
        <LogoMark inverted className="pointer-events-none absolute -bottom-28 -end-20 size-80 opacity-[0.07]" />
      </section>

      <footer className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-3 px-5 py-8 text-xs text-muted-foreground sm:px-8">
        <span>Fatoora · a portfolio project</span>
        <span>Example data on this page is illustrative.</span>
      </footer>
    </div>
  );
}
