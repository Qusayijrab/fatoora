import Link from "next/link";
import { ForgotPasswordForm } from "./forgot-password-form";

export const metadata = { title: "Forgot password" };

export default function ForgotPasswordPage() {
  return (
    <>
      <h1 className="text-2xl font-semibold">Forgot your password?</h1>
      <p className="mt-1.5 text-sm text-muted-foreground">
        Enter the email you sign in with and we&apos;ll send you a link to choose a new one.{" "}
        <Link href="/login" className="font-medium text-primary underline-offset-4 hover:underline">
          Back to log in
        </Link>
      </p>
      <div className="mt-8">
        <ForgotPasswordForm />
      </div>
    </>
  );
}
