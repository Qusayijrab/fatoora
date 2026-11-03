import { proxyPdf } from "@/lib/pdf-route";

export async function GET(_request: Request, { params }: RouteContext<"/invoices/[id]/pdf">) {
  const { id } = await params;
  return proxyPdf(`/invoices/${encodeURIComponent(id)}/pdf`);
}
