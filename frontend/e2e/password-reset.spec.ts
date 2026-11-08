import { expect, test } from "@playwright/test";
import { PASSWORD, register, uniqueEmail } from "./helpers";

const MAILPIT = process.env.MAILPIT_URL ?? "http://localhost:8025";

test("forgot password: email link, new password, sign in with it", async ({ page, browser }) => {
  const email = uniqueEmail("forgot");
  await register(page, { name: "Forgetful Owner", email, org: "Forgetful Trading" });

  // Someone who isn't signed in, on another browser.
  const visitor = await (await browser.newContext()).newPage();
  await visitor.goto("/login");
  await visitor.getByRole("link", { name: "Forgot password?" }).click();
  await expect(visitor.getByRole("heading", { level: 1 })).toContainText("Forgot your password?");
  await visitor.waitForLoadState("networkidle");
  await visitor.getByRole("textbox", { name: "Email" }).fill(email);
  await visitor.getByRole("button", { name: "Send reset link" }).click();
  await expect(visitor.getByText("Check your inbox")).toBeVisible();

  let link: string | undefined;
  await expect
    .poll(async () => {
      const query = encodeURIComponent(`to:${email} subject:"Reset your Fatoora password"`);
      const id = (await (await visitor.request.get(`${MAILPIT}/api/v1/search?query=${query}`)).json()).messages?.[0]?.ID;
      if (!id) return undefined;
      const message = await (await visitor.request.get(`${MAILPIT}/api/v1/message/${id}`)).json();
      link = /\/reset-password\/[A-Za-z0-9_-]{43}/.exec(message.HTML)?.[0];
      return link;
    })
    .toBeTruthy();

  await visitor.goto(link!);
  await visitor.getByLabel("New password", { exact: true }).fill("a-brand-new-passphrase");
  await visitor.getByLabel("Repeat the new password").fill("a-brand-new-passphrase");
  await visitor.getByRole("button", { name: "Set new password" }).click();
  await expect(visitor.getByText("Your password was changed.")).toBeVisible();

  // The old password no longer works; the new one does.
  await visitor.getByRole("link", { name: "Log in" }).click();
  await visitor.getByRole("textbox", { name: "Email" }).fill(email);
  await visitor.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await visitor.getByRole("button", { name: "Log in" }).click();
  await expect(visitor.getByRole("alert").filter({ hasText: "Invalid email or password" })).toBeVisible();
  await visitor.getByLabel("Password", { exact: true }).fill("a-brand-new-passphrase");
  await visitor.getByRole("button", { name: "Log in" }).click();
  await expect(visitor).toHaveURL(/\/dashboard$/);
});
