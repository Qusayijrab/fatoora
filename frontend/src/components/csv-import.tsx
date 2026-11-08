"use client";

import { CircleAlert, CircleCheck, Download, FileUp, SkipForward } from "lucide-react";
import Link from "next/link";
import { useRef, useState, useTransition } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import type { ImportReport } from "@/lib/types";
import { cn } from "@/lib/utils";
import { importCsv } from "@/app/(app)/import-actions";

const TEMPLATES = {
  clients:
    "Name,Email,Phone,Tax ID,Address,City,Country,Currency,Notes\n" +
    '"Petra Design, LLC",billing@petra.jo,+962 6 555 0101,JO-0123456,Rainbow St 12,Amman,JO,JOD,Pays by transfer\n',
  products: "Name,SKU,Description,Unit,Price,Currency,Tax\n" + "Logo design,LOGO-1,Primary and secondary logo,project,850.000,JOD,16%\n",
};

/**
 * Upload a CSV, see every row checked, then import. Two steps so nothing is written
 * until the file is clean; the server imports all the ok rows or none.
 */
export function CsvImport({ kind }: { kind: "clients" | "products" }) {
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<{ name: string; text: string } | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();
  const noun = kind === "clients" ? "client" : "product";

  async function choose(f: File | undefined) {
    if (!f) return;
    const text = await f.text();
    setFile({ name: f.name, text });
    setReport(null);
    setError(null);
    startTransition(async () => {
      const result = await importCsv(kind, text, true);
      if (result.report) setReport(result.report);
      else setError(result.error ?? "Could not read the file");
    });
  }

  function runImport() {
    if (!file) return;
    startTransition(async () => {
      const result = await importCsv(kind, file.text, false);
      if (result.report?.imported) {
        toast.success(`Imported ${result.report.ok} ${result.report.ok === 1 ? noun : kind}`);
        setReport(result.report);
        setFile(null);
      } else if (result.report) {
        setReport(result.report);
      } else {
        setError(result.error ?? "The import failed");
      }
    });
  }

  function downloadTemplate() {
    const url = URL.createObjectURL(new Blob(["\uFEFF" + TEMPLATES[kind]], { type: "text/csv;charset=utf-8" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = `fatoora-${kind}-template.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  const done = report?.imported;

  return (
    <div className="grid gap-6">
      <section className="rounded-xl border bg-card p-5 shadow-xs sm:p-6">
        <ol className="grid gap-2 text-sm text-muted-foreground">
          <li>
            1. In Excel or Google Sheets, put one {noun} per row with a header row, then save as <strong>CSV UTF-8</strong>.
            Headers can be in English or Arabic; only <strong>Name</strong> is required.
          </li>
          <li>2. Upload it. Every row is checked first; nothing is saved yet.</li>
          <li>3. Fix anything marked, upload again, then import.</li>
        </ol>
        <div className="mt-4 flex flex-wrap gap-2">
          <input
            ref={input}
            type="file"
            accept=".csv,text/csv"
            className="sr-only"
            aria-label="CSV file"
            data-testid="csv-file"
            onChange={(e) => {
              void choose(e.target.files?.[0]);
              e.target.value = "";
            }}
          />
          <Button onClick={() => input.current?.click()} disabled={pending}>
            <FileUp /> {file ? "Choose another file" : "Choose CSV file"}
          </Button>
          <Button variant="outline" onClick={downloadTemplate}>
            <Download /> Download template
          </Button>
        </div>
        {file && !done && <p className="mt-3 text-sm">File: {file.name}</p>}
        {pending && <p className="mt-3 text-sm text-muted-foreground">Checking…</p>}
        {error && (
          <p role="alert" className="mt-3 text-sm text-destructive">
            {error}
          </p>
        )}
      </section>

      {report && (
        <section className="rounded-xl border bg-card p-5 shadow-xs sm:p-6" data-testid="import-report">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm">
              {done ? (
                <span className="font-medium">
                  Imported {report.ok}.{" "}
                  <Link href={`/${kind}`} className="text-primary underline-offset-4 hover:underline">
                    See {kind}
                  </Link>
                </span>
              ) : (
                <>
                  <span className="font-medium">{report.ok} ready</span>
                  {report.skipped > 0 && <> · {report.skipped} already exist (skipped)</>}
                  {report.errors > 0 && <span className="text-destructive"> · {report.errors} need fixing</span>}
                </>
              )}
            </p>
            {!done && (
              <Button onClick={runImport} disabled={pending || report.errors > 0 || report.ok === 0}>
                Import {report.ok} {report.ok === 1 ? noun : kind}
              </Button>
            )}
          </div>
          {!done && report.errors > 0 && (
            <p className="mt-2 text-xs text-muted-foreground">Fix the rows below in your sheet and upload again. All rows import together or not at all.</p>
          )}
          <ul className="mt-4 divide-y text-sm">
            {report.rows.map((r) => (
              <li key={r.line} className="flex items-start gap-3 py-2">
                <span className="figures w-14 shrink-0 text-xs text-muted-foreground">Row {r.line}</span>
                {r.status === "OK" ? (
                  <CircleCheck className="mt-0.5 size-4 shrink-0 text-primary" aria-label="Ready" />
                ) : r.status === "SKIP" ? (
                  <SkipForward className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-label="Skipped" />
                ) : (
                  <CircleAlert className="mt-0.5 size-4 shrink-0 text-destructive" aria-label="Needs fixing" />
                )}
                <span className="min-w-0">
                  <span className={cn("block truncate", r.status === "SKIP" && "text-muted-foreground")} dir="auto">
                    {r.name || "(no name)"}
                  </span>
                  {r.messages.map((m) => (
                    <span key={m} className={cn("block text-xs", r.status === "ERROR" ? "text-destructive" : "text-muted-foreground")}>
                      {m}
                    </span>
                  ))}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
