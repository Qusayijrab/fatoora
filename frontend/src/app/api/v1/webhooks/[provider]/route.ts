const API_URL = process.env.API_URL ?? "http://localhost:8080";

/**
 * Payment providers post webhooks to the public site; this forwards them untouched to
 * the backend, which verifies the signature over the exact raw body.
 */
export async function POST(request: Request, { params }: RouteContext<"/api/v1/webhooks/[provider]">) {
  const { provider } = await params;
  const headers = new Headers({ "Content-Type": request.headers.get("content-type") ?? "application/json" });
  for (const name of ["stripe-signature", "x-mock-signature"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  const res = await fetch(`${API_URL}/api/v1/webhooks/${encodeURIComponent(provider)}`, {
    method: "POST",
    headers,
    body: await request.text(),
    cache: "no-store",
  });
  return new Response(await res.text(), { status: res.status, headers: { "Content-Type": "application/json" } });
}
