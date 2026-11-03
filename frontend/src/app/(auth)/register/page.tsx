import Link from "next/link";
import { RegisterForm } from "./register-form";

export const metadata = { title: "Create your account" };

export default function RegisterPage() {
  return (
    <>
      <h1 className="text-2xl font-semibold">Create your account</h1>
      <p className="mt-1.5 text-sm text-muted-foreground">
        You and your business, in one step. Already have an account?{" "}
        <Link href="/login" className="font-medium text-primary underline-offset-4 hover:underline">
          Log in
        </Link>
      </p>
      <div className="mt-8">
        <RegisterForm />
      </div>
    </>
  );
}
