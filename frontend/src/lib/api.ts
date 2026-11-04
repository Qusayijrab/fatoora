import "server-only";

import { notFound, redirect } from "next/navigation";
import { getToken } from "./session";
import type {
  AuditEntry,
  Client,
  ExchangeRate,
  Invoice,
  InvoiceSummary,
  Quote,
  QuoteSummary,
  Recurring,
  RecurringRun,
  FormState,
  Me,
  Member,
  Organization,
  OrganizationDetails,
  Overview,
  Page,
  Product,
  Summary,
  TaxRate,
} from "./types";

const API_URL = process.env.API_URL ?? "http://localhost:8080";

/** A problem+json error from the backend (RFC 7807). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly detail: string,
    readonly fieldErrors?: Record<string, string>,
  ) {
    super(detail);
  }
}

type Options = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  /** Send the session token? Off for login/register. */
  auth?: boolean;
};

/**
 * Calls the backend from the Next.js server. If an authenticated call gets 401
 * (token expired or invalid), the user is sent to /session-expired, which clears
 * the cookie and shows the login page.
 */
export async function api<T>(path: string, { method = "GET", body, auth = true }: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (auth) {
    const token = await getToken();
    if (!token) redirect("/login");
    headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${API_URL}/api/v1${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
  });

  if (res.status === 401 && auth) redirect("/session-expired");
  // A signed-in page asked for something that does not exist (or belongs to another organization): show
  // the 404 page. Public pages (payment links, portal) catch the error and explain it in their own words.
  if (res.status === 404 && method === "GET" && auth) notFound();
  if (res.status === 204) return undefined as T;

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new ApiError(res.status, data.detail ?? res.statusText, data.errors);
  }
  return data as T;
}

/** Turns a failed call into form state; rethrows anything that isn't an API error. */
export function toFormState(e: unknown, values?: Record<string, string>): FormState {
  if (e instanceof ApiError) {
    return { error: e.detail, fieldErrors: e.fieldErrors, values };
  }
  throw e;
}

// Typed helpers, one per endpoint the UI uses.
export const getMe = () => api<Me>("/me");
export const getMyOrganizations = () => api<Organization[]>("/organizations");
export const getMembers = () => api<Member[]>("/members");
export const getCurrentOrganization = () => api<OrganizationDetails>("/organizations/current");
export const getSummary = () => api<Summary>("/dashboard/summary");
export const getTaxRates = (includeArchived = false) =>
  api<TaxRate[]>(`/tax-rates${includeArchived ? "?includeArchived=true" : ""}`);

function listQuery(params: { q?: string; includeArchived?: boolean; page?: number; size?: number }) {
  const search = new URLSearchParams();
  if (params.q) search.set("q", params.q);
  if (params.includeArchived) search.set("includeArchived", "true");
  if (params.page) search.set("page", String(params.page));
  if (params.size) search.set("size", String(params.size));
  const s = search.toString();
  return s ? `?${s}` : "";
}

export const getClients = (params: { q?: string; includeArchived?: boolean; page?: number; size?: number } = {}) =>
  api<Page<Client>>(`/clients${listQuery(params)}`);
export const getClient = (id: string) => api<Client>(`/clients/${encodeURIComponent(id)}`);
export const getProducts = (params: { q?: string; includeArchived?: boolean; page?: number; size?: number } = {}) =>
  api<Page<Product>>(`/products${listQuery(params)}`);
export const getProduct = (id: string) => api<Product>(`/products/${encodeURIComponent(id)}`);

type DocListParams = { status?: string; q?: string; clientId?: string; kind?: string; page?: number; size?: number };

function docQuery(params: DocListParams) {
  const search = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== "") search.set(k, String(v));
  const s = search.toString();
  return s ? `?${s}` : "";
}

export const getInvoices = (params: DocListParams = {}) => api<Page<InvoiceSummary>>(`/invoices${docQuery(params)}`);
export const getInvoice = (id: string) => api<Invoice>(`/invoices/${encodeURIComponent(id)}`);
export const getQuotes = (params: DocListParams = {}) => api<Page<QuoteSummary>>(`/quotes${docQuery(params)}`);
export const getQuote = (id: string) => api<Quote>(`/quotes/${encodeURIComponent(id)}`);

/** Everything the document editor needs to offer pickers. */
export async function getEditorData() {
  const [me, clients, products, taxRates] = await Promise.all([
    getMe(),
    getClients({ size: 100 }),
    getProducts({ size: 100 }),
    getTaxRates(),
  ]);
  // "Today" in the organization's time zone, not the server's.
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: me.organization?.timeZone ?? "UTC" }).format(new Date());
  return {
    today,
    orgCurrency: me.organization?.defaultCurrency ?? "JOD",
    clients: clients.items,
    products: products.items,
    taxRates,
  };
}

/** Raw backend response for binary downloads (PDFs). Caller checks res.ok. */
export async function apiRaw(path: string): Promise<Response> {
  const token = await getToken();
  if (!token) redirect("/login");
  return fetch(`${API_URL}/api/v1${path}`, { headers: { Authorization: `Bearer ${token}` }, cache: "no-store" });
}

export const getRecurringList = () => api<Recurring[]>("/recurring");
export const getRecurring = (id: string) => api<Recurring>(`/recurring/${encodeURIComponent(id)}`);
export const getRecurringRuns = (id: string) => api<RecurringRun[]>(`/recurring/${encodeURIComponent(id)}/runs`);
export const getOverview = () => api<Overview>("/dashboard/overview");
export const getExchangeRates = () => api<ExchangeRate[]>("/exchange-rates");
export const previewRate = (currency: string) =>
  api<{ currency: string; baseCurrency: string; rate: string | null; source: string | null; date: string }>(
    `/exchange-rates/preview?currency=${encodeURIComponent(currency)}`,
  );
export const getAudit = (params: { entityId?: string; entityType?: string; page?: number; size?: number } = {}) => {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== "") q.set(k, String(v));
  return api<Page<AuditEntry>>(`/audit-events${q.size ? `?${q}` : ""}`);
};
