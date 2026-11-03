"use server";

import { api, toFormState } from "@/lib/api";
import type { FormState } from "@/lib/types";

/** Emails an issued invoice/credit note or a sent quote, with the PDF attached. */
export async function emailDocument(
  kind: "invoices" | "quotes",
  id: string,
  to: string,
  message: string,
): Promise<FormState & { sentTo?: string }> {
  try {
    const { sentTo } = await api<{ sentTo: string }>(`/${kind}/${encodeURIComponent(id)}/email`, {
      method: "POST",
      body: { to: to.trim() || null, message: message.trim() || null },
    });
    return { ok: true, sentTo };
  } catch (e) {
    return toFormState(e);
  }
}
