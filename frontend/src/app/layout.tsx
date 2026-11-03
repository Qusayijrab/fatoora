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
    { media: "(prefers-color-scheme: light)", color: "#173f35" },
    { media: "(prefers-color-scheme: dark)", color: "#0b1512" },
  ],
};

const CONTRACT = `<!--
THESIS: Invoicing as an Arab modernist bilingual document system; refuses the blue-gradient SaaS dashboard.
OWN-WORLD: cedar-green committed field (sidebar, hero), cool ink-on-paper neutrals, one saffron accent, Readex Pro for Latin and Arabic, 6px corners, document states as ink stamps.
STORY: an Amman or Gulf business owner sees exact 3-decimal money and bilingual documents, trusts it, creates an account, and is walked from first client to first payment.
FIRST VIEWPORT: app = cedar sidebar + "from work to paid" route on the dashboard; landing = cedar field with bilingual headline left, live invoice sheet right that the visitor stamps through its states.
FORM: Arab modernist bilingual graphic system, grounded list position 7, seed b2252e0e. Raised by the ticket wallet: every state is stamped on the record, nothing disappears, it cancels.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
-->`;

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      dir="ltr"
      suppressHydrationWarning
      className={`${readex.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col">
        <div hidden dangerouslySetInnerHTML={{ __html: CONTRACT }} />
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
