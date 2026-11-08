"use server";

import { revalidatePath } from "next/cache";
import { api, toFormState } from "@/lib/api";
import type { FormState, ImportReport } from "@/lib/types";

/** Checks (dryRun) or imports a CSV file of clients or products. Nothing is written by a dry run. */
export async function importCsv(
  kind: "clients" | "products",
  csv: string,
  dryRun: boolean,
): Promise<FormState & { report?: ImportReport }> {
  try {
    const report = await api<ImportReport>(`/${kind}/import?dryRun=${dryRun}`, {
      method: "POST",
      text: { body: csv, contentType: "text/csv; charset=utf-8" },
    });
    if (report.imported) revalidatePath(`/${kind}`);
    return { ok: true, report };
  } catch (e) {
    return toFormState(e);
  }
}
