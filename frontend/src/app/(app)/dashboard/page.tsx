import { Suspense } from "react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { getMe } from "@/lib/api";

export default function DashboardPage() {
  return (
    <Suspense fallback={<p className="text-muted-foreground">Loading…</p>}>
      <Dashboard />
    </Suspense>
  );
}

async function Dashboard() {
  const me = await getMe();
  const org = me.organization;

  return (
    <div className="grid gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Welcome, {me.fullName}</h1>
      {org ? (
        <Card>
          <CardHeader>
            <CardTitle data-testid="org-name">{org.name}</CardTitle>
            <CardDescription>
              Your role: <Badge variant="secondary">{org.role}</Badge>
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-1 text-sm text-muted-foreground">
            <p>Default currency: {org.defaultCurrency}</p>
            <p>Time zone: {org.timeZone}</p>
            <p className="pt-4">Clients, quotes and invoices arrive in the next phases.</p>
          </CardContent>
        </Card>
      ) : (
        <p className="text-muted-foreground">You are not in any organization. Create one with “+ New” above.</p>
      )}
    </div>
  );
}
