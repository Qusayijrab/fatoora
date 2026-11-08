import { ConfirmEmailButton } from "./confirm-email-button";

export const metadata = { title: "Confirm your email" };

/**
 * Opened from the link in the sign-up email. Opening it changes nothing: email scanners
 * open links on their own, and the token works once. The button does the confirming.
 */
export default async function VerifyEmailPage({ params }: PageProps<"/verify-email/[token]">) {
  const { token } = await params;
  return (
    <>
      <h1 className="text-2xl font-semibold">Confirm your email</h1>
      <p className="mt-1.5 text-sm text-muted-foreground">
        One click, and you can send invoices and quotes to your clients.
      </p>
      <div className="mt-8">
        <ConfirmEmailButton token={token} />
      </div>
    </>
  );
}
