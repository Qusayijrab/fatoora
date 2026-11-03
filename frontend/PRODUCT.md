# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Owners, bookkeepers and staff of small businesses and freelancers in Jordan and the Gulf.
They invoice clients in local currencies (JOD, SAR, AED, KWD, QAR, BHD, OMR) and USD,
often deal with bilingual (Arabic/English) clients, and do their invoicing between
other work, usually on a laptop, sometimes on a phone.

Their jobs: keep a list of clients and products, send quotes, turn accepted quotes into
invoices, send invoices as PDF by email, get paid through a payment link, see what is
overdue, and let the system chase late payers and repeat monthly invoices.

## Product Purpose

Fatoora ("invoice" in Arabic) is a multi-tenant invoicing and quotes app. Success means a
business gets from "did the work" to "got paid" with as few steps as possible, and always
knows who owes what.

It is also a portfolio project, so correctness that can be demonstrated (money handling,
numbering, tenant isolation, audit trail) matters as much as features.

## Positioning

Built for the Arab-region SMB: correct 3-decimal currencies (JOD, KWD, BHD, OMR),
Arabic-capable invoices and PDFs, org time zones, and multi-currency with exchange rates
snapshotted at issue time. Money is never stored as floating point.

## Operating Context

- One user can belong to several organizations, with roles OWNER, ADMIN, MEMBER.
- Workflows: clients and products, quotes (DRAFT -> SENT -> ACCEPTED/REJECTED/EXPIRED),
  invoices (DRAFT -> ISSUED -> PARTIALLY_PAID -> PAID, OVERDUE, VOID), payment links,
  recurring invoices, automatic overdue reminders, a client portal by unguessable link.
- Issued invoices are immutable; corrections are void or credit note.

## Capabilities and Constraints

- Stack: Next.js 16 (App Router, Cache Components) + Tailwind v4 + shadcn/ui frontend;
  Spring Boot 4.1 + PostgreSQL backend. The browser never calls the backend directly.
- UI language: English now. Layout must be RTL-safe (logical spacing such as ms/me/ps/pe)
  so an Arabic UI can be added later. Invoice content and PDFs must support Arabic text.
- Undecided: final product name styling, logo, pricing (do not invent any).

## Brand Commitments

Name: Fatoora. No logo or palette exists yet.

## Evidence on Hand

No customers, testimonials, metrics, pricing or press exist. Never fabricate them.

## Product Principles

1. Money must be exact and obvious: amounts, currencies and states are never ambiguous.
2. Every screen answers "what do I need to do next?"
3. Speed for repeat work: common actions are one or two clicks from anywhere.
4. Trust through visibility: status, history and audit trail are always reachable.
5. Region-native, not translated-afterthought: currencies, Arabic text and RTL are first-class.

## Accessibility & Inclusion

WCAG 2.2 AA contrast and keyboard access. Numbers use tabular figures. Must work at
phone width.
