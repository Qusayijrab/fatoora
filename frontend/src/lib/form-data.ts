import { NONE_VALUE } from "./types";

/**
 * Reads named fields from a form submission. Blank strings and the "none" select
 * sentinel become null, so the backend stores "not provided" rather than "".
 */
export function readForm<K extends string>(formData: FormData, keys: readonly K[]): Record<K, string | null> {
  const out = {} as Record<K, string | null>;
  for (const key of keys) {
    const raw = formData.get(key);
    const value = typeof raw === "string" ? raw.trim() : "";
    out[key] = value === "" || value === NONE_VALUE ? null : value;
  }
  return out;
}

/** The same values as strings, to refill the form after a validation error. */
export function echo(values: Record<string, string | null>): Record<string, string> {
  return Object.fromEntries(Object.entries(values).map(([k, v]) => [k, v ?? ""]));
}
