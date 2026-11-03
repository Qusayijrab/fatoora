"use client";

import { FileDown, Mail } from "lucide-react";
import { useId, useState, useTransition } from "react";
import { toast } from "sonner";
import { emailDocument } from "@/app/(app)/email-actions";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

/** "Download PDF" and "Email to client" for an issued invoice or a sent quote. */
export function DocumentDelivery({
  kind,
  id,
  number,
  clientEmail,
  canEmail,
}: {
  kind: "invoices" | "quotes";
  id: string;
  number: string | null;
  clientEmail: string | null;
  canEmail: boolean;
}) {
  const uid = useId();
  const [open, setOpen] = useState(false);
  const [to, setTo] = useState("");
  const [message, setMessage] = useState("");
  const [pending, startTransition] = useTransition();

  function send() {
    startTransition(async () => {
      const result = await emailDocument(kind, id, to, message);
      if (result.error) toast.error(result.fieldErrors?.to ?? result.error);
      else {
        toast.success(`Sent to ${result.sentTo}`);
        setOpen(false);
        setMessage("");
      }
    });
  }

  return (
    <section className="grid gap-2 rounded-lg border bg-card p-5">
      <h2 className="text-sm font-semibold">Send</h2>
      <Button asChild variant="outline" className="justify-start">
        <a href={`/${kind}/${id}/pdf`} target="_blank" rel="noopener">
          <FileDown /> {number ? "Download PDF" : "Preview PDF"}
        </a>
      </Button>
      {canEmail && (
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button variant="outline" className="justify-start">
              <Mail /> Email to client
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Email {number}</DialogTitle>
              <DialogDescription>
                The PDF is attached. Replies go to your organization&apos;s billing email.
              </DialogDescription>
            </DialogHeader>
            <div className="grid gap-4">
              <FormField id={`to-${uid}`} label="To">
                <Input
                  id={`to-${uid}`}
                  type="email"
                  value={to}
                  onChange={(e) => setTo(e.target.value)}
                  placeholder={clientEmail ?? "client@example.com"}
                />
              </FormField>
              {!clientEmail && !to && (
                <p className="-mt-2 text-xs text-muted-foreground">This client has no email on file; type one.</p>
              )}
              <FormField id={`msg-${uid}`} label="Personal message (optional)">
                <Textarea id={`msg-${uid}`} rows={3} value={message} onChange={(e) => setMessage(e.target.value)} />
              </FormField>
            </div>
            <DialogFooter>
              <Button onClick={send} disabled={pending || (!clientEmail && !to.trim())}>
                <Mail /> {pending ? "Sending…" : "Send email"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </section>
  );
}
