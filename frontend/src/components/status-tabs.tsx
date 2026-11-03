import Link from "next/link";
import { cn } from "@/lib/utils";

/** Status filter as links: the URL holds the filter, so it is shareable and back-button safe. */
export function StatusTabs({
  base,
  current,
  tabs,
  q,
}: {
  base: string;
  current?: string;
  tabs: { value?: string; label: string }[];
  q?: string;
}) {
  return (
    <nav aria-label="Filter by status" className="mb-4 flex max-w-full gap-0.5 overflow-x-auto rounded-lg bg-muted p-1 ring-1 ring-border/60 [scrollbar-width:none]">
      {tabs.map((t) => {
        const active = (t.value ?? "") === (current ?? "");
        const params = new URLSearchParams();
        if (t.value) params.set("status", t.value);
        if (q) params.set("q", q);
        const href = params.toString() ? `${base}?${params}` : base;
        return (
          <Link
            key={t.label}
            href={href}
            aria-current={active ? "page" : undefined}
            className={cn(
              "whitespace-nowrap rounded-md px-3 py-1 text-sm font-medium transition-colors duration-150",
              active ? "bg-card text-foreground shadow-xs ring-1 ring-border/70" : "text-muted-foreground hover:text-foreground",
            )}
          >
            {t.label}
          </Link>
        );
      })}
    </nav>
  );
}
