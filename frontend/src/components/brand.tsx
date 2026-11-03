import { cn } from "@/lib/utils";

/**
 * The mark: a square seal holding a square-Kufic "ف" (fa, the first letter of
 * فاتورة, "invoice"). The head sits on the right and the tail runs left, as the
 * letter is written. Drawn on a 32-unit grid so it stays crisp at 16px.
 * `inverted` is for the deep indigo brand field: a white tile with an indigo letter.
 */
export function LogoMark({ className, inverted = false }: { className?: string; inverted?: boolean }) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" className={cn("size-8 shrink-0", className)}>
      <rect width="32" height="32" rx="8" className={inverted ? "fill-brand-foreground" : "fill-primary"} />
      <g className={inverted ? "fill-brand" : "fill-primary-foreground"}>
        {/* dot above the head */}
        <rect x="18.5" y="5" width="4" height="4" />
        {/* the head: a square loop */}
        <path d="M15 11h11v11H15zM18.5 14.5v4h4v-4z" fillRule="evenodd" />
        {/* the tail, running left along the baseline */}
        <rect x="6" y="18.5" width="12" height="3.5" />
        <rect x="6" y="14.5" width="3.5" height="7.5" />
      </g>
    </svg>
  );
}

/** Bilingual lockup: Latin and Arabic names share one baseline, split by a hairline. */
export function Wordmark({
  className,
  size = "md",
  inverted = false,
}: {
  className?: string;
  size?: "md" | "lg";
  inverted?: boolean;
}) {
  return (
    <span className={cn("inline-flex items-center gap-2.5", className)}>
      <LogoMark inverted={inverted} className={size === "lg" ? "size-10" : "size-7"} />
      <span className={cn("font-semibold tracking-[-0.02em]", size === "lg" ? "text-2xl" : "text-[1.05rem]")}>Fatoora</span>
      <span aria-hidden="true" className="h-4 w-px bg-current opacity-30" />
      <span lang="ar" dir="rtl" className={cn("font-medium opacity-80", size === "lg" ? "text-xl" : "text-[0.95rem]")}>
        فاتورة
      </span>
    </span>
  );
}
