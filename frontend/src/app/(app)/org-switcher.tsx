"use client";

import Link from "next/link";
import { useRef } from "react";
import { selectClassName } from "@/components/form-field";
import type { Organization } from "@/lib/types";
import { switchOrganization } from "./actions";

/** Picking another org asks the backend for a new token scoped to that org. */
export function OrgSwitcher({ organizations, currentId }: { organizations: Organization[]; currentId?: string }) {
  const formRef = useRef<HTMLFormElement>(null);

  return (
    <div className="flex items-center gap-2">
      <form ref={formRef} action={switchOrganization}>
        <label htmlFor="org-switcher" className="sr-only">
          Organization
        </label>
        <select
          id="org-switcher"
          name="organizationId"
          className={`${selectClassName} w-48`}
          defaultValue={currentId ?? ""}
          onChange={() => formRef.current?.requestSubmit()}
        >
          {!currentId && <option value="">No organization</option>}
          {organizations.map((org) => (
            <option key={org.id} value={org.id}>
              {org.name}
            </option>
          ))}
        </select>
      </form>
      <Link href="/organizations/new" className="text-sm text-muted-foreground hover:text-foreground" title="New organization">
        + New
      </Link>
    </div>
  );
}
