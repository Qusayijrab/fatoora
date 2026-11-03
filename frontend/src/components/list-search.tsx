import { Search } from "lucide-react";
import Form from "next/form";
import { Input } from "@/components/ui/input";

/**
 * Search + "show archived" for list pages. A plain GET form (next/form keeps it a
 * client-side navigation), so the URL is the state: shareable and back-button safe.
 */
export function ListSearch({
  action,
  q,
  includeArchived,
  placeholder,
}: {
  action: string;
  q?: string;
  includeArchived?: boolean;
  placeholder: string;
}) {
  return (
    <Form action={action} className="flex flex-wrap items-center gap-3 pb-4">
      <div className="relative w-full max-w-sm">
        <Search className="pointer-events-none absolute start-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input name="q" type="search" defaultValue={q} placeholder={placeholder} className="h-9 ps-8" aria-label="Search" />
      </div>
      <label className="flex items-center gap-2 text-sm text-muted-foreground">
        <input
          type="checkbox"
          name="archived"
          value="1"
          defaultChecked={includeArchived}
          className="size-4 rounded border-input accent-primary"
        />
        Show archived
      </label>
      <button type="submit" className="sr-only">
        Search
      </button>
    </Form>
  );
}
