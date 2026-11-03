"use client";

import { useActionState } from "react";
import { FormError } from "@/components/form-error";
import { OrganizationFields } from "@/components/organization-fields";
import { Button } from "@/components/ui/button";
import type { FormState } from "@/lib/types";
import { createOrganization } from "../../actions";

export function NewOrganizationForm() {
  const [state, action, pending] = useActionState<FormState, FormData>(createOrganization, {});

  return (
    <form action={action} className="grid gap-4">
      <FormError message={state.error} />
      <OrganizationFields values={state.values} errors={state.fieldErrors} nameField="name" />
      <Button type="submit" disabled={pending}>
        {pending ? "Creating…" : "Create organization"}
      </Button>
    </form>
  );
}
