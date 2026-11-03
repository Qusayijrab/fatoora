import { expect, test } from "@playwright/test";
import { choose, register, uniqueEmail } from "./helpers";

test("client portal: the client accepts a quote and sees what they owe", async ({ browser, page }) => {
  await register(page, { name: "Portal Owner", email: uniqueEmail("portal"), org: "Portal Trading" });

  await page.goto("/clients/new");
  await page.getByRole("textbox", { name: "Client name" }).fill("Portal Client");
  await page.getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Portal Client");
  const clientUrl = page.url();

  // A sent quote and an issued invoice for this client.
  await page.goto("/quotes/new");
  await choose(page, "Client", "Portal Client");
  await page.getByRole("textbox", { name: "Description for line 1" }).fill("Website");
  await page.getByRole("textbox", { name: "Unit price for line 1" }).fill("800");
  await page.getByRole("button", { name: "Save draft" }).click();
  await page.getByRole("button", { name: "Send quote" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Send" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("QUO-2026-0001");

  await page.goto("/invoices/new");
  await choose(page, "Client", "Portal Client");
  await page.getByRole("textbox", { name: "Description for line 1" }).fill("Deposit");
  await page.getByRole("textbox", { name: "Unit price for line 1" }).fill("200");
  await page.getByRole("button", { name: "Save draft" }).click();
  await page.getByRole("button", { name: "Issue invoice" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Issue" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("INV-2026-0001");
  // The invoice page shows its history from the audit log.
  await expect(page.getByText("Invoice INV-2026-0001 issued")).toBeVisible();

  await page.goto(clientUrl);
  await page.getByRole("button", { name: "Get portal link" }).click();
  const link = await page.getByRole("textbox", { name: "Portal link" }).inputValue();

  const client = await browser.newContext();
  const visitor = await client.newPage();
  await visitor.goto(new URL(link).pathname);
  await expect(visitor.getByTestId("portal-owed")).toHaveText("JOD 200.000");
  await expect(visitor.getByTestId("portal-invoice")).toHaveCount(1);
  await expect(visitor.getByRole("link", { name: "Pay" })).toBeVisible();
  await visitor.getByRole("button", { name: "Accept QUO-2026-0001" }).click();
  await expect(visitor.getByTestId("portal-quote")).toContainText("ACCEPTED");
  await client.close();

  // The owner sees the client's answer, and it is in the audit log.
  await page.goto("/quotes");
  await expect(page.getByTestId("quote-row").first()).toContainText("ACCEPTED");
  await page.getByRole("link", { name: "Audit log" }).click();
  await expect(page.getByText("accepted by the client in the portal")).toBeVisible();
});
