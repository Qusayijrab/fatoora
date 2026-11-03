"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { api, toFormState } from "@/lib/api";
import { echo, readForm } from "@/lib/form-data";
import type { FormState, Product } from "@/lib/types";

const FIELDS = ["name", "description", "sku", "unit", "unitPrice", "currency", "taxRateId"] as const;

export async function saveProduct(id: string | null, _prev: FormState, formData: FormData): Promise<FormState> {
  const values = readForm(formData, FIELDS);
  let saved: Product;
  try {
    saved = await api<Product>(id ? `/products/${encodeURIComponent(id)}` : "/products", {
      method: id ? "PUT" : "POST",
      body: values,
    });
  } catch (e) {
    return toFormState(e, echo(values));
  }
  revalidatePath("/products");
  if (!id) redirect(`/products/${saved.id}`);
  return { ok: true };
}

export async function setProductArchived(id: string, archived: boolean): Promise<FormState> {
  try {
    await api(`/products/${encodeURIComponent(id)}/${archived ? "archive" : "restore"}`, { method: "POST" });
  } catch (e) {
    return toFormState(e);
  }
  revalidatePath("/products");
  revalidatePath(`/products/${id}`);
  return { ok: true };
}
