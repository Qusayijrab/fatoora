import { LogoMark } from "@/components/brand";

/** Public pages for payers: no app chrome, no login. */
export default function PayLayout({ children }: LayoutProps<"/pay">) {
  return (
    <div className="flex flex-1 flex-col bg-muted/40">
      <main className="mx-auto w-full max-w-2xl flex-1 px-4 py-8 sm:py-14">{children}</main>
      <footer className="flex items-center justify-center gap-2 pb-8 text-xs text-muted-foreground">
        <LogoMark className="size-4" />
        Payments by Fatoora
      </footer>
    </div>
  );
}
