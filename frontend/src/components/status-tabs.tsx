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
    <nav aria-label="Filter by status" className="-mx-1 mb-4 flex gap-1 overflow-x-auto px-1 pb-1">
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
              "whitespace-nowrap rounded-md px-3 py-1.5 text-sm transition-colors duration-150",
              active ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-secondary hover:text-foreground",
            )}
          >
            {t.label}
          </Link>
        );
      })}
    </nav>
  );
}
