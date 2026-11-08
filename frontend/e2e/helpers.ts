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
  await confirmEmail(page, email);
}

const MAILPIT = process.env.MAILPIT_URL ?? "http://localhost:8025";

/** Opens the confirmation link from the sign-up email (caught by Mailpit) and presses Confirm. */
export async function confirmEmail(page: Page, email: string) {
  let link: string | undefined;
  await expect
    .poll(async () => {
      const search = await page.request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
      const id = (await search.json()).messages?.[0]?.ID;
      if (!id) return undefined;
      const message = await (await page.request.get(`${MAILPIT}/api/v1/message/${id}`)).json();
      link = /\/verify-email\/[A-Za-z0-9_-]{43}/.exec(message.HTML)?.[0];
      return link;
    })
    .toBeTruthy();
  await page.goto(link!);
  await page.getByRole("button", { name: "Confirm my email" }).click();
  await expect(page.getByText("Your email address is confirmed.")).toBeVisible();
  await page.goto("/dashboard");
}

/** Picks an option in a shadcn/Radix Select by its label. */
export async function choose(page: Page, label: string, option: string | RegExp) {
  await page.getByRole("combobox", { name: label }).click();
  await page.getByRole("option", { name: option }).click();
}
