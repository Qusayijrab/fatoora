import { formatDate } from "@/lib/format";
import type { AuditEntry } from "@/lib/types";
import { SummaryText } from "@/components/summary-text";

/** A document's history from the audit log: who did what, when. */
export function History({ entries }: { entries: AuditEntry[] }) {
  if (entries.length === 0) return null;
  return (
    <section className="rounded-xl border bg-card shadow-xs p-5">
      <h2 className="text-sm font-semibold">History</h2>
      <ol className="mt-3 grid gap-3 border-s ps-4">
        {entries.map((e) => (
          <li key={e.id} className="relative text-sm">
            <span aria-hidden className="absolute -start-[1.3rem] top-1.5 size-2 rounded-full bg-primary" />
            <p dir="auto">
              <SummaryText text={e.summary} />
            </p>
            <p className="figures text-xs text-muted-foreground">
              {e.actorName ?? "Fatoora (automatic)"} · {formatDate(e.at)}{" "}
              {new Date(e.at).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}
            </p>
          </li>
        ))}
      </ol>
    </section>
  );
}
