import { expect, type Page, test } from "@playwright/test";
import { choose, register, uniqueEmail } from "./helpers";

async function setUpTaxAndClient(page: Page, clientName: string) {
  await page.getByRole("link", { name: "Tax rates" }).click();
  await page.getByRole("textbox", { name: "Name", exact: true }).fill("GST");
  await page.getByRole("textbox", { name: "Rate %" }).fill("16");
  await page.getByLabel("Default for new products and lines").check();
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByTestId("tax-rate-row")).toContainText("16%");

  await page.goto("/clients/new");
  await page.getByRole("textbox", { name: "Client name" }).fill(clientName);
  await page.getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText(clientName);
}

async function fillFirstLine(page: Page, description: string, qty: string, price: string) {
  await page.getByRole("textbox", { name: "Description for line 1" }).fill(description);
  await page.getByRole("textbox", { name: "Quantity for line 1" }).fill(qty);
  await page.getByRole("textbox", { name: "Unit price for line 1" }).fill(price);
}

test("invoice: draft, live totals, issue, part-pay, credit note to paid", async ({ page }) => {
  await register(page, { name: "Invoice Owner", email: uniqueEmail("inv"), org: "Invoice Trading" });
  await setUpTaxAndClient(page, "Petra Design Studio");

  await page.goto("/invoices/new");
  await choose(page, "Client", "Petra Design Studio");
  await fillFirstLine(page, "Bilingual website copy", "12", "25.000");
  // 300.000 + 16% = 348.000, previewed before saving.
  await expect(page.getByTestId("editor-total").filter({ visible: true })).toHaveText("JOD 348.000");
  await page.getByRole("button", { name: "Save draft" }).click();

  await expect(page.getByRole("heading", { level: 1 })).toContainText("Draft invoice");
  await page.getByRole("button", { name: "Issue invoice" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Issue" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("INV-2026-0001");
  await expect(page.getByTestId("balance-due")).toHaveText("JOD 348.000");

  await page.getByRole("button", { name: "Record payment" }).click();
  await page.getByRole("dialog").getByLabel("Amount (JOD)").fill("100");
  await page.getByRole("dialog").getByRole("button", { name: "Record payment" }).click();
  await expect(page.getByTestId("balance-due")).toHaveText("JOD 248.000");
  await expect(page.getByRole("heading", { level: 1 })).toContainText("PARTIALLY PAID");

  await page.getByRole("button", { name: "Issue credit note" }).click();
  await page.getByRole("dialog").getByLabel("Reason (printed on the credit note)").fill("Loyalty discount");
  await page.getByRole("dialog").getByLabel("Amount to credit (JOD, tax included)").fill("248");
  await page.getByRole("dialog").getByRole("button", { name: "Issue credit note" }).click();
  await expect(page.getByTestId("balance-due")).toHaveText("JOD 0.000");
  await expect(page.getByRole("heading", { level: 1 })).toContainText("PAID");
  await expect(page.getByText("CN-2026-0001")).toBeVisible();
});

test("quote: send, accept, turn into an invoice draft", async ({ page }) => {
  await register(page, { name: "Quote Owner", email: uniqueEmail("quo"), org: "Quote Trading" });
  await setUpTaxAndClient(page, "Wadi Rum Tours");

  await page.goto("/quotes/new");
  await choose(page, "Client", "Wadi Rum Tours");
  await fillFirstLine(page, "Desert camp website", "1", "1200");
  await expect(page.getByTestId("editor-total").filter({ visible: true })).toHaveText("JOD 1,392.000");
  await page.getByRole("button", { name: "Save draft" }).click();

  await expect(page.getByRole("heading", { level: 1 })).toContainText("Draft quote");
  await page.getByRole("button", { name: "Send quote" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Send" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("QUO-2026-0001");

  await page.getByRole("button", { name: "Mark accepted" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("ACCEPTED");
  await page.getByRole("button", { name: "Create invoice" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Create invoice" }).click();

  await expect(page).toHaveURL(/\/invoices\/[0-9a-f-]+$/);
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Draft invoice");
  await expect(page.getByTestId("editor-total").filter({ visible: true })).toHaveText("JOD 1,392.000");
});
