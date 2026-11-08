import { NextResponse, type NextRequest } from "next/server";
import { REFRESH_COOKIE, SESSION_COOKIE, cookieOptions, secondsLeft, type TokenPair } from "@/lib/session-cookies";

const PUBLIC_PATHS = ["/", "/login", "/register", "/session-expired", "/verify-email", "/forgot-password", "/reset-password", "/portal", "/pay", "/api/v1/webhooks"];
const API_URL = process.env.API_URL ?? "http://localhost:8080";

/** Renew this long before the access token expires, so a page never renders with one about to die. */
const RENEW_WITHIN_SECONDS = 120;

/**
 * Keeps the session alive and redirects optimistically. It reads the access token's
 * expiry without verifying the signature (the backend verifies every API call). When
 * it is missing or about to expire and a refresh token exists, it trades that for a new
 * pair, sets them on the browser and on the request being handled, so the page that
 * renders now already uses the new token.
 */
export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const isPublic = PUBLIC_PATHS.some((p) => pathname === p || (p !== "/" && pathname.startsWith(`${p}/`)));

  let renewed: TokenPair | null = null;
  let refreshFailed = false;
  const access = request.cookies.get(SESSION_COOKIE)?.value;
  const refresh = request.cookies.get(REFRESH_COOKIE)?.value;
  const left = secondsLeft(access);
  if (refresh && !(left > RENEW_WITHIN_SECONDS)) {
    const result = await renew(refresh);
    if (result === "rejected") refreshFailed = true;
    else if (result !== "unreachable") renewed = result;
  }
  const loggedIn = renewed !== null || left > 0;

  let response: NextResponse;
  if (!isPublic && !loggedIn) {
    response = NextResponse.redirect(new URL("/login", request.url));
  } else if ((pathname === "/" || pathname === "/login" || pathname === "/register") && loggedIn) {
    response = NextResponse.redirect(new URL("/dashboard", request.url));
  } else if (renewed) {
    request.cookies.set(SESSION_COOKIE, renewed.accessToken);
    if (renewed.refreshToken) request.cookies.set(REFRESH_COOKIE, renewed.refreshToken);
    response = NextResponse.next({ request: { headers: request.headers } });
  } else {
    response = NextResponse.next();
  }

  if (renewed) {
    response.cookies.set(SESSION_COOKIE, renewed.accessToken, cookieOptions(renewed.expiresIn));
    if (renewed.refreshToken) {
      response.cookies.set(REFRESH_COOKIE, renewed.refreshToken, cookieOptions(renewed.refreshExpiresIn ?? 0));
    }
  } else if (refreshFailed) {
    response.cookies.delete(REFRESH_COOKIE);
    if (!(left > 0)) response.cookies.delete(SESSION_COOKIE);
  }
  return response;
}

/** The new pair; "rejected" if the backend refused the token (log out); "unreachable" to keep things as they are. */
async function renew(refreshToken: string): Promise<TokenPair | "rejected" | "unreachable"> {
  try {
    const res = await fetch(`${API_URL}/api/v1/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ refreshToken }),
      cache: "no-store",
    });
    if (res.ok) return (await res.json()) as TokenPair;
    return res.status === 401 || res.status === 400 ? "rejected" : "unreachable";
  } catch {
    return "unreachable";
  }
}

export const config = {
  // Skip static files, images and the favicon.
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:png|svg|jpg|ico)$).*)"],
};
