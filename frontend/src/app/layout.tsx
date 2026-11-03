import type { Metadata, Viewport } from "next";
import { Geist_Mono, Readex_Pro } from "next/font/google";
import { Providers } from "@/components/providers";
import "./globals.css";

// One bilingual geometric face: the same voice for Latin and Arabic.
const readex = Readex_Pro({
  variable: "--font-readex",
  subsets: ["latin", "arabic"],
  weight: "variable",
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: { default: "Fatoora · Invoices for Arab businesses", template: "%s · Fatoora" },
  description: "Quotes, invoices, payment links and reminders, with exact 3-decimal currencies and Arabic-ready PDFs.",
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f9f9fb" },
    { media: "(prefers-color-scheme: dark)", color: "#111114" },
  ],
};


export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      dir="ltr"
      suppressHydrationWarning
      className={`${readex.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
