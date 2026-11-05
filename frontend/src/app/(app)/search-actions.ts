"use server";

import { getClients, getProducts } from "@/lib/api";
import type { Client, Product } from "@/lib/types";

/** How many matches a picker shows at a time; typing narrows the search on the server. */
const PICKER_PAGE = 20;

/** Active clients matching {@code q} (name, email or tax id), for the document editor's client picker. */
export async function searchClients(q: string): Promise<Client[]> {
  return (await getClients({ q: q.trim() || undefined, size: PICKER_PAGE })).items;
}

/** Active products matching {@code q}, for the line item picker. */
export async function searchProducts(q: string): Promise<Product[]> {
  return (await getProducts({ q: q.trim() || undefined, size: PICKER_PAGE })).items;
}
