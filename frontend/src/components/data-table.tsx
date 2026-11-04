"use client";

import {
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type RowData,
  type RowSelectionState,
  type SortingState,
  type VisibilityState,
} from "@tanstack/react-table";
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, ChevronsUpDown, Columns3 } from "lucide-react";
import { useMemo, useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

/*
 * Sortable, selectable table on TanStack Table, adapted from the Layro DataTable
 * (21st.dev, @uvain) onto our own Button, Checkbox and DropdownMenu.
 *
 * Search and status filters stay on the server (the URL), so they cover every
 * row, not just the loaded page; this table sorts, selects and pages what it is
 * given. Below `sm` the table gives way to `mobileCard`, one card per row, so
 * nothing important scrolls off a phone screen.
 */

declare module "@tanstack/react-table" {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface ColumnMeta<TData extends RowData, TValue> {
    /** "end" for numbers so the digits line up. */
    align?: "start" | "end";
    /** Name in the columns menu when the header is not a plain string. */
    label?: string;
    /** Extra classes for this column's cells (e.g. hide on tablet). */
    className?: string;
  }
}

export type { ColumnDef };

export function DataTable<T>({
  data,
  columns: userColumns,
  label,
  getRowId,
  selectable = false,
  bulkActions,
  columnToggle = false,
  pageSize = 20,
  mobileCard,
  empty,
  toolbar,
}: {
  data: T[];
  columns: ColumnDef<T, unknown>[];
  /** What the rows are, for the table's accessible name: "Invoices". */
  label: string;
  getRowId: (row: T) => string;
  selectable?: boolean;
  /** Shown above the table while rows are selected; `clear` empties the selection. */
  bulkActions?: (rows: T[], clear: () => void) => ReactNode;
  columnToggle?: boolean;
  /** Rows per page; 0 turns paging off. */
  pageSize?: number;
  /** How one row looks on a phone. Without it the table scrolls sideways. */
  mobileCard?: (row: T) => ReactNode;
  empty?: ReactNode;
  /** Left side of the toolbar (status tabs, search), kept on one line with the column menu. */
  toolbar?: ReactNode;
}) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
  const [visibility, setVisibility] = useState<VisibilityState>({});

  const columns = useMemo<ColumnDef<T, unknown>[]>(() => {
    if (!selectable) return userColumns;
    const select: ColumnDef<T, unknown> = {
      id: "select",
      enableSorting: false,
      enableHiding: false,
      header: ({ table }) => (
        <Checkbox
          aria-label="Select all rows on this page"
          checked={table.getIsAllPageRowsSelected() ? true : table.getIsSomePageRowsSelected() ? "indeterminate" : false}
          onCheckedChange={(v) => table.toggleAllPageRowsSelected(v === true)}
        />
      ),
      cell: ({ row }) => (
        <Checkbox aria-label="Select row" checked={row.getIsSelected()} onCheckedChange={(v) => row.toggleSelected(v === true)} />
      ),
    };
    return [select, ...userColumns];
  }, [selectable, userColumns]);

  // TanStack returns fresh functions each render; the React Compiler skips this component, which is fine.
  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data,
    columns,
    state: { sorting, rowSelection, columnVisibility: visibility },
    onSortingChange: setSorting,
    onRowSelectionChange: setRowSelection,
    onColumnVisibilityChange: setVisibility,
    getRowId: (row) => getRowId(row),
    enableRowSelection: selectable,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    ...(pageSize > 0
      ? { getPaginationRowModel: getPaginationRowModel(), initialState: { pagination: { pageIndex: 0, pageSize } } }
      : {}),
  });

  const rows = table.getRowModel().rows;
  const selected = table.getSelectedRowModel().rows.map((r) => r.original);
  const clear = () => table.resetRowSelection();
  const pageCount = table.getPageCount();

  return (
    <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-3">
      {(toolbar || columnToggle) && (
        <div className="flex flex-wrap items-center gap-3">
          {toolbar}
          {columnToggle && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="lg" className="ms-auto hidden sm:inline-flex">
                  <Columns3 /> Columns
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-44">
                <DropdownMenuLabel>Show columns</DropdownMenuLabel>
                {table
                  .getAllLeafColumns()
                  .filter((c) => c.getCanHide())
                  .map((c) => (
                    <DropdownMenuCheckboxItem
                      key={c.id}
                      checked={c.getIsVisible()}
                      onCheckedChange={(v) => c.toggleVisibility(v === true)}
                      onSelect={(e) => e.preventDefault()}
                    >
                      {c.columnDef.meta?.label ?? (typeof c.columnDef.header === "string" ? c.columnDef.header : c.id)}
                    </DropdownMenuCheckboxItem>
                  ))}
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      )}

      {selectable && selected.length > 0 && (
        <div
          aria-live="polite"
          className="hidden animate-pop items-center gap-2 rounded-xl border border-primary/25 bg-primary/5 px-3 py-2 sm:flex"
        >
          <span className="figures text-sm font-medium">{selected.length} selected</span>
          <Button variant="ghost" size="sm" onClick={clear} className="text-muted-foreground">
            Clear
          </Button>
          <div className="ms-auto flex flex-wrap items-center gap-2">{bulkActions?.(selected, clear)}</div>
        </div>
      )}

      {rows.length === 0 ? (
        <div className="rounded-xl border bg-card shadow-xs">{empty}</div>
      ) : (
        <>
          <div className={cn("relative overflow-x-auto rounded-xl border bg-card shadow-xs", mobileCard && "hidden sm:block")}>
            <table aria-label={label} className="w-full border-collapse text-sm">
              <thead>
                {table.getHeaderGroups().map((group) => (
                  <tr key={group.id} className="border-b bg-muted/50">
                    {group.headers.map((header) => {
                      const meta = header.column.columnDef.meta;
                      const sorted = header.column.getIsSorted();
                      const canSort = header.column.getCanSort();
                      return (
                        <th
                          key={header.id}
                          scope="col"
                          aria-sort={sorted === "asc" ? "ascending" : sorted === "desc" ? "descending" : canSort ? "none" : undefined}
                          className={cn(
                            "h-10 px-3 text-xs font-medium whitespace-nowrap text-muted-foreground first:ps-5 last:pe-5",
                            meta?.align === "end" ? "text-end" : "text-start",
                            header.column.id === "select" && "w-10",
                            meta?.className,
                          )}
                        >
                          {header.isPlaceholder ? null : canSort ? (
                            <button
                              type="button"
                              onClick={header.column.getToggleSortingHandler()}
                              className={cn(
                                "-mx-1.5 inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 outline-none hover:bg-accent hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring",
                                sorted && "text-foreground",
                                meta?.align === "end" && "flex-row-reverse",
                              )}
                            >
                              {flexRender(header.column.columnDef.header, header.getContext())}
                              {sorted === "asc" ? (
                                <ArrowUp className="size-3.5" aria-hidden />
                              ) : sorted === "desc" ? (
                                <ArrowDown className="size-3.5" aria-hidden />
                              ) : (
                                <ChevronsUpDown className="size-3.5 opacity-40" aria-hidden />
                              )}
                            </button>
                          ) : (
                            flexRender(header.column.columnDef.header, header.getContext())
                          )}
                        </th>
                      );
                    })}
                  </tr>
                ))}
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr
                    key={row.id}
                    data-state={row.getIsSelected() ? "selected" : undefined}
                    className="border-b transition-colors last:border-b-0 hover:bg-muted/40 data-[state=selected]:bg-primary/5"
                  >
                    {row.getVisibleCells().map((cell) => {
                      const meta = cell.column.columnDef.meta;
                      return (
                        <td
                          key={cell.id}
                          className={cn(
                            "px-3 py-3 align-middle first:ps-5 last:pe-5",
                            meta?.align === "end" && "text-end",
                            meta?.className,
                          )}
                        >
                          {flexRender(cell.column.columnDef.cell, cell.getContext())}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {mobileCard && (
            <ul aria-label={label} className="grid gap-2 sm:hidden">
              {rows.map((row) => (
                <li key={row.id}>{mobileCard(row.original)}</li>
              ))}
            </ul>
          )}
        </>
      )}

      {pageSize > 0 && pageCount > 1 && (
        <nav aria-label={`${label} pages`} className="flex items-center justify-between gap-3">
          <p className="figures text-xs text-muted-foreground" aria-live="polite">
            Page <span className="text-foreground">{table.getState().pagination.pageIndex + 1}</span> of {pageCount}
          </p>
          <div className="flex items-center gap-1">
            <Button variant="outline" size="sm" onClick={() => table.previousPage()} disabled={!table.getCanPreviousPage()}>
              <ChevronLeft className="rtl:rotate-180" /> Previous
            </Button>
            <Button variant="outline" size="sm" onClick={() => table.nextPage()} disabled={!table.getCanNextPage()}>
              Next <ChevronRight className="rtl:rotate-180" />
            </Button>
          </div>
        </nav>
      )}
    </div>
  );
}
