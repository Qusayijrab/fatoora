import "server-only";

import { cookies } from "next/headers";
import type { TokenResponse } from "./types";

/**
 * The backend JWT lives in an httpOnly cookie:
 * - httpOnly: page JavaScript can't read it, so an XSS bug can't steal it.
 * - sameSite=lax: the browser won't send it on cross-site POSTs (CSRF protection).
 * - secure in production: only sent over HTTPS.
 * The browser never talks to the backend directly; our Next.js server does,
 * adding the token as a Bearer header.
 */
export const SESSION_COOKIE = "fatoora_token";

export async function getToken(): Promise<string | undefined> {
  return (await cookies()).get(SESSION_COOKIE)?.value;
}

/** Only callable from Server Actions and Route Handlers. */
export async function setSession(token: TokenResponse): Promise<void> {
  (await cookies()).set(SESSION_COOKIE, token.accessToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: token.expiresIn,
  });
}

/** Only callable from Server Actions and Route Handlers. */
export async function clearSession(): Promise<void> {
  (await cookies()).delete(SESSION_COOKIE);
}
