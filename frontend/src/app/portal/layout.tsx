import { LogoMark } from "@/components/brand";

/** The client portal: public, no app chrome. */
export default function PortalLayout({ children }: LayoutProps<"/portal">) {
  return (
    <div className="flex flex-1 flex-col bg-muted/40">
      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8 sm:py-12">{children}</main>
      <footer className="flex items-center justify-center gap-2 pb-8 text-xs text-muted-foreground">
        <LogoMark className="size-4" />
        Client portal by Fatoora
      </footer>
    </div>
  );
}
