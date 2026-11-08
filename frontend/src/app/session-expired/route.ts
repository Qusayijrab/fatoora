import { NextResponse, type NextRequest } from "next/server";
import { REFRESH_COOKIE, SESSION_COOKIE } from "@/lib/session-cookies";

/** Clears a dead token and goes to /login (avoids a /login <-> /dashboard redirect loop). */
export function GET(request: NextRequest) {
  const response = NextResponse.redirect(new URL("/login?expired=1", request.url));
  response.cookies.delete(SESSION_COOKIE);
  response.cookies.delete(REFRESH_COOKIE);
  return response;
}
