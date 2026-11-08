"use client";

import {
  ChartColumn,
  Building2,
  FileText,
  LayoutDashboard,
  Package,
  Percent,
  ReceiptText,
  Repeat,
  ScrollText,
  UsersRound,
  Contact,
  Coins,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Suspense, type ReactNode } from "react";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  useSidebar,
} from "@/components/ui/sidebar";

type NavItem = { href: string; label: string; icon: LucideIcon };

const WORK: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/invoices", label: "Invoices", icon: ReceiptText },
  { href: "/quotes", label: "Quotes", icon: FileText },
  { href: "/recurring", label: "Recurring", icon: Repeat },
  { href: "/clients", label: "Clients", icon: Contact },
  { href: "/products", label: "Products", icon: Package },
  { href: "/reports", label: "Reports", icon: ChartColumn },
];

const SETTINGS: NavItem[] = [
  { href: "/settings/organization", label: "Organization", icon: Building2 },
  { href: "/settings/members", label: "Members", icon: UsersRound },
  { href: "/settings/tax-rates", label: "Tax rates", icon: Percent },
  { href: "/settings/exchange-rates", label: "Exchange rates", icon: Coins },
  { href: "/settings/audit", label: "Audit log", icon: ScrollText },
];

export function AppSidebar({ header, footer }: { header: ReactNode; footer: ReactNode }) {
  return (
    <Sidebar collapsible="icon">
      <SidebarHeader className="gap-3 px-3 pt-4">{header}</SidebarHeader>
      <SidebarContent className="px-1">
        <NavGroup label="Work" items={WORK} />
        <NavGroup label="Settings" items={SETTINGS} />
      </SidebarContent>
      <SidebarFooter className="px-3 pb-4">{footer}</SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}

/**
 * On dynamic routes the pathname is request data, so the "active" highlight streams in
 * behind <Suspense>; the fallback renders the same links without a highlight.
 */
function NavGroup({ label, items }: { label: string; items: NavItem[] }) {
  return (
    <Suspense fallback={<NavGroupView label={label} items={items} pathname={null} />}>
      <ActiveNavGroup label={label} items={items} />
    </Suspense>
  );
}

function ActiveNavGroup({ label, items }: { label: string; items: NavItem[] }) {
  return <NavGroupView label={label} items={items} pathname={usePathname()} />;
}

function NavGroupView({ label, items, pathname }: { label: string; items: NavItem[]; pathname: string | null }) {
  const { setOpenMobile } = useSidebar();

  return (
    <SidebarGroup>
      <SidebarGroupLabel className="text-xs font-medium text-sidebar-foreground/70">
        {label}
      </SidebarGroupLabel>
      <SidebarGroupContent>
        <SidebarMenu className="gap-0.5">
          {items.map((item) => {
            const active = !!pathname && (pathname === item.href || pathname.startsWith(`${item.href}/`));
            return (
              <SidebarMenuItem key={item.href}>
                <SidebarMenuButton
                  asChild
                  isActive={active}
                  tooltip={item.label}
                  className="h-9 rounded-lg font-medium text-sidebar-foreground hover:bg-sidebar-accent/70 data-active:bg-sidebar-accent data-active:text-sidebar-primary data-active:shadow-xs data-active:ring-1 data-active:ring-sidebar-border dark:data-active:text-sidebar-accent-foreground"
                >
                  <Link href={item.href} onClick={() => setOpenMobile(false)}>
                    <item.icon strokeWidth={1.75} />
                    <span>{item.label}</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            );
          })}
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  );
}
