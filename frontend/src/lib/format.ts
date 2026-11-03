import type { Money } from "./types";

/**
 * Formats a decimal string without ever converting it to a JS number, so
 * "1250.500" stays exact: groups the integer part, keeps every decimal.
 */
export function formatAmount(amount: string): string {
  const negative = amount.startsWith("-");
  const [int, frac] = (negative ? amount.slice(1) : amount).split(".");
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return `${negative ? "-" : ""}${grouped}${frac !== undefined ? `.${frac}` : ""}`;
}

/** "JOD 1,250.500" */
export function formatMoney(money: Money | null | undefined): string {
  if (!money) return "—";
  return `${money.currency} ${formatAmount(money.amount)}`;
}

const dateFormat = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });

/** "3 Nov 2026". Accepts ISO dates ("2026-11-03") and timestamps. */
export function formatDate(value: string | null | undefined): string {
  if (!value) return "—";
  const date = value.length === 10 ? new Date(`${value}T00:00:00`) : new Date(value);
  return dateFormat.format(date);
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase() ?? "")
    .join("");
}
