import { expect, test } from "@playwright/test";
import { choose, register, uniqueEmail } from "./helpers";

test("set up tax, a client and a product; the onboarding route follows", async ({ page }) => {
  await register(page, { name: "Catalog Owner", email: uniqueEmail("catalog"), org: "Catalog Trading" });

  // Tax rate, made the default.
  await page.getByRole("link", { name: "Tax rates" }).click();
  await page.getByRole("textbox", { name: "Name", exact: true }).fill("General sales tax");
  await page.getByRole("textbox", { name: "Rate %" }).fill("16");
  await page.getByLabel("Default for new products and lines").check();
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByTestId("tax-rate-row")).toContainText("16%");
  await expect(page.getByTestId("tax-rate-row")).toContainText("Default");

  // Client with an Arabic name.
  await page.getByRole("link", { name: "Clients" }).click();
  await page.getByRole("link", { name: "Add your first client" }).click();
  await page.getByRole("textbox", { name: "Client name" }).fill("شركة الندى للتجارة");
  await page.getByRole("textbox", { name: "Billing email" }).fill("billing@alnada.example");
  await page.getByRole("textbox", { name: "Tax number" }).fill("0123456");
  await choose(page, "Country", /^JO/);
  await page.getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("شركة الندى للتجارة");

  // Product priced to three decimals in JOD; the default tax is preselected.
  await page.getByRole("link", { name: "Products" }).click();
  await page.getByRole("link", { name: "Add your first product" }).click();
  await page.getByRole("textbox", { name: "Name", exact: true }).fill("Brand identity design");
  await page.getByRole("textbox", { name: "Unit price" }).fill("850.500");
  await page.getByRole("button", { name: "Add product" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Brand identity design");
  await expect(page.getByText("JOD 850.500 per item")).toBeVisible();

  // Too many decimals is refused with the backend's message.
  await page.getByRole("textbox", { name: "Unit price" }).fill("1.0005");
  await page.getByRole("button", { name: "Save changes" }).click();
  await expect(page.getByText(/JOD allows 3 decimals|must be an amount/)).toBeVisible();

  // Dashboard: client and product steps are now done.
  await page.getByRole("link", { name: "Dashboard" }).click();
  await expect(page.getByTestId("route-client")).toHaveAttribute("data-done", "true");
  await expect(page.getByTestId("route-product")).toHaveAttribute("data-done", "true");
});
