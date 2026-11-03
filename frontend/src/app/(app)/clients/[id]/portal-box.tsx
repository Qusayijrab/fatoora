"use client";

import { Copy, ExternalLink, RotateCcw, Share2 } from "lucide-react";
import { useState, useTransition } from "react";
import { toast } from "sonner";
import { ConfirmAction } from "@/components/confirm-action";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { getPortalLink, revokePortalLink } from "../actions";

/** The client's private portal link: share it, copy it, or replace it if it leaked. */
export function PortalBox({ clientId }: { clientId: string }) {
  const [url, setUrl] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  function load() {
    startTransition(async () => {
      const r = await getPortalLink(clientId);
      if (r.url) setUrl(r.url);
      else toast.error(r.error ?? "Could not create the link");
    });
  }

  return (
    <section className="flex flex-wrap items-center gap-3 rounded-lg border bg-card p-5">
      <div className="min-w-56 flex-1">
        <h2 className="text-sm font-semibold">Client portal</h2>
        <p className="mt-0.5 text-sm text-muted-foreground">
          A private page where this client sees their invoices and quotes, downloads PDFs, pays and accepts quotes.
        </p>
      </div>
      {url ? (
        <div className="flex w-full gap-1.5 sm:w-auto sm:min-w-96">
          <Input readOnly value={url} className="figures h-8 text-xs" aria-label="Portal link" onFocus={(e) => e.target.select()} />
          <Button
            size="icon-sm"
            variant="outline"
            aria-label="Copy portal link"
            onClick={async () => {
              await navigator.clipboard.writeText(url);
              toast.success("Portal link copied");
            }}
          >
            <Copy />
          </Button>
          <Button size="icon-sm" variant="outline" asChild aria-label="Open portal">
            <a href={url} target="_blank" rel="noopener">
              <ExternalLink />
            </a>
          </Button>
          <ConfirmAction
            trigger={
              <Button size="icon-sm" variant="outline" aria-label="Replace portal link">
                <RotateCcw />
              </Button>
            }
            title="Replace the portal link?"
            description="The current link stops working immediately. Use this if it was sent to the wrong person."
            confirmLabel="Replace"
            action={async () => {
              const r = await revokePortalLink(clientId);
              if (!r.error) {
                const fresh = await getPortalLink(clientId);
                setUrl(fresh.url ?? null);
              }
              return r;
            }}
            successMessage="Old link revoked; here is a new one"
          />
        </div>
      ) : (
        <Button variant="outline" onClick={load} disabled={pending}>
          <Share2 /> {pending ? "Creating…" : "Get portal link"}
        </Button>
      )}
    </section>
  );
}
