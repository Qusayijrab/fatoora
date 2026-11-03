import { currencyDecimals } from "./currencies";
import { formatAmount } from "./format";

/**
 * Exact money math for live previews in the editor, using BigInt minor units. It mirrors
 * the backend's rules (unit price x quantity, then tax per line, both rounded HALF_UP),
 * so the preview matches what the server stores. The server is still the authority.
 */

const AMOUNT = /^\d{1,13}(\.\d{1,3})?$/;
const QUANTITY = /^\d{1,9}(\.\d{1,3})?$/;

/** "25.5" in JOD -> 25500n. Null when not a valid amount for that currency. */
export function parseMinor(value: string, currency: string): bigint | null {
  const v = value.trim();
  if (!AMOUNT.test(v)) return null;
  const decimals = currencyDecimals(currency);
  const [int, frac = ""] = v.split(".");
  if (frac.replace(/0+$/, "").length > decimals) return null;
  return BigInt(int + frac.padEnd(decimals, "0").slice(0, decimals));
}

/** A decimal with up to 3 places as an integer count of thousandths: "2.5" -> 2500n. */
function thousandths(value: string, pattern = QUANTITY): bigint | null {
  const v = value.trim();
  if (!pattern.test(v)) return null;
  const [int, frac = ""] = v.split(".");
  return BigInt(int + frac.padEnd(3, "0"));
}

/** n / d rounded half up, for n >= 0. */
function divHalfUp(n: bigint, d: bigint): bigint {
  return (2n * n + d) / (2n * d);
}

export type LineAmounts = { subtotal: bigint; tax: bigint; total: bigint };

export function lineAmounts(unitPrice: string, quantity: string, ratePercent: string, currency: string): LineAmounts | null {
  const price = parseMinor(unitPrice, currency);
  const qty = thousandths(quantity);
  const rate = thousandths(ratePercent || "0", /^\d{1,3}(\.\d{1,3})?$/);
  if (price === null || qty === null || qty === 0n || rate === null) return null;
  const subtotal = divHalfUp(price * qty, 1000n);
  const tax = divHalfUp(subtotal * rate, 100_000n); // rate is in thousandths of a percent
  return { subtotal, tax, total: subtotal + tax };
}

/** 1250500n in JOD -> "1,250.500" */
export function formatMinor(minor: bigint, currency: string): string {
  const decimals = currencyDecimals(currency);
  const negative = minor < 0n;
  const digits = (negative ? -minor : minor).toString().padStart(decimals + 1, "0");
  const plain = decimals === 0 ? digits : `${digits.slice(0, -decimals)}.${digits.slice(-decimals)}`;
  return `${negative ? "-" : ""}${formatAmount(plain)}`;
}

export function amountPlaceholder(currency: string): string {
  const d = currencyDecimals(currency);
  return d === 0 ? "0" : `0.${"0".repeat(d)}`;
}
