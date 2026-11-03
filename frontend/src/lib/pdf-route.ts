import "server-only";

import { apiRaw } from "./api";

/** Streams a PDF from the backend through the Next.js server (the browser never holds the token). */
export async function proxyPdf(path: string): Promise<Response> {
  const res = await apiRaw(path);
  if (!res.ok) {
    return new Response(res.status === 404 ? "Not found" : "Could not generate the PDF", { status: res.status });
  }
  return new Response(res.body, {
    headers: {
      "Content-Type": "application/pdf",
      "Content-Disposition": res.headers.get("Content-Disposition") ?? "inline",
      "Cache-Control": "private, no-store",
    },
  });
}
