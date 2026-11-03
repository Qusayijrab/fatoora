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
