"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { api, toFormState } from "@/lib/api";
import { clearSession, setSession } from "@/lib/session";
import type { FormState, Organization, Role, TokenResponse } from "@/lib/types";

// Every action calls the backend with the user's own token, so the backend
// re-checks identity, organization and role each time. The UI hiding a button
// is a convenience, never the security boundary.

export async function logout(): Promise<void> {
  await clearSession();
  redirect("/login");
}

export async function switchOrganization(formData: FormData): Promise<void> {
  const organizationId = String(formData.get("organizationId") ?? "");
  const token = await api<TokenResponse>(`/auth/switch-org/${encodeURIComponent(organizationId)}`, {
    method: "POST",
  });
  await setSession(token);
  redirect("/dashboard");
}

export async function createOrganization(_prev: FormState, formData: FormData): Promise<FormState> {
  const values = {
    name: String(formData.get("name") ?? ""),
    defaultCurrency: String(formData.get("defaultCurrency") ?? ""),
    timeZone: String(formData.get("timeZone") ?? ""),
  };
  let token: TokenResponse;
  try {
    const org = await api<Organization>("/organizations", { method: "POST", body: values });
    token = await api<TokenResponse>(`/auth/switch-org/${org.id}`, { method: "POST" });
  } catch (e) {
    return toFormState(e, values);
  }
  await setSession(token);
  redirect("/dashboard");
}

export async function addMember(_prev: FormState, formData: FormData): Promise<FormState> {
  const values = { email: String(formData.get("email") ?? ""), role: String(formData.get("role") ?? "MEMBER") };
  try {
    await api("/members", { method: "POST", body: values });
  } catch (e) {
    return toFormState(e, values);
  }
  revalidatePath("/settings/members");
  return { ok: true };
}

export async function setMemberRole(membershipId: string, role: Role): Promise<FormState> {
  try {
    await api(`/members/${encodeURIComponent(membershipId)}`, { method: "PATCH", body: { role } });
  } catch (e) {
    return toFormState(e);
  }
  revalidatePath("/settings/members");
  return { ok: true };
}

export async function removeMember(membershipId: string): Promise<FormState> {
  try {
    await api(`/members/${encodeURIComponent(membershipId)}`, { method: "DELETE" });
  } catch (e) {
    return toFormState(e);
  }
  revalidatePath("/settings/members");
  return { ok: true };
}

/** "Resend" on the confirm-your-email banner. */
export async function resendVerificationEmail(): Promise<FormState> {
  try {
    await api("/me/verification-email", { method: "POST" });
  } catch (e) {
    return toFormState(e);
  }
  return { ok: true };
}
