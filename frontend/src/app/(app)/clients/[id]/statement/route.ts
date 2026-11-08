import { proxyPdf } from "@/lib/pdf-route";

/** The client's statement of account (PDF); from, to and currency pass through. */
export async function GET(request: Request, { params }: RouteContext<"/clients/[id]/statement">) {
  const { id } = await params;
  const incoming = new URL(request.url).searchParams;
  const query = new URLSearchParams();
  for (const key of ["from", "to", "currency"]) {
    const value = incoming.get(key);
    if (value) query.set(key, value);
  }
  const qs = query.toString();
  return proxyPdf(`/clients/${encodeURIComponent(id)}/statement${qs ? `?${qs}` : ""}`);
}
