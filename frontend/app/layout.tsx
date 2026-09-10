import type { Metadata } from "next";
import { Space_Grotesk, Inter, Geist_Mono } from "next/font/google";
import { SessionProvider } from "@/context/SessionProvider";
import { LocaleProvider } from "@/lib/i18n/LocaleContext";
import "./globals.css";

/**
 * Type system for the "control panel" design (see .claude/agents/ui-designer.md): a geometric
 * display face for headings, a neutral workhorse sans for body copy, and a monospace face
 * deliberately reused for data-shaped content (prices, seat/ticket ids, countdowns, statuses) to
 * read as instrumentation rather than prose.
 */
const displayFont = Space_Grotesk({
  variable: "--font-display",
  subsets: ["latin"],
  weight: ["500", "600", "700"],
});

const bodyFont = Inter({
  variable: "--font-body",
  subsets: ["latin"],
});

const dataFont = Geist_Mono({
  variable: "--font-data",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Ticketing",
  description: "Browse events, pick seats, and get your tickets.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`${displayFont.variable} ${bodyFont.variable} ${dataFont.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <LocaleProvider>
          <SessionProvider>{children}</SessionProvider>
        </LocaleProvider>
      </body>
    </html>
  );
}
