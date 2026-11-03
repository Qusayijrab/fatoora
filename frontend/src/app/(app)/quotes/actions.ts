"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { api, toFormState } from "@/lib/api";
import type { DocumentInput, FormState, Quote } from "@/lib/types";

function refresh(id?: string) {
  revalidatePath("/quotes");
  revalidatePath("/dashboard");
  if (id) revalidatePath(`/quotes/${id}`);
}

export async function saveQuote(id: string | null, input: DocumentInput): Promise<FormState> {
  let saved: Quote;
  try {
    saved = await api<Quote>(id ? `/quotes/${encodeURIComponent(id)}` : "/quotes", {
      method: id ? "PUT" : "POST",
      body: input,
    });
  } catch (e) {
    return toFormState(e);
  }
  refresh(saved.id);
  if (!id) redirect(`/quotes/${saved.id}`);
  return { ok: true };
}

/** send / accept / reject */
export async function quoteTransition(id: string, action: "send" | "accept" | "reject"): Promise<FormState> {
  try {
    await api(`/quotes/${encodeURIComponent(id)}/${action}`, { method: "POST" });
  } catch (e) {
    return toFormState(e);
  }
  refresh(id);
  return { ok: true };
}

export async function deleteQuote(id: string): Promise<FormState> {
  try {
    await api(`/quotes/${encodeURIComponent(id)}`, { method: "DELETE" });
  } catch (e) {
    return toFormState(e);
  }
  refresh();
  redirect("/quotes");
}

export async function convertQuote(id: string): Promise<FormState> {
  let invoiceId: string;
  try {
    ({ invoiceId } = await api<{ invoiceId: string }>(`/quotes/${encodeURIComponent(id)}/convert`, { method: "POST" }));
  } catch (e) {
    return toFormState(e);
  }
  refresh(id);
  revalidatePath("/invoices");
  redirect(`/invoices/${invoiceId}`);
}
