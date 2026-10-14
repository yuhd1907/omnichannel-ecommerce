import { SHOP_NAME } from "@/config/constants";

/** Biểu tượng "g" hình học + dấu chấm (cùng hình với favicon src/app/icon.svg). */
export function LogoMark({ size = 32, className = "" }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" className={className} aria-hidden>
      <rect width="32" height="32" rx="9" fill="currentColor" />
      <circle cx="14.5" cy="13.5" r="5" fill="none" stroke="#fff" strokeWidth="3" />
      <path d="M19.5 9.5v9a6 6 0 0 1-6 6H11" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="24" cy="24" r="2" fill="#fff" />
    </svg>
  );
}

export function Logo({ size = 32 }: { size?: number }) {
  return (
    <span className="flex items-center gap-2.5 text-neutral-950">
      <LogoMark size={size} />
      <span className="text-[22px] leading-none font-extrabold tracking-[-0.04em]">
        {SHOP_NAME}
        <span className="text-neutral-400">.</span>
      </span>
    </span>
  );
}
