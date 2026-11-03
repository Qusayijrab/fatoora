import { expect, type Page, test } from "@playwright/test";

const PASSWORD = "correct-horse-battery";

function uniqueEmail(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

async function register(page: Page, { name, email, org }: { name: string; email: string; org: string }) {
  await page.goto("/register");
  await page.getByLabel("Your name").fill(name);
  await page.getByRole("textbox", { name: "Email" }).fill(email);
  await page.getByLabel("Password (8+ characters)").fill(PASSWORD);
  await page.getByLabel("Business name").fill(org);
  await page.getByLabel("Currency").selectOption("JOD");
  await page.getByLabel("Time zone").fill("Asia/Amman");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

test("protected pages redirect to login when signed out", async ({ page }) => {
  await page.goto("/settings/members");
  await expect(page).toHaveURL(/\/login$/);
});

test("wrong password shows an error", async ({ page }) => {
  await page.goto("/login");
  await page.getByRole("textbox", { name: "Email" }).fill(uniqueEmail("nobody"));
  await page.getByLabel("Password").fill("wrong-password");
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Invalid email or password" })).toBeVisible();
});

test("register, see dashboard, add a member, log out, log back in", async ({ browser, page }) => {
  // A colleague signs up first (in a separate browser context = separate cookies).
  const colleagueEmail = uniqueEmail("colleague");
  const colleagueContext = await browser.newContext();
  await register(await colleagueContext.newPage(), { name: "Colleague", email: colleagueEmail, org: "Colleague Co" });
  await colleagueContext.close();

  // The owner signs up and lands on the dashboard of their new organization.
  const ownerEmail = uniqueEmail("owner");
  await register(page, { name: "Owner Person", email: ownerEmail, org: "Acme Trading" });
  await expect(page.getByTestId("org-name")).toHaveText("Acme Trading");
  await expect(page.getByText("OWNER", { exact: true })).toBeVisible();

  // Members page: just the owner at first.
  await page.getByRole("link", { name: "Members" }).click();
  await expect(page.getByTestId("member-row")).toHaveCount(1);
  await expect(page.getByTestId("member-row").first()).toContainText("(you)");

  // Add the colleague as a member.
  await page.getByRole("textbox", { name: "Email" }).fill(colleagueEmail);
  await page.getByLabel("Role", { exact: true }).selectOption("MEMBER");
  await page.getByRole("button", { name: "Add" }).click();
  await expect(page.getByTestId("member-row")).toHaveCount(2);
  await expect(page.getByRole("row", { name: new RegExp(colleagueEmail) })).toContainText("MEMBER");

  // The last owner cannot remove themselves: the backend's rule shows up in the UI.
  await page.getByTestId("member-row").filter({ hasText: "(you)" }).getByRole("button", { name: "Remove" }).click();
  await expect(page.getByText("An organization must keep at least one owner")).toBeVisible();

  // Log out, then log back in.
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.getByRole("textbox", { name: "Email" }).fill(ownerEmail);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByTestId("org-name")).toHaveText("Acme Trading");
});
