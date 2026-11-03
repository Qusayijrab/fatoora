import Link from "next/link";
import { LoginForm } from "./login-form";

export const metadata = { title: "Log in" };

export default function LoginPage() {
  return (
    <>
      <h1 className="text-2xl font-semibold">Log in</h1>
      <p className="mt-1.5 text-sm text-muted-foreground">
        New to Fatoora?{" "}
        <Link href="/register" className="font-medium text-primary underline-offset-4 hover:underline">
          Create an account
        </Link>
      </p>
      <div className="mt-8">
        <LoginForm />
      </div>
    </>
  );
}
