import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { NewOrganizationForm } from "./new-organization-form";

export default function NewOrganizationPage() {
  return (
    <Card className="mx-auto max-w-md">
      <CardHeader>
        <CardTitle>New organization</CardTitle>
        <CardDescription>You will be its owner, and we will switch you to it.</CardDescription>
      </CardHeader>
      <CardContent>
        <NewOrganizationForm />
      </CardContent>
    </Card>
  );
}
