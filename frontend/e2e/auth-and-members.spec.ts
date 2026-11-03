import { expect, test } from "@playwright/test";
import { PASSWORD, register, uniqueEmail } from "./helpers";

test("landing page lets a visitor stamp the example invoice", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toContainText("From done");
  await page.getByRole("button", { name: "Void" }).click();
  await expect(page.locator('[data-status="VOID"]').first()).toBeVisible();
});

test("protected pages redirect to login when signed out", async ({ page }) => {
  await page.goto("/settings/members");
  await expect(page).toHaveURL(/\/login$/);
});

test("wrong password shows an error", async ({ page }) => {
  await page.goto("/login");
  await page.getByRole("textbox", { name: "Email" }).fill(uniqueEmail("nobody"));
  await page.getByLabel("Password", { exact: true }).fill("wrong-password");
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
  await expect(page.getByTestId("route-company")).toHaveAttribute("data-done", "true");
  await expect(page.getByTestId("route-team")).toHaveAttribute("data-done", "false");

  // Members page: just the owner at first.
  await page.getByRole("link", { name: "Members" }).click();
  await expect(page.getByTestId("member-row")).toHaveCount(1);
  await expect(page.getByTestId("member-row").first()).toContainText("(you)");

  // Add the colleague as a member (default role).
  await page.getByRole("textbox", { name: "Add a member by email" }).fill(colleagueEmail);
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.getByText("Member added")).toBeVisible();
  await expect(page.getByTestId("member-row")).toHaveCount(2);
  await expect(page.getByRole("row", { name: new RegExp(colleagueEmail) })).toContainText("MEMBER");

  // The last owner cannot leave: the backend's rule comes back as a toast.
  await page.getByRole("button", { name: `Remove ${ownerEmail}` }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Leave" }).click();
  await expect(page.getByText("An organization must keep at least one owner")).toBeVisible();

  // The team step on the dashboard is now done.
  await page.getByRole("link", { name: "Dashboard" }).click();
  await expect(page.getByTestId("route-team")).toHaveAttribute("data-done", "true");

  // Log out from the account menu, then log back in.
  await page.getByRole("button", { name: "Account menu" }).click();
  await page.getByRole("menuitem", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.getByRole("textbox", { name: "Email" }).fill(ownerEmail);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByTestId("org-name")).toHaveText("Acme Trading");
});
