import Link from "next/link";
import { LogoMark } from "@/components/brand";

/** Split screen: the cedar brand field on one side, the form on paper on the other. */
export default function AuthLayout({ children }: LayoutProps<"/">) {
  return (
    <div className="grid flex-1 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <aside className="relative flex flex-col justify-between gap-10 overflow-hidden bg-cedar px-6 py-8 text-cedar-foreground sm:px-10 lg:px-14 lg:py-12">
        <Link href="/" className="flex items-center gap-2.5">
          <LogoMark inverted className="size-8" />
          <span className="text-lg font-semibold tracking-[-0.02em]">Fatoora</span>
          <span lang="ar" dir="rtl" className="text-base opacity-70">
            فاتورة
          </span>
        </Link>

        <div className="hidden max-w-md lg:block">
          <p className="text-[2.6rem] font-semibold leading-[1.08] tracking-[-0.03em]">
            Invoices that add up to the last fils.
          </p>
          <p lang="ar" dir="rtl" className="mt-4 text-end text-2xl font-medium text-saffron">
            فواتير دقيقة حتى آخر فلس
          </p>
          <dl className="figures mt-10 grid max-w-sm grid-cols-[auto_1fr] gap-x-6 gap-y-2 border-t border-cedar-foreground/20 pt-5 text-sm">
            <dt className="opacity-70">JOD</dt>
            <dd className="text-end font-medium">1,250.500</dd>
            <dt className="opacity-70">SAR</dt>
            <dd className="text-end font-medium">4,690.00</dd>
            <dt className="opacity-70">KWD</dt>
            <dd className="text-end font-medium">385.125</dd>
          </dl>
          <p className="mt-2 text-xs opacity-60">Each currency keeps its own decimals. Example amounts.</p>
        </div>

        <p className="hidden text-xs opacity-55 lg:block">Quotes · Invoices · Payment links · Reminders</p>
        {/* Seal watermark: the mark at architectural scale, cropped by the field. */}
        <LogoMark inverted className="pointer-events-none absolute -bottom-24 -end-24 size-96 opacity-[0.06]" />
      </aside>

      <main className="flex items-center justify-center px-5 py-10 sm:px-10">
        <div className="w-full max-w-[26rem]">{children}</div>
      </main>
    </div>
  );
}
