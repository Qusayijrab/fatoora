"use client";

import { Trash2 } from "lucide-react";
import { useTransition } from "react";
import { toast } from "sonner";
import { ConfirmAction } from "@/components/confirm-action";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { type Member, type Role, ROLES } from "@/lib/types";
import { removeMember, setMemberRole } from "../../actions";

/** Role picker + remove, for one row. Rules like "keep one owner" come back from the backend. */
export function MemberActions({ member, actorRole, isSelf }: { member: Member; actorRole: Role; isSelf: boolean }) {
  const [pending, startTransition] = useTransition();
  const editable = actorRole === "OWNER" || member.role !== "OWNER";
  const roles = ROLES.filter((r) => r !== "OWNER" || actorRole === "OWNER" || member.role === "OWNER");

  function changeRole(role: string) {
    startTransition(async () => {
      const result = await setMemberRole(member.id, role as Role);
      if (result.error) toast.error(result.error);
      else toast.success(`${member.fullName} is now ${role.toLowerCase()}`);
    });
  }

  return (
    <div className="flex items-center justify-end gap-2">
      <Select value={member.role} onValueChange={changeRole} disabled={!editable || pending}>
        <SelectTrigger size="sm" className="w-28" aria-label={`Role for ${member.email}`}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent align="end">
          {roles.map((r) => (
            <SelectItem key={r} value={r}>
              {r.charAt(0) + r.slice(1).toLowerCase()}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <ConfirmAction
        trigger={
          <Button variant="ghost" size="icon-sm" disabled={!editable} aria-label={`Remove ${member.email}`}>
            <Trash2 />
          </Button>
        }
        title={isSelf ? "Leave this organization?" : `Remove ${member.fullName}?`}
        description={
          isSelf
            ? "You will lose access to its clients and invoices straight away."
            : `${member.fullName} loses access to this organization immediately. Their own account and other organizations are not affected.`
        }
        confirmLabel={isSelf ? "Leave" : "Remove"}
        successMessage={`${member.fullName} removed`}
        action={() => removeMember(member.id)}
      />
    </div>
  );
}
