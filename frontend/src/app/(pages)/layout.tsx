import type { ReactNode } from "react";
import { Footer } from "@/app/components/footer/Footer";
import { Header } from "@/app/components/header/Header";

export default function PagesLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <Header />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 pt-8 sm:px-6">{children}</main>
      <Footer />
    </>
  );
}
