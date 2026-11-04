import { SearchX } from "lucide-react";
import Link from "next/link";
import { EmptyState } from "@/components/empty-state";
import { Button } from "@/components/ui/button";

export const metadata = { title: "Not found" };

/** A record that does not exist, or belongs to another organization. */
export default function NotFound() {
  return (
    <div className="mt-6 grid gap-6">
      <h1 className="text-[1.6rem] font-semibold tracking-tight">Not found</h1>
      <EmptyState
        icon={SearchX}
        title="We could not find that"
        action={
          <Button asChild>
            <Link href="/dashboard">Back to the dashboard</Link>
          </Button>
        }
      >
        It may have been deleted, or it belongs to another organization. If you switched organizations, switch back and try again.
      </EmptyState>
    </div>
  );
}
