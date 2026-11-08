import { getMe } from "@/lib/api";
import { ResendVerificationButton } from "./resend-verification-button";

/** Shown until the user confirms their address; until an owner does, the organization can't email clients. */
export async function VerifyEmailBanner() {
  const me = await getMe();
  if (me.emailVerified) return null;
  return (
    <div
      role="status"
      data-testid="verify-email-banner"
      className="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-primary/30 bg-primary/5 px-4 py-3 text-sm"
    >
      <p>
        <span className="font-medium">Confirm your email address.</span>{" "}
        <span className="text-muted-foreground">
          We sent a link to <span dir="ltr">{me.email}</span>. Until it&apos;s confirmed, you can&apos;t email clients.
        </span>
      </p>
      <ResendVerificationButton />
    </div>
  );
}
