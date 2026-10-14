import type { Metadata } from "next";
import { Be_Vietnam_Pro, JetBrains_Mono } from "next/font/google";
import "./globals.css";
import { Providers } from "@/app/providers";
import { SHOP_NAME, SHOP_TAGLINE } from "@/config/constants";

// Be Vietnam Pro: thiết kế cho tiếng Việt (dấu không bị dính/lệch); next/font tự host, không gọi Google lúc chạy
const sans = Be_Vietnam_Pro({
  subsets: ["latin", "vietnamese"],
  weight: ["400", "500", "600", "700", "800"],
  variable: "--font-be-vietnam",
  display: "swap",
});
const mono = JetBrains_Mono({ subsets: ["latin"], variable: "--font-jetbrains", display: "swap" });

export const metadata: Metadata = {
  title: { default: `${SHOP_NAME}. — ${SHOP_TAGLINE}`, template: `%s · ${SHOP_NAME}.` },
  description: "Thương mại điện tử đa kênh: storefront, đặt hàng, thanh toán và quản trị trên kiến trúc microservices",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="vi" className={`${sans.variable} ${mono.variable} h-full`}>
      <body className="flex min-h-full flex-col">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
