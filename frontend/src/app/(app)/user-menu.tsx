"use client";

import { ChevronsUpDown, LogOut, Monitor, Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { useTransition } from "react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { SidebarMenu, SidebarMenuButton, SidebarMenuItem } from "@/components/ui/sidebar";
import { logout } from "./actions";

export function UserMenu({ fullName, email }: { fullName: string; email: string }) {
  const { theme, setTheme } = useTheme();
  const [pending, startTransition] = useTransition();
  const initials = fullName
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase() ?? "")
    .join("");

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton size="lg" aria-label="Account menu" className="data-[state=open]:bg-sidebar-accent">
              <span className="grid size-8 shrink-0 place-items-center rounded-full border border-sidebar-border text-xs font-semibold text-sidebar-accent-foreground">
                {initials}
              </span>
              <span className="grid min-w-0 flex-1 text-start leading-tight">
                <span className="truncate text-sm font-medium text-sidebar-accent-foreground" data-testid="current-user">
                  {fullName}
                </span>
                <span className="truncate text-xs text-sidebar-foreground/65">{email}</span>
              </span>
              <ChevronsUpDown className="ms-auto opacity-60" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent side="top" align="start" className="w-(--radix-dropdown-menu-trigger-width) min-w-56">
            <DropdownMenuLabel className="text-xs text-muted-foreground">Appearance</DropdownMenuLabel>
            <DropdownMenuRadioGroup value={theme ?? "system"} onValueChange={setTheme}>
              <DropdownMenuRadioItem value="light" className="gap-2">
                <Sun className="size-4" /> Light
              </DropdownMenuRadioItem>
              <DropdownMenuRadioItem value="dark" className="gap-2">
                <Moon className="size-4" /> Dark
              </DropdownMenuRadioItem>
              <DropdownMenuRadioItem value="system" className="gap-2">
                <Monitor className="size-4" /> System
              </DropdownMenuRadioItem>
            </DropdownMenuRadioGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem disabled={pending} onSelect={() => startTransition(() => logout())} className="gap-2">
              <LogOut className="size-4" /> Log out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
