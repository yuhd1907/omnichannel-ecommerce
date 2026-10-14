import { SHOP_TAGLINE } from "@/config/constants";
import { Logo } from "@/app/components/header/Logo";

export function Footer() {
  return (
    <footer className="mt-16 border-t border-neutral-200 bg-neutral-50">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-6 px-4 py-8 text-sm text-neutral-500 sm:px-6">
        <div>
          <Logo size={24} />
        </div>
        <span className="font-medium text-neutral-900">{SHOP_TAGLINE}</span>
      </div>
    </footer>
  );
}
