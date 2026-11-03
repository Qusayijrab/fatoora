import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { StatusStamp } from "@/components/status-stamp";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getMe, getMembers } from "@/lib/api";
import { canManageMembers } from "@/lib/types";
import { AddMemberForm } from "./add-member-form";
import { MemberActions } from "./member-actions";

export const metadata = { title: "Members" };

export default function MembersPage() {
  return (
    <>
      <PageHeader
        title="Members"
        description="People who can work in this organization. Owners and admins manage the team; an organization always keeps at least one owner."
      />
      <Suspense fallback={<Skeleton className="h-64 w-full" />}>
        <Members />
      </Suspense>
    </>
  );
}

const dateFormat = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" });

async function Members() {
  const me = await getMe();
  if (!me.organization) {
    return <p className="text-muted-foreground">Select or create an organization first.</p>;
  }
  const members = await getMembers();
  const role = me.organization.role;
  const canManage = canManageMembers(role);

  return (
    <div className="grid gap-6">
      {canManage && <AddMemberForm actorRole={role} />}
      <div className="overflow-hidden rounded-lg border bg-card">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/50 hover:bg-muted/50">
              <TableHead className="ps-5">Person</TableHead>
              <TableHead className="hidden sm:table-cell">Joined</TableHead>
              <TableHead>Role</TableHead>
              {canManage && <TableHead className="pe-5 text-end">Manage</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {members.map((m) => (
              <TableRow key={m.id} data-testid="member-row">
                <TableCell className="py-3 ps-5">
                  <div className="flex items-center gap-3">
                    <span className="grid size-8 shrink-0 place-items-center rounded-full bg-secondary text-xs font-semibold text-secondary-foreground">
                      {m.fullName
                        .split(/\s+/)
                        .slice(0, 2)
                        .map((w) => w[0]?.toUpperCase())
                        .join("")}
                    </span>
                    <div className="min-w-0">
                      <p className="truncate font-medium">
                        {m.fullName}
                        {m.userId === me.id && <span className="font-normal text-muted-foreground"> (you)</span>}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">{m.email}</p>
                    </div>
                  </div>
                </TableCell>
                <TableCell className="figures hidden text-muted-foreground sm:table-cell">
                  {dateFormat.format(new Date(m.joinedAt))}
                </TableCell>
                <TableCell>
                  <StatusStamp status={m.role} />
                </TableCell>
                {canManage && (
                  <TableCell className="pe-5 text-end">
                    <MemberActions member={m} actorRole={role} isSelf={m.userId === me.id} />
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
