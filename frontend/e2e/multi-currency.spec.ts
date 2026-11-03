import { expect, test } from "@playwright/test";
import { choose, register, uniqueEmail } from "./helpers";

test("a USD invoice in a JOD company freezes the peg and feeds the dashboard in JOD", async ({ page }) => {
  await register(page, { name: "FX Owner", email: uniqueEmail("fx"), org: "Amman Exports" });

  await page.goto("/clients/new");
  await page.getByRole("textbox", { name: "Client name" }).fill("Boston Buyer");
  await page.getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Boston Buyer");

  await page.goto("/invoices/new");
  await choose(page, "Client", "Boston Buyer");
  await choose(page, "Currency", /^USD/);
  await page.getByRole("textbox", { name: "Description for line 1" }).fill("Export consulting");
  await page.getByRole("textbox", { name: "Unit price for line 1" }).fill("1000");
  await expect(page.getByTestId("editor-total").filter({ visible: true })).toHaveText("USD 1,000.00");
  await page.getByRole("button", { name: "Save draft" }).click();
  await page.getByRole("button", { name: "Issue invoice" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Issue" }).click();

  await expect(page.getByTestId("frozen-rate")).toContainText("1 USD = 0.709 JOD (official peg)");
  await expect(page.getByTestId("frozen-rate")).toContainText("JOD 709.000");

  await page.getByRole("link", { name: "Dashboard" }).click();
  await expect(page.getByTestId("ledger-0")).toHaveText("JOD 709.000");
  await expect(page.getByText("Invoiced and collected, last 12 months")).toBeVisible();
  await expect(page.getByText("Boston Buyer").first()).toBeVisible();

  await page.getByRole("link", { name: "Exchange rates" }).click();
  await page.getByRole("textbox", { name: "1 unit = how many JOD" }).fill("0.71");
  await page.getByRole("button", { name: "Add rate" }).click();
  await expect(page.getByTestId("rate-row")).toContainText("1 USD = 0.71 JOD");
});
