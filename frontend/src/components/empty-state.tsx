import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

/** An empty list that teaches: what goes here, why it matters, and the first action. */
export function EmptyState({
  icon: Icon,
  title,
  children,
  action,
}: {
  icon: LucideIcon;
  title: string;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-start gap-4 rounded-lg border border-dashed bg-card px-6 py-10 sm:flex-row sm:items-center sm:gap-6 sm:px-8">
      <div className="grid size-12 shrink-0 place-items-center rounded-md bg-secondary text-primary">
        <Icon className="size-6" strokeWidth={1.75} />
      </div>
      <div className="min-w-0 flex-1">
        <h2 className="text-base font-semibold">{title}</h2>
        <p className="mt-1 max-w-[60ch] text-sm text-muted-foreground">{children}</p>
      </div>
      {action}
    </div>
  );
}
