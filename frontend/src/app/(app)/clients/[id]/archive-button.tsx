"use client";

import { Archive, ArchiveRestore } from "lucide-react";
import { useTransition } from "react";
import { toast } from "sonner";
import { ConfirmAction } from "@/components/confirm-action";
import { Button } from "@/components/ui/button";
import { setClientArchived } from "../actions";

export function ArchiveClientButton({ id, name, archived }: { id: string; name: string; archived: boolean }) {
  const [pending, startTransition] = useTransition();

  if (archived) {
    return (
      <Button
        variant="outline"
        disabled={pending}
        onClick={() =>
          startTransition(async () => {
            const r = await setClientArchived(id, false);
            if (r.error) toast.error(r.error);
            else toast.success(`${name} restored`);
          })
        }
      >
        <ArchiveRestore /> Restore
      </Button>
    );
  }

  return (
    <ConfirmAction
      trigger={
        <Button variant="outline">
          <Archive /> Archive
        </Button>
      }
      title={`Archive ${name}?`}
      description="Archived clients disappear from pickers and lists. Their quotes and invoices stay exactly as they are, and you can restore them at any time."
      confirmLabel="Archive"
      destructive={false}
      successMessage={`${name} archived`}
      action={() => setClientArchived(id, true)}
    />
  );
}
