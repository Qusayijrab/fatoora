import { ResetPasswordForm } from "./reset-password-form";

export const metadata = { title: "Choose a new password" };

export default async function ResetPasswordPage({ params }: PageProps<"/reset-password/[token]">) {
  const { token } = await params;
  return (
    <>
      <h1 className="text-2xl font-semibold">Choose a new password</h1>
      <p className="mt-1.5 text-sm text-muted-foreground">At least 8 characters. You&apos;ll be signed out everywhere else.</p>
      <div className="mt-8">
        <ResetPasswordForm token={token} />
      </div>
    </>
  );
}
