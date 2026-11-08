import { expect, test } from "@playwright/test";
import { register, uniqueEmail } from "./helpers";

test("import clients from a CSV: preview the rows, fix, then import", async ({ page }) => {
  await register(page, { name: "Import Owner", email: uniqueEmail("import"), org: "Import Trading" });

  await page.goto("/clients");
  await page.getByRole("link", { name: "Import CSV" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Import clients");

  // A row with a bad email: checked, nothing saved, import disabled.
  await page.getByTestId("csv-file").setInputFiles({
    name: "clients.csv",
    mimeType: "text/csv",
    buffer: Buffer.from('Name,Email,Country\n"Petra Design, LLC",hello@petra.jo,JO\nشركة الندى,not-an-email,JO\n'),
  });
  const report = page.getByTestId("import-report");
  await expect(report).toContainText("1 ready");
  await expect(report).toContainText("1 need fixing");
  await expect(page.getByRole("button", { name: /^Import 1 client$/ })).toBeDisabled();

  // Fixed file: both rows import.
  await page.getByTestId("csv-file").setInputFiles({
    name: "clients-fixed.csv",
    mimeType: "text/csv",
    buffer: Buffer.from('Name,Email,Country\n"Petra Design, LLC",hello@petra.jo,JO\nشركة الندى,info@nada.jo,JO\n'),
  });
  await expect(report).toContainText("2 ready");
  await page.getByRole("button", { name: "Import 2 clients" }).click();
  await expect(report).toContainText("Imported 2");

  await page.goto("/clients");
  await expect(page.getByRole("link", { name: "Petra Design, LLC" })).toBeVisible();
  await expect(page.getByRole("link", { name: "شركة الندى" })).toBeVisible();
});
