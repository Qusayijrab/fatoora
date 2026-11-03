import { proxyPdf } from "@/lib/pdf-route";

export async function GET(_request: Request, { params }: RouteContext<"/quotes/[id]/pdf">) {
  const { id } = await params;
  return proxyPdf(`/quotes/${encodeURIComponent(id)}/pdf`);
}
