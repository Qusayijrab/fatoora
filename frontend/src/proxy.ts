import { NextResponse, type NextRequest } from "next/server";

const SESSION_COOKIE = "fatoora_token";
const PUBLIC_PATHS = ["/", "/login", "/register", "/session-expired", "/portal", "/pay"];

/**
 * Optimistic redirects only: it looks at the cookie without verifying the signature.
 * The real check is the backend, which verifies the JWT on every API call.
 * This just avoids flashing a protected page before bouncing to /login.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const isPublic = PUBLIC_PATHS.some((p) => pathname === p || (p !== "/" && pathname.startsWith(`${p}/`)));
  const loggedIn = hasUnexpiredToken(request.cookies.get(SESSION_COOKIE)?.value);

  if (!isPublic && !loggedIn) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  if ((pathname === "/" || pathname === "/login" || pathname === "/register") && loggedIn) {
    return NextResponse.redirect(new URL("/dashboard", request.url));
  }
  return NextResponse.next();
}

/** Reads the JWT's "exp" claim (no signature check: that's the backend's job). */
function hasUnexpiredToken(token: string | undefined): boolean {
  if (!token) return false;
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.exp === "number" && payload.exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

export const config = {
  // Skip static files, images and the favicon.
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:png|svg|jpg|ico)$).*)"],
};
