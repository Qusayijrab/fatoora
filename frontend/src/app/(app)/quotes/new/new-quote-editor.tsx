"use client";

import { DocumentEditor, type EditorData } from "@/components/document-editor";
import { saveQuote } from "../actions";

export function NewQuoteEditor({ data }: { data: EditorData }) {
  return <DocumentEditor kind="quote" data={data} save={(input) => saveQuote(null, input)} />;
}
