import { Button } from "@/components/ui/button";
import { getMe, getMyOrganizations } from "@/lib/api";
import { logout } from "./actions";
import { OrgSwitcher } from "./org-switcher";

export async function AccountBar() {
  const [me, organizations] = await Promise.all([getMe(), getMyOrganizations()]);

  return (
    <div className="flex items-center gap-3">
      <OrgSwitcher organizations={organizations} currentId={me.organization?.id} />
      <span className="hidden text-sm text-muted-foreground sm:inline" data-testid="current-user">
        {me.fullName}
      </span>
      <form action={logout}>
        <Button type="submit" variant="ghost" size="sm">
          Log out
        </Button>
      </form>
    </div>
  );
}
