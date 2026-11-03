"use client";

import { Check, ChevronsUpDown, Plus } from "lucide-react";
import Link from "next/link";
import { useTransition } from "react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { SidebarMenu, SidebarMenuButton, SidebarMenuItem } from "@/components/ui/sidebar";
import type { Organization } from "@/lib/types";
import { switchOrganization } from "./actions";

function initials(name: string) {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase() ?? "")
    .join("");
}

/** Picking another org asks the backend for a new token scoped to that org. */
export function OrgSwitcher({ organizations, current }: { organizations: Organization[]; current?: Organization }) {
  const [pending, startTransition] = useTransition();

  function choose(id: string) {
    if (id === current?.id) return;
    const data = new FormData();
    data.set("organizationId", id);
    startTransition(() => switchOrganization(data));
  }

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              aria-label="Switch organization"
              data-testid="org-switcher"
              className="border border-sidebar-border bg-sidebar-accent/40 data-[state=open]:bg-sidebar-accent"
              disabled={pending}
            >
              <span className="grid size-8 shrink-0 place-items-center rounded-[4px] bg-saffron text-xs font-semibold text-saffron-foreground">
                {current ? initials(current.name) : "?"}
              </span>
              <span className="grid min-w-0 flex-1 text-start leading-tight">
                <span className="truncate text-sm font-medium text-sidebar-accent-foreground">
                  {current?.name ?? "No organization"}
                </span>
                <span className="truncate text-xs text-sidebar-foreground/65">
                  {current ? `${current.defaultCurrency} · ${current.role.toLowerCase()}` : "Create one to start"}
                </span>
              </span>
              <ChevronsUpDown className="ms-auto opacity-60" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent className="w-(--radix-dropdown-menu-trigger-width) min-w-60" align="start" sideOffset={6}>
            <DropdownMenuLabel className="text-xs text-muted-foreground">Your organizations</DropdownMenuLabel>
            {organizations.map((org) => (
              <DropdownMenuItem key={org.id} onSelect={() => choose(org.id)} className="gap-2.5">
                <span className="grid size-6 place-items-center rounded-[3px] bg-secondary text-[10px] font-semibold">
                  {initials(org.name)}
                </span>
                <span className="flex-1 truncate">{org.name}</span>
                {org.id === current?.id && <Check className="size-4 text-primary" />}
              </DropdownMenuItem>
            ))}
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild className="gap-2.5">
              <Link href="/organizations/new">
                <Plus className="size-4" />
                New organization
              </Link>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
