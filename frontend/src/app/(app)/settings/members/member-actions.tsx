"use client";

import { useActionState, useRef } from "react";
import { selectClassName } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { type FormState, type Member, type Role, ROLES } from "@/lib/types";
import { changeMemberRole, removeMember } from "../../actions";

/** Change role / remove, for one row. Errors (e.g. "last owner") come from the backend. */
export function MemberActions({ member, actorRole }: { member: Member; actorRole: Role }) {
  const [roleState, roleAction, rolePending] = useActionState<FormState, FormData>(changeMemberRole, {});
  const [removeState, removeAction, removePending] = useActionState<FormState, FormData>(removeMember, {});
  const roleForm = useRef<HTMLFormElement>(null);

  const editable = actorRole === "OWNER" || member.role !== "OWNER";
  const roles = ROLES.filter((r) => r !== "OWNER" || actorRole === "OWNER" || member.role === "OWNER");
  const error = roleState.error ?? removeState.error;

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex items-center justify-end gap-2">
        <form ref={roleForm} action={roleAction}>
          <input type="hidden" name="membershipId" value={member.id} />
          <label className="sr-only" htmlFor={`role-${member.id}`}>
            Role for {member.email}
          </label>
          <select
            id={`role-${member.id}`}
            name="role"
            className={`${selectClassName} w-28`}
            defaultValue={member.role}
            key={member.role}
            disabled={!editable || rolePending}
            onChange={() => roleForm.current?.requestSubmit()}
          >
            {roles.map((r) => (
              <option key={r}>{r}</option>
            ))}
          </select>
        </form>
        <form action={removeAction}>
          <input type="hidden" name="membershipId" value={member.id} />
          <Button type="submit" variant="destructive" size="sm" disabled={!editable || removePending}>
            Remove
          </Button>
        </form>
      </div>
      {error && (
        <p className="text-xs text-destructive" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
