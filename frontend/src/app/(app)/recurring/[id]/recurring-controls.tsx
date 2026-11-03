"use client";

import { CirclePause, CirclePlay, CircleStop, Zap } from "lucide-react";
import { useTransition } from "react";
import { toast } from "sonner";
import { ConfirmAction } from "@/components/confirm-action";
import { Button } from "@/components/ui/button";
import type { Recurring } from "@/lib/types";
import { generateNow, recurringAction } from "../actions";

export function RecurringControls({ id, status, name }: { id: string; status: Recurring["status"]; name: string }) {
  const [pending, startTransition] = useTransition();

  function run(action: "pause" | "resume", done: string) {
    startTransition(async () => {
      const r = await recurringAction(id, action);
      if (r.error) toast.error(r.error);
      else toast.success(done);
    });
  }

  if (status === "ENDED") return null;

  return (
    <div className="mt-4 grid gap-2">
      {status === "ACTIVE" && (
        <ConfirmAction
          trigger={
            <Button>
              <Zap /> Generate next invoice now
            </Button>
          }
          title="Generate the next invoice now?"
          description="The invoice for the next period is created today instead of on its date. The schedule then moves on to the following period."
          confirmLabel="Generate"
          destructive={false}
          action={() => generateNow(id)}
        />
      )}
      {status === "ACTIVE" ? (
        <Button variant="outline" disabled={pending} onClick={() => run("pause", `${name} paused`)}>
          <CirclePause /> Pause
        </Button>
      ) : (
        <Button variant="outline" disabled={pending} onClick={() => run("resume", `${name} resumed`)}>
          <CirclePlay /> Resume
        </Button>
      )}
      <ConfirmAction
        trigger={
          <Button variant="ghost" className="justify-start text-destructive hover:text-destructive">
            <CircleStop /> End schedule
          </Button>
        }
        title={`End "${name}"?`}
        description="No more invoices will be generated. Invoices it already created are not affected."
        confirmLabel="End"
        successMessage="Schedule ended"
        action={() => recurringAction(id, "end")}
      />
    </div>
  );
}
