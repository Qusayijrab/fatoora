"use server";

import { revalidatePath } from "next/cache";
import { api, toFormState } from "@/lib/api";
import { echo, readForm } from "@/lib/form-data";
import type { FormState } from "@/lib/types";

export async function saveTaxRate(id: string | null, _prev: FormState, formData: FormData): Promise<FormState> {
  const values = readForm(formData, ["name", "rate"] as const);
  const isDefault = formData.get("isDefault") === "on";
  try {
    await api(id ? `/tax-rates/${encodeURIComponent(id)}` : "/tax-rates", {
      method: id ? "PUT" : "POST",
      body: { name: values.name, rate: values.rate, isDefault },
    });
  } catch (e) {
    return toFormState(e, echo(values));
  }
  revalidatePath("/settings/tax-rates");
  return { ok: true };
}

export async function setTaxRateArchived(id: string, archived: boolean): Promise<FormState> {
  try {
    await api(`/tax-rates/${encodeURIComponent(id)}/${archived ? "archive" : "restore"}`, { method: "POST" });
  } catch (e) {
    return toFormState(e);
  }
  revalidatePath("/settings/tax-rates");
  return { ok: true };
}

const ORG_FIELDS = [
  "name",
  "defaultCurrency",
  "timeZone",
  "legalName",
  "taxId",
  "email",
  "phone",
  "addressLine1",
  "addressLine2",
  "city",
  "country",
] as const;

export async function saveOrganization(_prev: FormState, formData: FormData): Promise<FormState> {
  const values = readForm(formData, ORG_FIELDS);
  try {
    await api("/organizations/current", { method: "PATCH", body: values });
  } catch (e) {
    return toFormState(e, echo(values));
  }
  revalidatePath("/", "layout");
  return { ok: true };
}
