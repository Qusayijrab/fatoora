import { Suspense } from "react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getMe, getMembers } from "@/lib/api";
import { canManageMembers } from "@/lib/types";
import { AddMemberForm } from "./add-member-form";
import { MemberActions } from "./member-actions";

export default function MembersPage() {
  return (
    <div className="grid gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Members</h1>
      <Suspense fallback={<p className="text-muted-foreground">Loading members…</p>}>
        <Members />
      </Suspense>
    </div>
  );
}

async function Members() {
  const me = await getMe();
  if (!me.organization) {
    return <p className="text-muted-foreground">Select or create an organization first.</p>;
  }
  const members = await getMembers();
  const canManage = canManageMembers(me.organization.role);

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>{me.organization.name}</CardTitle>
          <CardDescription>
            {members.length} {members.length === 1 ? "member" : "members"}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Role</TableHead>
                {canManage && <TableHead className="text-end">Actions</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {members.map((m) => (
                <TableRow key={m.id} data-testid="member-row">
                  <TableCell>
                    {m.fullName}
                    {m.userId === me.id && <span className="text-muted-foreground"> (you)</span>}
                  </TableCell>
                  <TableCell>{m.email}</TableCell>
                  <TableCell>
                    <Badge variant={m.role === "OWNER" ? "default" : "secondary"}>{m.role}</Badge>
                  </TableCell>
                  {canManage && (
                    <TableCell className="text-end">
                      <MemberActions member={m} actorRole={me.organization!.role} />
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {canManage && (
        <Card>
          <CardHeader>
            <CardTitle>Add a member</CardTitle>
            <CardDescription>They need a Fatoora account first. Email invitations come later.</CardDescription>
          </CardHeader>
          <CardContent>
            <AddMemberForm actorRole={me.organization.role} />
          </CardContent>
        </Card>
      )}
    </>
  );
}
