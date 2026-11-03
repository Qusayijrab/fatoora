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
