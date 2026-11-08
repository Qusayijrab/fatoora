"use server";

import { redirect } from "next/navigation";
import { api, toFormState } from "@/lib/api";
import { setSession } from "@/lib/session";
import type { FormState, TokenResponse } from "@/lib/types";

// Server Actions run on the Next.js server. Next.js checks the Origin header on
// every action call, which protects them against CSRF.

function field(formData: FormData, name: string): string {
  return String(formData.get(name) ?? "");
}

export async function login(_prev: FormState, formData: FormData): Promise<FormState> {
  const email = field(formData, "email");
  try {
    const token = await api<TokenResponse>("/auth/login", {
      method: "POST",
      auth: false,
      body: { email, password: field(formData, "password") },
    });
    await setSession(token);
  } catch (e) {
    return toFormState(e, { email });
  }
  redirect("/dashboard");
}

export async function register(_prev: FormState, formData: FormData): Promise<FormState> {
  const values = {
    fullName: field(formData, "fullName"),
    email: field(formData, "email"),
    organizationName: field(formData, "organizationName"),
    defaultCurrency: field(formData, "defaultCurrency"),
    timeZone: field(formData, "timeZone"),
  };
  try {
    const token = await api<TokenResponse>("/auth/register", {
      method: "POST",
      auth: false,
      body: { ...values, password: field(formData, "password") },
    });
    await setSession(token);
  } catch (e) {
    return toFormState(e, values);
  }
  redirect("/dashboard");
}

/** The button on /verify-email/[token]: confirms the address (a POST, so link scanners can't use the token up). */
export async function verifyEmail(token: string): Promise<FormState> {
  try {
    await api("/auth/verify-email", { method: "POST", body: { token }, auth: false });
  } catch (e) {
    return toFormState(e);
  }
  return { ok: true };
}
