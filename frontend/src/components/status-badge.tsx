import { cn } from "@/lib/utils";

/**
 * A document state as a tinted pill: a dot and the state name on a light wash of the
 * state's color. The text stays the raw status ("PARTIALLY PAID") for screen readers
 * and tests; CSS shows it in sentence case. `fresh` pops it in once (use it right
 * after a state change, never on list load).
 */
const INKS: Record<string, string> = {
  DRAFT: "text-ink-draft bg-ink-draft/10",
  SENT: "text-ink-sent bg-ink-sent/10",
  ISSUED: "text-ink-sent bg-ink-sent/10",
  ACCEPTED: "text-ink-paid bg-ink-paid/12",
  PAID: "text-ink-paid bg-ink-paid/12",
  PARTIALLY_PAID: "text-ink-partial bg-ink-partial/12",
  OVERDUE: "text-ink-overdue bg-ink-overdue/10",
  REJECTED: "text-ink-overdue bg-ink-overdue/10",
  EXPIRED: "text-ink-expired bg-ink-expired/12",
  VOID: "text-ink-draft bg-ink-draft/10",
  CONVERTED: "text-ink-paid bg-ink-paid/12",
  ACTIVE: "text-ink-paid bg-ink-paid/12",
  PAUSED: "text-ink-expired bg-ink-expired/12",
  OWNER: "text-primary bg-primary/10",
  ADMIN: "text-ink-partial bg-ink-partial/12",
  MEMBER: "text-ink-draft bg-ink-draft/10",
};

export function StatusBadge({
  status,
  label,
  fresh = false,
  size = "sm",
  className,
}: {
  status: string;
  label?: string;
  fresh?: boolean;
  size?: "sm" | "lg";
  className?: string;
}) {
  const text = label ?? status.replace(/_/g, " ");
  return (
    <span
      data-status={status}
      className={cn(
        "inline-flex items-center gap-1.5 whitespace-nowrap rounded-full font-medium leading-none",
        size === "lg" ? "px-3 py-1.5 text-sm" : "px-2.5 py-1 text-xs",
        INKS[status] ?? INKS.DRAFT,
        fresh && "animate-pop",
        className,
      )}
    >
      <span aria-hidden className={cn("shrink-0 rounded-full bg-current", size === "lg" ? "size-2" : "size-1.5")} />
      <span className={cn("inline-block lowercase first-letter:uppercase", status === "VOID" && !label && "line-through")}>{text}</span>
    </span>
  );
}
