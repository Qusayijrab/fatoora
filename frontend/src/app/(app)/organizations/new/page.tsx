import { PageHeader } from "@/components/page-header";
import { NewOrganizationForm } from "./new-organization-form";

export const metadata = { title: "New organization" };

export default function NewOrganizationPage() {
  return (
    <div className="max-w-xl">
      <PageHeader
        title="New organization"
        description="A separate business with its own clients, numbering and members. You will be its owner, and we switch you to it."
      />
      <div className="rounded-xl border bg-card shadow-xs p-5 sm:p-6">
        <NewOrganizationForm />
      </div>
    </div>
  );
}
