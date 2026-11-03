"use server";

import { redirect } from "next/navigation";
import { api, ApiError } from "@/lib/api";

/** Public: starts a provider checkout for what is still owed, then sends the payer there. */
export async function startCheckout(token: string): Promise<{ error?: string }> {
  let url: string;
  try {
    ({ url } = await api<{ url: string }>(`/public/pay/${encodeURIComponent(token)}/checkout`, { method: "POST", auth: false }));
  } catch (e) {
    if (e instanceof ApiError) return { error: e.detail };
    throw e;
  }
  redirect(url);
}

/** Public (mock provider only): the payer confirms the test payment. */
export async function completeMockCheckout(sessionId: string): Promise<{ error?: string }> {
  let payToken: string;
  try {
    ({ payToken } = await api<{ payToken: string }>(`/public/mock-checkout/${encodeURIComponent(sessionId)}/complete`, {
      method: "POST",
      auth: false,
    }));
  } catch (e) {
    if (e instanceof ApiError) return { error: e.detail };
    throw e;
  }
  redirect(`/pay/${payToken}?status=success`);
}
