import { Skeleton } from "@/components/ui/skeleton";
import { getMe, getMyOrganizations } from "@/lib/api";
import { OrgSwitcher } from "./org-switcher";
import { UserMenu } from "./user-menu";

// Server components that read the session. Each sits behind its own <Suspense>,
// so the sidebar frame and nav render instantly and these stream in.

export async function SidebarOrgSlot() {
  const [me, organizations] = await Promise.all([getMe(), getMyOrganizations()]);
  return <OrgSwitcher organizations={organizations} current={me.organization ?? undefined} />;
}

export async function SidebarUserSlot() {
  const me = await getMe();
  return <UserMenu fullName={me.fullName} email={me.email} />;
}

export function SidebarSlotSkeleton() {
  return <Skeleton className="h-12 w-full rounded-md bg-sidebar-accent/60" />;
}
