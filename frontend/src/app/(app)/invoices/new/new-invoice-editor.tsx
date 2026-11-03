"use client";

import { DocumentEditor, type EditorData } from "@/components/document-editor";
import { saveInvoice } from "../actions";

export function NewInvoiceEditor({ data, presetClientId }: { data: EditorData; presetClientId?: string }) {
  return <DocumentEditor kind="invoice" data={data} presetClientId={presetClientId} save={(input) => saveInvoice(null, input)} />;
}
