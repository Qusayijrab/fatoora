"use client";

import { Download, Mail } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { toast } from "sonner";
import { emailDocument } from "@/app/(app)/email-actions";
import { ConfirmAction } from "@/components/confirm-action";
import { DataTable, type ColumnDef } from "@/components/data-table";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { formatAmount, formatDate } from "@/lib/format";
import type { InvoiceStatus, InvoiceSummary } from "@/lib/types";

/** Lifecycle order, so sorting by status reads draft → paid → void. */
const STATUS_ORDER: InvoiceStatus[] = ["DRAFT", "ISSUED", "PARTIALLY_PAID", "OVERDUE", "PAID", "VOID"];

/** Compares two decimal strings exactly ("1250.500" vs "99.99"), never through floats. */
function compareDecimal(a: string, b: string): number {
  const scale = Math.max(a.split(".")[1]?.length ?? 0, b.split(".")[1]?.length ?? 0);
  const minor = (v: string) => {
    const [int, frac = ""] = v.replace("-", "").split(".");
    const n = BigInt(int + frac.padEnd(scale, "0"));
    return v.startsWith("-") ? -n : n;
  };
  const x = minor(a);
  const y = minor(b);
  return x < y ? -1 : x > y ? 1 : 0;
}

/** Same currency sorts by amount; different currencies group by code rather than pretend to compare. */
const byMoney = (a: { currency: string; amount: string }, b: { currency: string; amount: string }) =>
  a.currency === b.currency ? compareDecimal(a.amount, b.amount) : a.currency.localeCompare(b.currency);

const hasBalance = (inv: InvoiceSummary) => inv.status !== "VOID" && inv.status !== "DRAFT";
/** Still owed money: the phone card shows what is left to pay rather than the total. */
const isOpen = (inv: InvoiceSummary) => inv.status === "ISSUED" || inv.status === "PARTIALLY_PAID" || inv.status === "OVERDUE";
const canEmail = (inv: InvoiceSummary) => inv.number !== null && inv.status !== "VOID";

const COLUMNS: ColumnDef<InvoiceSummary, unknown>[] = [
  {
    id: "number",
    header: "Number",
    accessorFn: (inv) => inv.number ?? "",
    cell: ({ row }) => (
      <Link href={`/invoices/${row.original.id}`} className="figures font-mono font-medium hover:underline" data-testid="invoice-row">
        {row.original.number ?? <span className="font-sans text-muted-foreground">Draft</span>}
      </Link>
    ),
  },
  {
    id: "client",
    header: "Client",
    accessorFn: (inv) => inv.client.name,
    cell: ({ getValue }) => (
      <span className="inline-block max-w-56 truncate align-bottom" dir="auto">
        {getValue<string>()}
      </span>
    ),
  },
  {
    id: "issued",
    header: "Issued",
    accessorFn: (inv) => inv.issueDate,
    cell: ({ getValue }) => <span className="figures text-muted-foreground">{formatDate(getValue<string>())}</span>,
    meta: { className: "hidden md:table-cell" },
  },
  {
    id: "due",
    header: "Due",
    accessorFn: (inv) => inv.dueDate ?? "",
    cell: ({ row }) => <span className="figures text-muted-foreground">{formatDate(row.original.dueDate)}</span>,
    meta: { className: "hidden md:table-cell" },
  },
  {
    id: "total",
    header: "Total",
    accessorFn: (inv) => inv.total,
    sortingFn: (a, b) => byMoney(a.original.total, b.original.total),
    cell: ({ row }) => (
      <span className="figures whitespace-nowrap">
        <span className="text-muted-foreground">{row.original.currency}</span> {formatAmount(row.original.total.amount)}
      </span>
    ),
    meta: { align: "end" },
  },
  {
    id: "balance",
    header: "Balance",
    accessorFn: (inv) => inv.balanceDue,
    sortingFn: (a, b) => byMoney(a.original.balanceDue, b.original.balanceDue),
    cell: ({ row }) => (
      <span className="figures whitespace-nowrap">{hasBalance(row.original) ? formatAmount(row.original.balanceDue.amount) : "—"}</span>
    ),
    meta: { align: "end" },
  },
  {
    id: "status",
    header: "Status",
    accessorFn: (inv) => STATUS_ORDER.indexOf(inv.status),
    cell: ({ row }) => <StatusBadge status={row.original.status} />,
    meta: { align: "end" },
  },
];

/** The selected rows as a CSV file (with a BOM so Excel reads Arabic names correctly). */
function downloadCsv(rows: InvoiceSummary[]) {
  const quote = (v: string) => (/[",\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v);
  const lines = [
    ["Number", "Client", "Currency", "Issued", "Due", "Total", "Balance", "Status"],
    ...rows.map((inv) => [
      inv.number ?? "Draft",
      inv.client.name,
      inv.currency,
      inv.issueDate,
      inv.dueDate ?? "",
      inv.total.amount,
      hasBalance(inv) ? inv.balanceDue.amount : "",
      inv.status,
    ]),
  ];
  const csv = "﻿" + lines.map((l) => l.map(quote).join(",")).join("\r\n");
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = `invoices-${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

/** Emails each issued invoice to its client, one at a time, and reports what went through. */
async function emailAll(rows: InvoiceSummary[], clear: () => void) {
  let sent = 0;
  const failed: string[] = [];
  for (const inv of rows) {
    const result = await emailDocument("invoices", inv.id, "", "");
    if (result.error) failed.push(`${inv.number}: ${result.error}`);
    else sent++;
  }
  if (sent) toast.success(`Emailed ${sent} ${sent === 1 ? "invoice" : "invoices"}`);
  if (failed.length) toast.error(`${failed.length} not sent`, { description: failed.join("\n") });
  clear();
}

function MobileCard({ inv }: { inv: InvoiceSummary }) {
  return (
    <Link
      href={`/invoices/${inv.id}`}
      className="grid gap-2 rounded-xl border bg-card p-4 shadow-xs transition-colors active:bg-muted/50"
    >
      <span className="flex items-center justify-between gap-3">
        <span className="figures font-mono text-sm font-medium">
          {inv.number ?? <span className="font-sans text-muted-foreground">Draft</span>}
        </span>
        <StatusBadge status={inv.status} />
      </span>
      <span className="truncate text-sm" dir="auto">
        {inv.client.name}
      </span>
      <span className="flex items-baseline justify-between gap-3 text-xs text-muted-foreground">
        <span className="figures">Due {formatDate(inv.dueDate)}</span>
        <span className="figures text-base font-semibold text-foreground">
          <span className="text-xs font-normal text-muted-foreground">{inv.currency}</span>{" "}
          {formatAmount(isOpen(inv) ? inv.balanceDue.amount : inv.total.amount)}
        </span>
      </span>
    </Link>
  );
}

export function InvoiceTable({ invoices, toolbar, empty }: { invoices: InvoiceSummary[]; toolbar: ReactNode; empty: ReactNode }) {
  return (
    <DataTable
      label="Invoices"
      data={invoices}
      columns={COLUMNS}
      getRowId={(inv) => inv.id}
      toolbar={toolbar}
      empty={empty}
      selectable
      columnToggle
      mobileCard={(inv) => <MobileCard inv={inv} />}
      bulkActions={(rows, clear) => {
        const emailable = rows.filter(canEmail);
        return (
          <>
            <Button variant="outline" onClick={() => downloadCsv(rows)}>
              <Download /> Export CSV
            </Button>
            <ConfirmAction
              destructive={false}
              trigger={
                <Button disabled={emailable.length === 0}>
                  <Mail /> Email {emailable.length || ""}
                </Button>
              }
              title={`Email ${emailable.length} ${emailable.length === 1 ? "invoice" : "invoices"}?`}
              description={
                <>
                  Each client gets their invoice as a PDF, with its payment link, at the email on file.
                  {emailable.length < rows.length && ` ${rows.length - emailable.length} draft or void ${rows.length - emailable.length === 1 ? "invoice is" : "invoices are"} skipped.`}
                </>
              }
              confirmLabel="Send emails"
              action={() => emailAll(emailable, clear)}
            />
          </>
        );
      }}
    />
  );
}
