"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { api, toFormState } from "@/lib/api";
import type { DocumentInput, FormState, Invoice, LineInput } from "@/lib/types";

function refresh(id?: string) {
  revalidatePath("/invoices");
  revalidatePath("/dashboard");
  if (id) revalidatePath(`/invoices/${id}`);
}

/** Create (id = null) or update a draft. A new draft redirects to its own page. */
export async function saveInvoice(id: string | null, input: DocumentInput): Promise<FormState> {
  let saved: Invoice;
  try {
    saved = await api<Invoice>(id ? `/invoices/${encodeURIComponent(id)}` : "/invoices", {
      method: id ? "PUT" : "POST",
      body: input,
    });
  } catch (e) {
    return toFormState(e);
  }
  refresh(saved.id);
  if (!id) redirect(`/invoices/${saved.id}`);
  return { ok: true };
}

/** keepDates: issue with the draft's own (past) dates instead of today's. */
export async function issueInvoice(id: string, keepDates = false): Promise<FormState> {
  try {
    await api(`/invoices/${encodeURIComponent(id)}/issue`, { method: "POST", body: { keepDates } });
  } catch (e) {
    return toFormState(e);
  }
  refresh(id);
  return { ok: true };
}

export async function deleteInvoice(id: string): Promise<FormState> {
  try {
    await api(`/invoices/${encodeURIComponent(id)}`, { method: "DELETE" });
  } catch (e) {
    return toFormState(e);
  }
  refresh();
  redirect("/invoices");
}

export async function recordPayment(id: string, _prev: FormState, formData: FormData): Promise<FormState> {
  const values = {
    amount: String(formData.get("amount") ?? "").trim(),
    paidOn: String(formData.get("paidOn") ?? "").trim(),
    method: String(formData.get("method") ?? "BANK_TRANSFER"),
    reference: String(formData.get("reference") ?? "").trim(),
  };
  try {
    await api(`/invoices/${encodeURIComponent(id)}/payments`, {
      method: "POST",
      body: { ...values, paidOn: values.paidOn || null, reference: values.reference || null },
    });
  } catch (e) {
    return toFormState(e, values);
  }
  refresh(id);
  return { ok: true };
}

/** Money given back to the client after a credit note on a paid invoice. */
export async function recordRefund(id: string, _prev: FormState, formData: FormData): Promise<FormState> {
  const values = {
    amount: String(formData.get("amount") ?? "").trim(),
    paidOn: String(formData.get("paidOn") ?? "").trim(),
    method: String(formData.get("method") ?? "BANK_TRANSFER"),
    reference: String(formData.get("reference") ?? "").trim(),
  };
  try {
    await api(`/invoices/${encodeURIComponent(id)}/refunds`, {
      method: "POST",
      body: { ...values, paidOn: values.paidOn || null, reference: values.reference || null },
    });
  } catch (e) {
    return toFormState(e, values);
  }
  refresh(id);
  return { ok: true };
}

/** Marks an online payment that could not be applied as handled (refunded at the provider). */
export async function resolvePaymentProblem(id: string): Promise<FormState> {
  try {
    await api(`/payment-problems/${encodeURIComponent(id)}/resolve`, { method: "POST" });
  } catch (e) {
    return toFormState(e);
  }
  revalidatePath("/dashboard");
  return { ok: true };
}

export async function voidInvoice(id: string, reason: string): Promise<FormState> {
  try {
    await api(`/invoices/${encodeURIComponent(id)}/void`, { method: "POST", body: { reason } });
  } catch (e) {
    return toFormState(e);
  }
  refresh(id);
  return { ok: true };
}

export async function createCreditNote(id: string, reason: string, lines: LineInput[]): Promise<FormState> {
  try {
    await api(`/invoices/${encodeURIComponent(id)}/credit-notes`, { method: "POST", body: { reason, lines } });
  } catch (e) {
    return toFormState(e);
  }
  refresh(id);
  return { ok: true };
}

export async function getPaymentLink(id: string): Promise<FormState & { url?: string }> {
  try {
    const { url } = await api<{ url: string }>(`/invoices/${encodeURIComponent(id)}/payment-link`, { method: "POST" });
    return { ok: true, url };
  } catch (e) {
    return toFormState(e);
  }
}
