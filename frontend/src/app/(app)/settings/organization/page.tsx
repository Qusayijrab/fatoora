import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { getCurrentOrganization } from "@/lib/api";
import { canManageMembers } from "@/lib/types";
import { OrganizationForm } from "./organization-form";

export const metadata = { title: "Organization" };

export default function OrganizationSettingsPage() {
  return (
    <div className="max-w-4xl">
      <PageHeader
        title="Organization"
        description="Your seller details. They are printed on every invoice and frozen onto it at the moment it is issued."
      />
      <Suspense fallback={<Skeleton className="h-96 w-full" />}>
        <Settings />
      </Suspense>
    </div>
  );
}

async function Settings() {
  const org = await getCurrentOrganization();
  return (
    <div className="rounded-xl border bg-card shadow-xs p-5 sm:p-7">
      <OrganizationForm org={org} canEdit={canManageMembers(org.role)} />
    </div>
  );
}
