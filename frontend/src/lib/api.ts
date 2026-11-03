import "server-only";

import { redirect } from "next/navigation";
import { getToken } from "./session";
import type { FormState, Me, Member, Organization } from "./types";

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
  method?: "GET" | "POST" | "PATCH" | "DELETE";
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
