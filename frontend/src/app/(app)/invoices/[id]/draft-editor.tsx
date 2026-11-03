"use client";

import { Stamp, Trash2 } from "lucide-react";
import { ConfirmAction } from "@/components/confirm-action";
import { DocumentEditor, type EditorData } from "@/components/document-editor";
import { Button } from "@/components/ui/button";
import type { Invoice } from "@/lib/types";
import { deleteInvoice, issueInvoice, saveInvoice } from "../actions";

export function DraftInvoiceEditor({ invoice, data }: { invoice: Invoice; data: EditorData }) {
  return (
    <DocumentEditor
      kind="invoice"
      data={data}
      initial={{
        clientId: invoice.client.id,
        currency: invoice.currency,
        issueDate: invoice.issueDate,
        endDate: invoice.dueDate,
        notes: invoice.notes,
        terms: invoice.terms,
        lines: invoice.lines,
      }}
      save={(input) => saveInvoice(invoice.id, input)}
      footer={
        <>
          <ConfirmAction
            trigger={
              <Button type="button" variant="ghost" className="me-auto text-destructive">
                <Trash2 /> Delete draft
              </Button>
            }
            title="Delete this draft?"
            description="Drafts have no number yet, so deleting one leaves no gap in your invoice sequence."
            confirmLabel="Delete"
            action={() => deleteInvoice(invoice.id)}
          />
          <ConfirmAction
            trigger={
              <Button type="button" variant="outline">
                <Stamp /> Issue invoice
              </Button>
            }
            title="Issue this invoice?"
            description="It gets the next invoice number and is locked: no more edits, only payments, a credit note or a void. Save your changes first."
            confirmLabel="Issue"
            destructive={false}
            successMessage="Invoice issued"
            action={() => issueInvoice(invoice.id)}
          />
        </>
      }
    />
  );
}
