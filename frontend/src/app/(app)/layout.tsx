import Link from "next/link";
import { Suspense } from "react";
import { LogoMark } from "@/components/brand";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import { AppSidebar } from "./app-sidebar";
import { SidebarOrgSlot, SidebarSlotSkeleton, SidebarUserSlot } from "./sidebar-slots";
import { VerifyEmailBanner } from "./verify-email-banner";

/**
 * The app frame. The sidebar and nav are static and render instantly; the org
 * switcher and user menu read the session cookie, so each streams in behind its own
 * <Suspense> (with Cache Components, reading cookies outside a boundary is a build error).
 */
export default function AppLayout({ children }: LayoutProps<"/">) {
  return (
    <SidebarProvider>
      <AppSidebar
        header={
          <>
            <Link href="/dashboard" className="flex items-center gap-2.5 px-1 text-foreground">
              <LogoMark className="size-7" />
              <span className="text-[1.05rem] font-semibold tracking-[-0.02em] group-data-[collapsible=icon]:hidden">
                Fatoora
              </span>
              <span lang="ar" dir="rtl" className="text-sm text-muted-foreground group-data-[collapsible=icon]:hidden">
                فاتورة
              </span>
            </Link>
            <Suspense fallback={<SidebarSlotSkeleton />}>
              <SidebarOrgSlot />
            </Suspense>
          </>
        }
        footer={
          <Suspense fallback={<SidebarSlotSkeleton />}>
            <SidebarUserSlot />
          </Suspense>
        }
      />
      <SidebarInset className="bg-background">
        <header className="sticky top-0 z-10 flex h-12 items-center gap-2 border-b bg-background/90 px-4 backdrop-blur-sm md:hidden">
          <SidebarTrigger />
          <LogoMark className="size-6" />
          <span className="font-semibold">Fatoora</span>
        </header>
        <div className="hidden px-4 pt-3 md:block">
          <SidebarTrigger className="text-muted-foreground" />
        </div>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 pb-16 pt-4 sm:px-8 md:pt-2">
          <Suspense fallback={null}>
            <VerifyEmailBanner />
          </Suspense>
          {children}
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}
