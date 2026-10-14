import Link from "next/link";
import type { ButtonHTMLAttributes, ComponentProps, ReactNode } from "react";

// Nút đơn sắc. Hai nhóm:
// - Có viền (secondary, outline-danger): nền trắng, khi bấm có bóng xám mờ phía dưới.
// - Không viền (primary, ghost): chưa di chuột thì đậm, di chuột vào nhạt đi.
type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

const PRESS_SHADOW = "active:shadow-[0_8px_18px_-6px_rgba(0,0,0,0.28)] active:translate-y-px";

const VARIANT: Record<Variant, string> = {
  primary: "bg-neutral-900 text-white hover:bg-neutral-600",
  secondary: `border border-neutral-300 bg-white text-neutral-900 hover:border-neutral-400 ${PRESS_SHADOW}`,
  ghost: "text-neutral-900 hover:text-neutral-500",
  danger: `border border-neutral-300 bg-white text-rose-600 hover:border-rose-300 ${PRESS_SHADOW}`,
};

const SIZE: Record<Size, string> = {
  sm: "h-8 px-3 text-xs gap-1.5",
  md: "h-10 px-4 text-sm gap-2",
  lg: "h-12 px-6 text-[15px] gap-2",
};

function classes(variant: Variant, size: Size, extra?: string) {
  return [
    "inline-flex items-center justify-center whitespace-nowrap rounded-xl font-semibold transition duration-150",
    "disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none disabled:translate-y-0",
    VARIANT[variant],
    SIZE[size],
    extra ?? "",
  ].join(" ");
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  icon?: ReactNode;
}

export function Button({ variant = "primary", size = "md", loading, icon, className, children, disabled, ...rest }: ButtonProps) {
  return (
    <button className={classes(variant, size, className)} disabled={disabled || loading} {...rest}>
      {loading ? <Spinner /> : icon}
      {children}
    </button>
  );
}

interface LinkButtonProps extends ComponentProps<typeof Link> {
  variant?: Variant;
  size?: Size;
  icon?: ReactNode;
}

export function LinkButton({ variant = "primary", size = "md", icon, className, children, ...rest }: LinkButtonProps) {
  return (
    <Link className={classes(variant, size, className)} {...rest}>
      {icon}
      {children}
    </Link>
  );
}

export function Spinner({ className = "h-4 w-4" }: { className?: string }) {
  return <span className={`${className} inline-block animate-spin rounded-full border-2 border-current border-r-transparent`} aria-hidden />;
}

/** Ô chọn có viền (phương thức thanh toán, phiên bản, địa chỉ): bấm thì có bóng xám mờ. */
export function choiceClass(selected: boolean) {
  return `rounded-xl border text-left transition ${PRESS_SHADOW} ${
    selected ? "border-neutral-900 bg-neutral-50 ring-1 ring-neutral-900" : "border-neutral-300 bg-white hover:border-neutral-400"
  }`;
}
