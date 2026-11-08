import { expect, test } from "@playwright/test";
import { register, uniqueEmail } from "./helpers";

test("reports page: aging and tax summary, with CSV downloads", async ({ page }) => {
  await register(page, { name: "Report Owner", email: uniqueEmail("reports"), org: "Report Trading" });

  await page.getByRole("link", { name: "Reports" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Reports");
  await expect(page.getByTestId("aging-report")).toContainText("Nothing is owed right now.");
  await expect(page.getByTestId("tax-report")).toContainText("Nothing was issued in this period.");

  const csv = await page.request.get("/reports/export/aging");
  expect(csv.status()).toBe(200);
  expect(csv.headers()["content-type"]).toContain("text/csv");
  expect(await csv.text()).toContain("Client,Not yet due");
});
