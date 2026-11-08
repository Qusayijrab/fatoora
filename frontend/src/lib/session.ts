import "server-only";

import { cookies } from "next/headers";
import { REFRESH_COOKIE, SESSION_COOKIE, cookieOptions } from "./session-cookies";
import type { TokenResponse } from "./types";

/**
 * The backend tokens live in httpOnly cookies (see session-cookies.ts):
 * - httpOnly: page JavaScript can't read them, so an XSS bug can't steal them.
 * - sameSite=lax: the browser won't send them on cross-site POSTs (CSRF protection).
 * - secure in production: only sent over HTTPS.
 * The browser never talks to the backend directly; our Next.js server does,
 * adding the access token as a Bearer header. proxy.ts renews it with the refresh
 * token shortly before it expires, so a session lasts as long as it is used.
 */
export { SESSION_COOKIE };

const API_URL = process.env.API_URL ?? "http://localhost:8080";

export async function getToken(): Promise<string | undefined> {
  return (await cookies()).get(SESSION_COOKIE)?.value;
}

/**
 * Only callable from Server Actions and Route Handlers. A previous session on this
 * browser (e.g. before switching organization) is revoked first.
 */
export async function setSession(token: TokenResponse): Promise<void> {
  const jar = await cookies();
  const previous = jar.get(REFRESH_COOKIE)?.value;
  if (previous && previous !== token.refreshToken) await revoke(previous);
  jar.set(SESSION_COOKIE, token.accessToken, cookieOptions(token.expiresIn));
  if (token.refreshToken) jar.set(REFRESH_COOKIE, token.refreshToken, cookieOptions(token.refreshExpiresIn));
}

/** Only callable from Server Actions and Route Handlers. Ends the session on the server too. */
export async function clearSession(): Promise<void> {
  const jar = await cookies();
  const refresh = jar.get(REFRESH_COOKIE)?.value;
  if (refresh) await revoke(refresh);
  jar.delete(SESSION_COOKIE);
  jar.delete(REFRESH_COOKIE);
}

/** Best effort: if the backend is unreachable the cookie is still deleted, and the token expires on its own. */
async function revoke(refreshToken: string): Promise<void> {
  await fetch(`${API_URL}/api/v1/auth/logout`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
    cache: "no-store",
  }).catch(() => undefined);
}
