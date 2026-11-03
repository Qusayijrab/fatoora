"use client";

import { Archive, ArchiveRestore } from "lucide-react";
import { useTransition } from "react";
import { toast } from "sonner";
import { ConfirmAction } from "@/components/confirm-action";
import { Button } from "@/components/ui/button";
import { setProductArchived } from "../actions";

export function ArchiveProductButton({ id, name, archived }: { id: string; name: string; archived: boolean }) {
  const [pending, startTransition] = useTransition();

  if (archived) {
    return (
      <Button
        variant="outline"
        disabled={pending}
        onClick={() =>
          startTransition(async () => {
            const r = await setProductArchived(id, false);
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
      description="It disappears from the product picker. Existing quotes and invoices keep their lines unchanged."
      confirmLabel="Archive"
      destructive={false}
      successMessage={`${name} archived`}
      action={() => setProductArchived(id, true)}
    />
  );
}
