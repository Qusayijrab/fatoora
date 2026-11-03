import Link from "next/link";
import { Suspense } from "react";
import { AccountBar } from "./account-bar";

/**
 * The static parts (logo, nav) render instantly. Anything that reads the session
 * cookie sits behind <Suspense> and streams in: with Cache Components, reading
 * cookies outside a Suspense boundary is a build error.
 */
export default function AppLayout({ children }: LayoutProps<"/">) {
  return (
    <div className="flex flex-1 flex-col">
      <header className="border-b bg-background">
        <div className="mx-auto flex h-14 max-w-5xl items-center gap-6 px-4">
          <Link href="/dashboard" className="font-semibold tracking-tight">
            Fatoora
          </Link>
          <nav className="flex gap-4 text-sm text-muted-foreground">
            <Link href="/dashboard" className="hover:text-foreground">
              Dashboard
            </Link>
            <Link href="/settings/members" className="hover:text-foreground">
              Members
            </Link>
          </nav>
          <div className="ms-auto">
            <Suspense fallback={<div className="h-8 w-48 animate-pulse rounded-lg bg-muted" />}>
              <AccountBar />
            </Suspense>
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">{children}</main>
    </div>
  );
}
