import { expect, test } from "@playwright/test";
import { choose, register, uniqueEmail } from "./helpers";

test("send an invoice on WhatsApp: the client's number and the payment link are filled in", async ({ page, context }) => {
  // Never leave the machine: answer wa.me locally and just read the URL.
  await context.route("https://wa.me/**", (route) => route.fulfill({ body: "WhatsApp" }));
  await register(page, { name: "WA Owner", email: uniqueEmail("wa"), org: "WA Trading" });

  await page.goto("/clients/new");
  await page.getByRole("textbox", { name: "Client name" }).fill("Aqaba Divers");
  await page.getByRole("textbox", { name: "Phone" }).fill("079 123 4567");
  await page.getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Aqaba Divers");

  await page.goto("/invoices/new");
  await choose(page, "Client", "Aqaba Divers");
  await page.getByRole("textbox", { name: "Description for line 1" }).fill("Dive trip");
  await page.getByRole("textbox", { name: "Unit price for line 1" }).fill("80");
  await page.getByRole("button", { name: "Save draft" }).click();
  await page.getByRole("button", { name: "Issue invoice" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Issue" }).click();
  await expect(page.getByTestId("whatsapp")).toHaveText(/Send on WhatsApp/);

  const [tab] = await Promise.all([page.waitForEvent("popup"), page.getByTestId("whatsapp").click()]);
  await tab.waitForURL(/wa\.me/);
  const url = new URL(tab.url());
  expect(url.pathname).toBe("/962791234567");
  const text = url.searchParams.get("text") ?? "";
  expect(text).toContain("Aqaba Divers");
  expect(text).toMatch(/INV-\d{4}-0001/);
  expect(text).toMatch(/\/pay\/[A-Za-z0-9_-]{43}/);
});
