import { expect, test } from "@playwright/test";
import { choose, register, uniqueEmail } from "./helpers";

test("recurring invoice: create a monthly schedule and generate the next invoice", async ({ page }) => {
  await register(page, { name: "Landlord", email: uniqueEmail("rec"), org: "Rent Holdings" });

  await page.goto("/clients/new");
  await page.getByRole("textbox", { name: "Client name" }).fill("Tenant Co");
  await page.getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Tenant Co");

  await page.getByRole("link", { name: "Recurring" }).click();
  await page.getByRole("link", { name: "Set one up" }).click();
  await page.getByRole("textbox", { name: "Name" }).fill("Office rent");
  await choose(page, "Client", "Tenant Co");
  await page.getByRole("textbox", { name: "Description for line 1" }).fill("Monthly rent, unit 4B");
  await page.getByRole("textbox", { name: "Unit price for line 1" }).fill("450");
  await page.getByRole("button", { name: "Create recurring invoice" }).click();

  await expect(page.getByRole("heading", { level: 1 })).toContainText("Office rent");
  await expect(page.getByRole("heading", { level: 1 })).toContainText("ACTIVE");

  await page.getByRole("button", { name: "Generate next invoice now" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Generate" }).click();
  await expect(page).toHaveURL(/\/invoices\/[0-9a-f-]+$/);
  // Issued automatically, with the next number.
  await expect(page.getByRole("heading", { level: 1 })).toContainText("INV-2026-0001");

  await page.getByRole("link", { name: "Recurring" }).click();
  await expect(page.getByTestId("recurring-row")).toHaveCount(1);
  await page.getByRole("link", { name: "Office rent" }).click();
  await expect(page.getByTestId("run-row")).toHaveCount(1);
  await page.getByRole("button", { name: "Pause" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("PAUSED");
});
