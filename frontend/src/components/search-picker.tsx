"use client";

import { Check, ChevronDown, Search } from "lucide-react";
import { type KeyboardEvent, useEffect, useId, useState } from "react";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

/**
 * A select whose options come from the server: it shows the first page straight away and
 * searches as you type, so a business with thousands of clients or products can still
 * pick any of them. The trigger is a combobox and the results a listbox, like a native
 * select to assistive technology (and to the Playwright helpers).
 */
export function SearchPicker<T>({
  id,
  value,
  selectedLabel,
  placeholder,
  initial,
  search,
  getId,
  getLabel,
  getHint,
  onSelect,
  pinned,
  size = "default",
  className,
  ariaLabel,
  ariaInvalid,
  searchPlaceholder = "Type to search",
}: {
  id?: string;
  /** The selected item's id, or null. */
  value: string | null;
  /** What the trigger shows for the current value (it may not be in any loaded page). */
  selectedLabel: string | null;
  placeholder: string;
  /** The first page, shown before anything is typed. */
  initial: T[];
  search: (q: string) => Promise<T[]>;
  getId: (item: T) => string;
  getLabel: (item: T) => string;
  getHint?: (item: T) => string | null | undefined;
  onSelect: (item: T) => void;
  /** Fixed entries above the results, e.g. "Custom item". */
  pinned?: { id: string; label: string; onSelect: () => void }[];
  size?: "default" | "sm";
  className?: string;
  ariaLabel?: string;
  ariaInvalid?: boolean;
  searchPlaceholder?: string;
}) {
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  // The latest answer from the server, tagged with the query it answers. Older answers
  // that arrive late are simply ignored because their query no longer matches.
  const [found, setFound] = useState<{ q: string; items: T[] } | null>(null);
  const q = query.trim();

  useEffect(() => {
    if (!open || !q) return;
    let cancelled = false;
    const timer = setTimeout(async () => {
      const items = await search(q).catch(() => [] as T[]);
      if (!cancelled) setFound({ q, items });
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [q, open, search]);

  const loading = q !== "" && found?.q !== q;
  const results = q === "" ? initial : found?.q === q ? found.items : [];

  const entries = [
    ...(pinned ?? []).map((p) => ({ key: p.id, label: p.label, hint: null as string | null, pick: p.onSelect })),
    ...results.map((item) => ({
      key: getId(item),
      label: getLabel(item),
      hint: getHint?.(item) ?? null,
      pick: () => onSelect(item),
    })),
  ];

  function choose(index: number) {
    const entry = entries[index];
    if (!entry) return;
    entry.pick();
    setOpen(false);
    setQuery("");
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((a) => Math.min(a + 1, entries.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((a) => Math.max(a - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      choose(active);
    }
  }

  return (
    <Popover
      open={open}
      onOpenChange={(o) => {
        setOpen(o);
        setActive(0);
        if (!o) setQuery("");
      }}
    >
      <PopoverTrigger asChild>
        <button
          id={id}
          type="button"
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-label={ariaLabel}
          aria-invalid={ariaInvalid || undefined}
          data-size={size}
          className={cn(
            "flex w-full items-center justify-between gap-1.5 rounded-lg border border-input bg-transparent py-2 pe-2 ps-2.5 text-sm whitespace-nowrap transition-colors outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 data-[size=default]:h-8 data-[size=sm]:h-7 data-[size=sm]:text-xs dark:bg-input/30 dark:hover:bg-input/50",
            className,
          )}
        >
          <span className={cn("min-w-0 truncate", !selectedLabel && "text-muted-foreground")} dir="auto">
            {selectedLabel ?? placeholder}
          </span>
          <ChevronDown className="size-4 shrink-0 opacity-50" aria-hidden />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-(--radix-popover-trigger-width) min-w-64 gap-0 p-0">
        <div className="flex items-center gap-2 border-b px-2.5">
          <Search className="size-4 shrink-0 opacity-50" aria-hidden />
          <input
            autoFocus
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setActive(0);
            }}
            onKeyDown={onKeyDown}
            placeholder={searchPlaceholder}
            aria-label={searchPlaceholder}
            aria-controls={listId}
            aria-activedescendant={entries[active] ? `${listId}-${active}` : undefined}
            className="h-9 w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
            dir="auto"
          />
        </div>
        <ul id={listId} role="listbox" className="max-h-64 overflow-y-auto p-1">
          {entries.map((entry, i) => (
            <li
              key={entry.key}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={entry.key === value}
              onMouseEnter={() => setActive(i)}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => choose(i)}
              className={cn(
                "flex cursor-default items-center gap-2 rounded-md px-2 py-1.5 text-sm",
                i === active && "bg-accent text-accent-foreground",
              )}
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate" dir="auto">
                  {entry.label}
                </span>
                {entry.hint && <span className="block truncate text-xs text-muted-foreground">{entry.hint}</span>}
              </span>
              {entry.key === value && <Check className="size-4 shrink-0" aria-hidden />}
            </li>
          ))}
          {!loading && results.length === 0 && (
            <li className="px-2 py-1.5 text-sm text-muted-foreground">{q ? "No matches" : "Nothing here yet"}</li>
          )}
          {loading && <li className="px-2 py-1.5 text-sm text-muted-foreground">Searching…</li>}
        </ul>
      </PopoverContent>
    </Popover>
  );
}
