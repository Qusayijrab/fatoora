"use client";

import { useActionState } from "react";
import { FormError } from "@/components/form-error";
import { FormField, selectClassName } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { type FormState, type Role, ROLES } from "@/lib/types";
import { addMember } from "../../actions";

export function AddMemberForm({ actorRole }: { actorRole: Role }) {
  const [state, action, pending] = useActionState<FormState, FormData>(addMember, {});
  // Only owners can make owners (the backend enforces this too).
  const roles = ROLES.filter((r) => r !== "OWNER" || actorRole === "OWNER");

  return (
    <form action={action} className="grid gap-4 sm:grid-cols-[1fr_10rem_auto] sm:items-end">
      <div className="sm:col-span-3">
        <FormError message={state.error} />
      </div>
      <FormField id="member-email" label="Email" error={state.fieldErrors?.email}>
        <Input id="member-email" name="email" type="email" required key={state.ok ? "reset" : "keep"} defaultValue={state.ok ? "" : state.values?.email} />
      </FormField>
      <FormField id="member-role" label="Role">
        <select id="member-role" name="role" className={selectClassName} defaultValue="MEMBER">
          {roles.map((r) => (
            <option key={r}>{r}</option>
          ))}
        </select>
      </FormField>
      <Button type="submit" disabled={pending}>
        {pending ? "Adding…" : "Add"}
      </Button>
    </form>
  );
}
