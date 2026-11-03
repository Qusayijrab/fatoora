import { cn } from "@/lib/utils";

/**
 * Document states are ink stamps, not pills: a record is never repainted, it gets
 * stamped. Each state owns one ink. `fresh` plays the press once (use it right after
 * a state change, never on list load).
 */
const INKS: Record<string, string> = {
  DRAFT: "text-ink-draft",
  SENT: "text-ink-sent",
  ISSUED: "text-ink-sent",
  ACCEPTED: "text-ink-paid",
  PAID: "text-ink-paid",
  PARTIALLY_PAID: "text-ink-partial",
  OVERDUE: "text-ink-overdue",
  REJECTED: "text-ink-overdue",
  EXPIRED: "text-ink-expired",
  VOID: "text-ink-draft",
  CONVERTED: "text-ink-paid",
  ACTIVE: "text-ink-paid",
  PAUSED: "text-ink-expired",
  OWNER: "text-primary",
  ADMIN: "text-ink-sent",
  MEMBER: "text-ink-draft",
};

export function StatusStamp({
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
        "inline-flex items-center whitespace-nowrap rounded-[3px] border-[1.5px] border-current font-semibold uppercase leading-none tracking-[0.09em]",
        "shadow-[inset_0_0_0_1.5px_var(--card),inset_0_0_0_2.5px_currentColor]",
        size === "lg" ? "px-3.5 py-2 text-sm [--stamp-tilt:-4deg] rotate-[-4deg]" : "px-2 py-[5px] text-[10.5px]",
        status === "VOID" && "line-through decoration-2",
        INKS[status] ?? "text-ink-draft",
        fresh && "animate-stamp",
        className,
      )}
    >
      {text}
    </span>
  );
}
