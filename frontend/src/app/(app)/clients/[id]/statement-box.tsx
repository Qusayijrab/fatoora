"use client";

import { FileText } from "lucide-react";
import { useId, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/** Statement of account (كشف حساب): every invoice, payment and credit note for a period, as a PDF. */
export function StatementBox({ clientId, today }: { clientId: string; today: string }) {
  const uid = useId();
  const [from, setFrom] = useState(`${today.slice(0, 4)}-01-01`);
  const [to, setTo] = useState(today);
  const valid = from !== "" && to !== "" && from <= to;
  const href = `/clients/${clientId}/statement?from=${from}&to=${to}`;

  return (
    <section className="rounded-xl border bg-card p-5 shadow-xs">
      <h2 className="text-sm font-semibold">
        Statement of account <span className="font-normal text-muted-foreground" lang="ar" dir="rtl">كشف حساب</span>
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        The balance brought forward, then every invoice, payment and credit note in the period, with a running balance.
      </p>
      <div className="mt-3 grid grid-cols-2 gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor={`from-${uid}`}>From</Label>
          <Input id={`from-${uid}`} type="date" className="figures" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor={`to-${uid}`}>To</Label>
          <Input id={`to-${uid}`} type="date" className="figures" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
        </div>
      </div>
      {valid ? (
        <Button asChild variant="outline" className="mt-3">
          <a href={href} target="_blank" rel="noopener">
            <FileText /> Download statement (PDF)
          </a>
        </Button>
      ) : (
        <Button variant="outline" className="mt-3" disabled>
          <FileText /> Download statement (PDF)
        </Button>
      )}
    </section>
  );
}
