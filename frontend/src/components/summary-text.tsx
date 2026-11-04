import { Fragment } from "react";

const DOC_NUMBER = /\b((?:INV|QUO|CN)-\d{4}-\d{4,})\b/g;

/** An audit summary where document numbers ("INV-2026-0010") never break across lines at the hyphen. */
export function SummaryText({ text }: { text: string }) {
  const parts = text.split(DOC_NUMBER);
  return (
    <>
      {parts.map((part, i) =>
        i % 2 === 1 ? (
          <span key={i} className="whitespace-nowrap">
            {part}
          </span>
        ) : (
          <Fragment key={i}>{part}</Fragment>
        ),
      )}
    </>
  );
}
