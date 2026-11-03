import { expect, type Page } from "@playwright/test";

export const PASSWORD = "correct-horse-battery";

export function uniqueEmail(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

export async function register(page: Page, { name, email, org }: { name: string; email: string; org: string }) {
  await page.goto("/register");
  await page.getByLabel("Your name").fill(name);
  await page.getByRole("textbox", { name: "Email" }).fill(email);
  await page.getByLabel("Password (8+ characters)").fill(PASSWORD);
  await page.getByLabel("Business name").fill(org);
  await page.getByLabel("Time zone").fill("Asia/Amman");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

/** Picks an option in a shadcn/Radix Select by its label. */
export async function choose(page: Page, label: string, option: string | RegExp) {
  await page.getByRole("combobox", { name: label }).click();
  await page.getByRole("option", { name: option }).click();
}
