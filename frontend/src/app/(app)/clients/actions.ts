"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { api, toFormState } from "@/lib/api";
import { echo, readForm } from "@/lib/form-data";
import type { Client, FormState } from "@/lib/types";

const FIELDS = [
  "name",
  "email",
  "phone",
  "taxId",
  "addressLine1",
  "addressLine2",
  "city",
  "region",
  "postalCode",
  "country",
  "currency",
  "notes",
] as const;

export async function saveClient(id: string | null, _prev: FormState, formData: FormData): Promise<FormState> {
  const values = readForm(formData, FIELDS);
  let saved: Client;
  try {
    saved = await api<Client>(id ? `/clients/${encodeURIComponent(id)}` : "/clients", {
      method: id ? "PUT" : "POST",
      body: values,
    });
  } catch (e) {
    return toFormState(e, echo(values));
  }
  revalidatePath("/clients");
  if (!id) redirect(`/clients/${saved.id}?created=1`);
  return { ok: true };
}

export async function setClientArchived(id: string, archived: boolean): Promise<FormState> {
  try {
    await api(`/clients/${encodeURIComponent(id)}/${archived ? "archive" : "restore"}`, { method: "POST" });
  } catch (e) {
    return toFormState(e);
  }
  revalidatePath("/clients");
  revalidatePath(`/clients/${id}`);
  return { ok: true };
}

export async function getPortalLink(id: string): Promise<FormState & { url?: string }> {
  try {
    const { url } = await api<{ url: string }>(`/clients/${encodeURIComponent(id)}/portal-link`, { method: "POST" });
    return { ok: true, url };
  } catch (e) {
    return toFormState(e);
  }
}

export async function revokePortalLink(id: string): Promise<FormState> {
  try {
    await api(`/clients/${encodeURIComponent(id)}/portal-link`, { method: "DELETE" });
  } catch (e) {
    return toFormState(e);
  }
  return { ok: true };
}
