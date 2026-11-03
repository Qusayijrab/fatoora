import { expect, test } from "@playwright/test";
import { choose, register, uniqueEmail } from "./helpers";

test("client pays online from the payment link; the invoice becomes PAID", async ({ browser, page }) => {
  await register(page, { name: "Pay Owner", email: uniqueEmail("pay"), org: "Pay Trading" });

  await page.goto("/clients/new");
  await page.getByRole("textbox", { name: "Client name" }).fill("Online Client");
  await page.getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Online Client");

  await page.goto("/invoices/new");
  await choose(page, "Client", "Online Client");
  await page.getByRole("textbox", { name: "Description for line 1" }).fill("Monthly retainer");
  await page.getByRole("textbox", { name: "Unit price for line 1" }).fill("250");
  await page.getByRole("button", { name: "Save draft" }).click();
  await page.getByRole("button", { name: "Issue invoice" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Issue" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("INV-2026-0001");

  await page.getByRole("button", { name: "Get payment link" }).click();
  const link = await page.getByRole("textbox", { name: "Payment link" }).inputValue();
  expect(link).toMatch(/\/pay\/[A-Za-z0-9_-]{43}$/);
  const path = new URL(link).pathname;

  // The client: a separate browser with no login.
  const client = await browser.newContext();
  const payer = await client.newPage();
  await payer.goto(path);
  await expect(payer.getByTestId("pay-amount")).toHaveText("JOD 250.000");
  await payer.getByRole("button", { name: "Pay JOD 250.000" }).click();
  await expect(payer).toHaveURL(/\/pay\/mock\//);
  await expect(payer.getByText("Test checkout · no real money moves")).toBeVisible();
  await payer.getByRole("button", { name: "Confirm test payment of JOD 250.000" }).click();
  await expect(payer).toHaveURL(/status=success/);
  await expect(payer.getByText("Payment received. Thank you!")).toBeVisible();
  await expect(payer.getByText("Paid in full")).toBeVisible();
  await client.close();

  // The owner sees it, recorded as an online payment.
  await page.reload();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("PAID");
  await expect(page.getByTestId("payment-row")).toContainText("Online");
});

test("an invalid payment link shows a friendly error", async ({ page }) => {
  await page.goto("/pay/this-is-not-a-real-token-at-all-0000000000000");
  await expect(page.getByRole("heading", { name: "This link is not valid" })).toBeVisible();
});
