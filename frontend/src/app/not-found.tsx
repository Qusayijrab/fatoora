import Link from "next/link";
import { Wordmark } from "@/components/brand";

export const metadata = { title: "Not found" };

/** Unknown pages and expired or mistyped public links (payment, client portal). */
export default function NotFound() {
  return (
    <main className="grid min-h-svh place-items-center bg-background px-4 py-16">
      <div className="grid w-full max-w-md gap-6 rounded-xl border bg-card p-8 text-center shadow-xs">
        <Link href="/" className="justify-self-center" aria-label="Fatoora home">
          <Wordmark />
        </Link>
        <div className="grid gap-2">
          <p className="figures text-sm font-medium text-primary">404</p>
          <h1 className="text-xl font-semibold">This page or link does not exist</h1>
          <p className="text-sm text-muted-foreground">
            If someone sent you a payment or portal link, it may have been replaced. Ask them for a new one.
          </p>
        </div>
        <Link
          href="/"
          className="inline-flex h-9 items-center justify-center justify-self-center rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground shadow-xs hover:bg-primary/90"
        >
          Go to Fatoora
        </Link>
      </div>
    </main>
  );
}
