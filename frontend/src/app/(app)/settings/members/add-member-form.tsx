"use client";

import { UserPlus } from "lucide-react";
import { useActionState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useActionFeedback } from "@/hooks/use-action-feedback";
import { type FormState, type Role, ROLES } from "@/lib/types";
import { addMember } from "../../actions";

export function AddMemberForm({ actorRole }: { actorRole: Role }) {
  const [state, action, pending] = useActionState<FormState, FormData>(addMember, {});
  useActionFeedback(state, "Member added");
  // Only owners can make owners (the backend enforces this too).
  const roles = ROLES.filter((r) => r !== "OWNER" || actorRole === "OWNER");

  return (
    <form action={action} className="rounded-xl border bg-card shadow-xs p-4 sm:p-5">
      <div className="grid gap-3 sm:grid-cols-[1fr_9rem_auto] sm:items-end">
        <div className="grid gap-1.5">
          <Label htmlFor="member-email">Add a member by email</Label>
          <Input
            id="member-email"
            name="email"
            type="email"
            required
            placeholder="colleague@company.com"
            // React resets the form after each submit; on error, put the email back.
            defaultValue={state.values?.email}
            aria-invalid={!!state.fieldErrors?.email || undefined}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="member-role">Role</Label>
          <Select name="role" defaultValue="MEMBER">
            <SelectTrigger id="member-role" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {roles.map((r) => (
                <SelectItem key={r} value={r}>
                  {r.charAt(0) + r.slice(1).toLowerCase()}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button type="submit" disabled={pending}>
          <UserPlus />
          {pending ? "Adding…" : "Add"}
        </Button>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">
        {state.fieldErrors?.email ?? "They need a Fatoora account first: ask them to sign up, then add their email here."}
      </p>
    </form>
  );
}
