"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { FiLogOut, FiPackage, FiSearch, FiShield, FiShoppingCart, FiUser } from "react-icons/fi";
import { toast } from "sonner";
import { Button } from "@/app/components/button/Button";
import { Logo } from "@/app/components/header/Logo";
import { useAuth } from "@/hooks/useAuth";
import { useCart } from "@/hooks/useCart";

export function Header() {
  const router = useRouter();
  const pathname = usePathname();
  const { user, isAdmin, hydrated, logout, openLogin } = useAuth();
  const { totalCount } = useCart();
  const [q, setQ] = useState("");

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    router.push(q.trim() ? `/?q=${encodeURIComponent(q.trim())}` : "/");
  };

  const onLogout = async () => {
    await logout();
    toast.info("Đã đăng xuất");
    router.push("/");
  };

  const nav = [
    { href: "/", label: "Cửa hàng", show: true },
    { href: "/orders", label: "Đơn hàng", show: !!user },
    { href: "/admin", label: "Quản trị", show: isAdmin },
  ];

  return (
    <header className="sticky top-0 z-40 border-b border-neutral-200 bg-white/90 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6">
        <Link href="/" className="shrink-0" aria-label="Trang chủ">
          <Logo />
        </Link>

        <nav className="hidden items-center gap-1 md:flex">
          {nav
            .filter((n) => n.show)
            .map((n) => {
              const active = n.href === "/" ? pathname === "/" : pathname.startsWith(n.href);
              return (
                <Link
                  key={n.href}
                  href={n.href}
                  className={`rounded-lg px-3 py-2 text-sm font-medium transition ${
                    active ? "bg-neutral-100 text-neutral-900" : "text-neutral-900 hover:text-neutral-500"
                  }`}
                >
                  {n.label}
                </Link>
              );
            })}
        </nav>

        <form onSubmit={onSearch} className="relative ml-auto w-full max-w-xs">
          <FiSearch className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-neutral-500" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Tìm sản phẩm..."
            className="input h-10 py-0 pl-9"
            aria-label="Tìm sản phẩm"
          />
        </form>

        <Link href="/cart" className="relative rounded-xl p-2.5 text-neutral-900 hover:bg-neutral-100 hover:text-neutral-500" aria-label="Giỏ hàng">
          <FiShoppingCart size={20} />
          {hydrated && totalCount > 0 && (
            <span className="absolute -top-0.5 -right-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-neutral-900 px-1 text-[11px] font-bold text-white">
              {totalCount}
            </span>
          )}
        </Link>

        {!hydrated ? (
          <div className="h-10 w-28" />
        ) : user ? (
          <div className="group relative">
            <button className="flex items-center gap-2 rounded-xl px-2.5 py-2 text-sm text-neutral-800 hover:bg-neutral-100">
              <span className="flex h-7 w-7 items-center justify-center rounded-full bg-neutral-200 text-neutral-900">
                {isAdmin ? <FiShield size={14} /> : <FiUser size={14} />}
              </span>
              <span className="hidden max-w-32 truncate lg:block">{user.fullName}</span>
            </button>
            <div className="invisible absolute right-0 mt-1 w-56 rounded-xl border border-neutral-200 bg-neutral-100 p-1.5 opacity-0 shadow-2xl transition group-focus-within:visible group-focus-within:opacity-100 group-hover:visible group-hover:opacity-100">
              <div className="border-b border-neutral-200 px-3 py-2">
                <div className="truncate text-sm font-semibold text-neutral-900">{user.fullName}</div>
                <div className="truncate text-xs text-neutral-500">{user.email}</div>
              </div>
              <Link href="/account" className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-neutral-900 hover:bg-neutral-100 hover:text-neutral-500">
                <FiUser /> Tài khoản của tôi
              </Link>
              <Link href="/orders" className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-neutral-700 hover:bg-neutral-100">
                <FiPackage /> Đơn hàng của tôi
              </Link>
              {isAdmin && (
                <Link href="/admin" className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-neutral-700 hover:bg-neutral-100">
                  <FiShield /> Trang quản trị
                </Link>
              )}
              <button onClick={onLogout} className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-rose-700 hover:bg-rose-50">
                <FiLogOut /> Đăng xuất
              </button>
            </div>
          </div>
        ) : (
          <Button size="sm" onClick={openLogin}>
            Đăng nhập
          </Button>
        )}
      </div>
    </header>
  );
}
