const API_URL = process.env.API_URL ?? "http://localhost:8080";

/** Public PDF from the client portal (the portal token is the permission). */
export async function GET(_request: Request, { params }: RouteContext<"/portal/[token]/invoices/[id]/pdf">) {
  const { token, id } = await params;
  const res = await fetch(
    `${API_URL}/api/v1/public/portal/${encodeURIComponent(token)}/invoices/${encodeURIComponent(id)}/pdf`,
    { cache: "no-store" },
  );
  if (!res.ok) return new Response("Not found", { status: 404 });
  return new Response(res.body, {
    headers: {
      "Content-Type": "application/pdf",
      "Content-Disposition": res.headers.get("Content-Disposition") ?? "inline",
      "Cache-Control": "private, no-store",
    },
  });
}
