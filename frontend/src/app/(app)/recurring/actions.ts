"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { api, toFormState } from "@/lib/api";
import type { FormState, Recurring, RecurringInput } from "@/lib/types";

function refresh(id?: string) {
  revalidatePath("/recurring");
  revalidatePath("/invoices");
  if (id) revalidatePath(`/recurring/${id}`);
}

export async function saveRecurring(id: string | null, input: RecurringInput): Promise<FormState> {
  let saved: Recurring;
  try {
    saved = await api<Recurring>(id ? `/recurring/${encodeURIComponent(id)}` : "/recurring", {
      method: id ? "PUT" : "POST",
      body: input,
    });
  } catch (e) {
    return toFormState(e);
  }
  refresh(saved.id);
  if (!id) redirect(`/recurring/${saved.id}`);
  return { ok: true };
}

export async function recurringAction(id: string, action: "pause" | "resume" | "end"): Promise<FormState> {
  try {
    await api(`/recurring/${encodeURIComponent(id)}/${action}`, { method: "POST" });
  } catch (e) {
    return toFormState(e);
  }
  refresh(id);
  return { ok: true };
}

export async function generateNow(id: string): Promise<FormState> {
  let invoiceId: string;
  try {
    ({ invoiceId } = await api<{ invoiceId: string }>(`/recurring/${encodeURIComponent(id)}/generate-now`, { method: "POST" }));
  } catch (e) {
    return toFormState(e);
  }
  refresh(id);
  redirect(`/invoices/${invoiceId}`);
}
