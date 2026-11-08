/**
 * Cookie names and options shared by lib/session.ts (Server Actions) and proxy.ts
 * (which renews the session before pages render). No server-only imports here.
 *
 * - fatoora_token: the 1-hour access token (a JWT the backend checks on every call).
 * - fatoora_refresh: the 30-day refresh token, traded for a new pair before the access
 *   token runs out. Both httpOnly (page JavaScript can't read them), sameSite=lax and,
 *   in production, secure.
 */
export const SESSION_COOKIE = "fatoora_token";
export const REFRESH_COOKIE = "fatoora_refresh";

export type TokenPair = { accessToken: string; expiresIn: number; refreshToken?: string | null; refreshExpiresIn?: number };

export function cookieOptions(maxAgeSeconds: number) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
    maxAge: maxAgeSeconds,
  };
}

/** Seconds until the JWT expires (no signature check: that's the backend's job). Negative or NaN if unusable. */
export function secondsLeft(token: string | undefined): number {
  if (!token) return Number.NaN;
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.exp === "number" ? payload.exp - Date.now() / 1000 : Number.NaN;
  } catch {
    return Number.NaN;
  }
}
