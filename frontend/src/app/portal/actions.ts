"use server";

import { revalidatePath } from "next/cache";
import { api, ApiError } from "@/lib/api";

/** Public: the client accepts or rejects a quote from their portal. */
export async function answerQuote(token: string, quoteId: string, accept: boolean): Promise<{ error?: string; status?: string }> {
  try {
    const { status } = await api<{ status: string }>(
      `/public/portal/${encodeURIComponent(token)}/quotes/${encodeURIComponent(quoteId)}/${accept ? "accept" : "reject"}`,
      { method: "POST", auth: false },
    );
    revalidatePath(`/portal/${token}`);
    return { status };
  } catch (e) {
    if (e instanceof ApiError) return { error: e.detail };
    throw e;
  }
}
