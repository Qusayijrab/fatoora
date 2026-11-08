import { apiRaw } from "@/lib/api";

/** Streams a report CSV from the backend (the browser never holds the token). */
export async function GET(request: Request, { params }: RouteContext<"/reports/export/[kind]">) {
  const { kind } = await params;
  if (kind !== "aging" && kind !== "tax") return new Response("Not found", { status: 404 });
  const incoming = new URL(request.url).searchParams;
  const query = new URLSearchParams();
  for (const key of ["from", "to"]) {
    const value = incoming.get(key);
    if (value && /^\d{4}-\d{2}-\d{2}$/.test(value)) query.set(key, value);
  }
  const qs = query.toString();
  const res = await apiRaw(`/reports/${kind}.csv${qs ? `?${qs}` : ""}`);
  if (!res.ok) return new Response("Could not create the report", { status: res.status });
  return new Response(res.body, {
    headers: {
      "Content-Type": "text/csv; charset=utf-8",
      "Content-Disposition": res.headers.get("Content-Disposition") ?? `attachment; filename="${kind}.csv"`,
      "Cache-Control": "private, no-store",
    },
  });
}
