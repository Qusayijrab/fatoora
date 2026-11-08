// Shapes returned by the backend API (/api/v1).

export type Role = "OWNER" | "ADMIN" | "MEMBER";

export const ROLES: Role[] = ["OWNER", "ADMIN", "MEMBER"];

export type Organization = {
  id: string;
  name: string;
  defaultCurrency: string;
  timeZone: string;
  role: Role;
};

export type Me = {
  id: string;
  email: string;
  fullName: string;
  /** Until true, the organization can't email clients (unless another owner confirmed). */
  emailVerified: boolean;
  organization: Organization | null;
};

export type Member = {
  id: string;
  userId: string;
  email: string;
  fullName: string;
  role: Role;
  joinedAt: string;
};

export type TokenResponse = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
};

/** What a form's server action returns, for useActionState. */
export type FormState = {
  error?: string;
  fieldErrors?: Record<string, string>;
  values?: Record<string, string>;
  ok?: boolean;
};

export function canManageMembers(role: Role | undefined): boolean {
  return role === "OWNER" || role === "ADMIN";
}

/** Money from the API: amount is a decimal string ("1250.500"), never a JS float. */
export type Money = { amount: string; currency: string; amountMinor: number };

export type Page<T> = { items: T[]; page: number; size: number; total: number };

export type OrganizationDetails = Organization & {
  remindersEnabled: boolean;
  reminderDays: number[];
  legalName: string | null;
  taxId: string | null;
  email: string | null;
  phone: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  country: string | null;
};

export type Client = {
  id: string;
  name: string;
  email: string | null;
  phone: string | null;
  taxId: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  region: string | null;
  postalCode: string | null;
  country: string | null;
  currency: string | null;
  notes: string | null;
  archived: boolean;
  createdAt: string;
};

export type TaxRate = { id: string; name: string; rate: string; isDefault: boolean; archived: boolean };

export type Product = {
  id: string;
  name: string;
  description: string | null;
  sku: string | null;
  unit: string;
  unitPrice: Money;
  taxRate: TaxRate | null;
  archived: boolean;
};

export type Summary = Record<string, number>;

/** Sentinel for "nothing selected" in optional selects (Radix Select can't use ""). */
export const NONE_VALUE = "__none__";

export type InvoiceStatus = "DRAFT" | "ISSUED" | "PARTIALLY_PAID" | "PAID" | "OVERDUE" | "VOID";
export type QuoteStatus = "DRAFT" | "SENT" | "ACCEPTED" | "REJECTED" | "EXPIRED";
export type PaymentMethod = "CASH" | "BANK_TRANSFER" | "CHEQUE" | "CARD" | "ONLINE" | "OTHER";

export type ClientRef = { id: string; name: string };

export type Party = {
  name: string;
  email: string | null;
  phone: string | null;
  taxId: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  region: string | null;
  postalCode: string | null;
  country: string | null;
};

export type Line = {
  id: string;
  position: number;
  productId: string | null;
  description: string;
  quantity: string;
  unit: string | null;
  unitPrice: Money;
  taxRateId: string | null;
  taxRate: string;
  subtotal: Money;
  tax: Money;
  total: Money;
};

/** What the editor sends: decimal strings only. */
export type LineInput = {
  productId: string | null;
  description: string;
  quantity: string;
  unit: string | null;
  unitPrice: string;
  taxRateId: string | null;
};

export type DocumentInput = {
  clientId: string;
  currency: string;
  issueDate: string;
  dueDate?: string | null;
  validUntil?: string | null;
  notes: string | null;
  terms: string | null;
  lines: LineInput[];
};

export type Payment = {
  id: string;
  /** REFUND = money given back after a credit note on a paid invoice. */
  kind: "PAYMENT" | "REFUND";
  amount: Money;
  method: PaymentMethod;
  paidOn: string;
  reference: string | null;
  provider: string | null;
};

export type InvoiceSummary = {
  id: string;
  kind: "INVOICE" | "CREDIT_NOTE";
  number: string | null;
  status: InvoiceStatus;
  client: ClientRef;
  currency: string;
  issueDate: string;
  dueDate: string | null;
  total: Money;
  balanceDue: Money;
};

export type Invoice = InvoiceSummary & {
  billTo: Party | null;
  seller: Party | null;
  notes: string | null;
  terms: string | null;
  lines: Line[];
  subtotal: Money;
  tax: Money;
  amountPaid: Money;
  amountCredited: Money;
  amountRefunded: Money;
  /** Owed back to the client after a credit note on a paid invoice, until refunded. */
  refundDue: Money;
  payments: Payment[];
  creditNotes: { id: string; number: string; total: Money; issueDate: string; reason: string | null }[];
  /** Per invoice line, what credit notes may still credit. */
  creditable: { lineId: string; quantity: string; amount: Money }[];
  creditedInvoice: { id: string; number: string } | null;
  quoteId: string | null;
  baseCurrency: string | null;
  exchangeRate: string | null;
  rateSource: string | null;
  totalBase: Money | null;
  issuedAt: string | null;
  voidedAt: string | null;
  voidReason: string | null;
  createdAt: string;
};

export type QuoteSummary = {
  id: string;
  number: string | null;
  status: QuoteStatus;
  client: ClientRef;
  currency: string;
  issueDate: string;
  validUntil: string | null;
  total: Money;
  convertedInvoiceId: string | null;
};

export type Quote = QuoteSummary & {
  billTo: Party | null;
  notes: string | null;
  terms: string | null;
  lines: Line[];
  subtotal: Money;
  tax: Money;
  sentAt: string | null;
  acceptedAt: string | null;
  rejectedAt: string | null;
  createdAt: string;
};

export type PublicInvoice = {
  organizationName: string;
  number: string;
  status: InvoiceStatus;
  clientName: string;
  currency: string;
  issueDate: string;
  dueDate: string | null;
  lines: Line[];
  subtotal: Money;
  tax: Money;
  total: Money;
  amountPaid: Money;
  balanceDue: Money;
  payable: boolean;
  provider: string;
};

export type MockCheckout = {
  organizationName: string;
  invoiceNumber: string;
  amount: Money;
  status: "OPEN" | "COMPLETED" | "EXPIRED";
  payToken: string;
};

export type Frequency = "WEEKLY" | "MONTHLY" | "QUARTERLY" | "YEARLY";

export type Recurring = {
  id: string;
  name: string;
  client: ClientRef;
  currency: string;
  frequency: Frequency;
  intervalCount: number;
  startDate: string;
  endDate: string | null;
  nextRunDate: string | null;
  runCount: number;
  paymentTermsDays: number;
  autoIssue: boolean;
  autoEmail: boolean;
  status: "ACTIVE" | "PAUSED" | "ENDED";
  /** Failed runs in a row; after 3 the schedule pauses itself (pausedByFailure). */
  failureCount: number;
  lastError: string | null;
  lastFailedAt: string | null;
  pausedByFailure: boolean;
  notes: string | null;
  terms: string | null;
  lines: Line[];
  subtotal: Money;
  tax: Money;
  total: Money;
  lastRunAt: string | null;
};

export type RecurringRun = {
  periodDate: string;
  invoiceId: string | null;
  invoiceNumber: string | null;
  invoiceStatus: InvoiceStatus | null;
  createdAt: string;
};

export type RecurringInput = DocumentInput & {
  name: string;
  frequency: Frequency;
  intervalCount: number;
  startDate: string;
  endDate: string | null;
  paymentTermsDays: number;
  autoIssue: boolean;
  autoEmail: boolean;
};

export function describeFrequency(f: Frequency, n: number): string {
  const unit = { WEEKLY: "week", MONTHLY: "month", QUARTERLY: "quarter", YEARLY: "year" }[f];
  return n === 1 ? `Every ${unit}` : `Every ${n} ${unit}s`;
}

export type Overview = {
  baseCurrency: string;
  outstanding: Money;
  openInvoices: number;
  overdue: Money;
  overdueInvoices: number;
  collectedThisMonth: Money;
  invoicedThisMonth: Money;
  drafts: number;
  quotesAwaitingAnswer: number;
  months: { month: string; invoiced: Money; collected: Money }[];
  aging: { label: string; amount: Money; count: number }[];
  topClients: { clientId: string; name: string; outstanding: Money; invoices: number }[];
  activity: { summary: string; entityType: string; entityId: string; at: string }[];
};

export type ExchangeRate = { id: string; currency: string; baseCurrency: string; rate: string; effectiveDate: string };

export type AuditEntry = {
  id: string;
  entityType: string;
  entityId: string;
  action: string;
  summary: string;
  actorName: string | null;
  data: string;
  at: string;
};

export type PortalView = {
  organizationName: string;
  organizationEmail: string | null;
  clientName: string;
  outstanding: Money[];
  invoices: {
    id: string;
    kind: "INVOICE" | "CREDIT_NOTE";
    number: string;
    status: InvoiceStatus;
    issueDate: string;
    dueDate: string | null;
    total: Money;
    balanceDue: Money;
    payUrl: string | null;
  }[];
  quotes: { id: string; number: string; status: QuoteStatus; issueDate: string; validUntil: string | null; total: Money }[];
};

/** An online payment the provider took but that could not be applied to its invoice. */
export type PaymentProblem = {
  id: string;
  provider: string;
  invoiceId: string;
  invoiceNumber: string;
  clientName: string;
  detail: string;
  receivedAt: string;
};

/** One credit note line: an original invoice line, how many units, and optionally a lower unit price. */
export type CreditLineInput = { invoiceLineId: string; quantity: string; unitPrice: string | null };
