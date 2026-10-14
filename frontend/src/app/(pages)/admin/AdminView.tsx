"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { FiArchive, FiBox, FiLock, FiTruck } from "react-icons/fi";
import { Button } from "@/app/components/button/Button";
import { EmptyBlock, LoadingBlock, PageTitle } from "@/app/components/card/States";
import { useAuth } from "@/hooks/useAuth";
import { AdminInventory } from "./AdminInventory";
import { AdminOrders } from "./AdminOrders";
import { AdminProducts } from "./AdminProducts";

type Tab = "orders" | "products" | "inventory";
const TABS: Tab[] = ["orders", "products", "inventory"];

export function AdminView() {
  const { user, isAdmin, hydrated, openLogin } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  // Tab nằm trên URL (?tab=products) -> quay lại từ trang sửa sản phẩm đúng tab
  const raw = params.get("tab");
  const tab: Tab = TABS.includes(raw as Tab) ? (raw as Tab) : "orders";
  const setTab = (t: Tab) => router.replace(t === "orders" ? pathname : `${pathname}?tab=${t}`, { scroll: false });

  if (!hydrated) return <LoadingBlock />;
  if (!user) {
    return (
      <EmptyBlock icon={<FiLock />} title="Đăng nhập bằng tài khoản quản trị">
        <Button className="mt-2" onClick={openLogin}>
          Đăng nhập
        </Button>
      </EmptyBlock>
    );
  }
  if (!isAdmin) {
    return (
      <EmptyBlock icon={<FiLock />} title="Bạn không có quyền truy cập">
        Trang này chỉ dành cho tài khoản có role ADMIN. API admin cũng trả 403 cho user thường.
      </EmptyBlock>
    );
  }

  return (
    <div>
      <PageTitle title="Quản trị" subtitle="Duyệt giao hàng, quản lý sản phẩm và tồn kho" />
      <div className="mb-6 inline-flex rounded-xl bg-neutral-100 p-1 ring-1 ring-neutral-200">
        {(
          [
            { id: "orders", label: "Đơn hàng", icon: <FiTruck /> },
            { id: "products", label: "Sản phẩm", icon: <FiBox /> },
            { id: "inventory", label: "Tồn kho", icon: <FiArchive /> },
          ] as const
        ).map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition ${
              tab === t.id ? "bg-neutral-900 text-white" : "text-neutral-900 hover:text-neutral-500"
            }`}
          >
            {t.icon} {t.label}
          </button>
        ))}
      </div>
      {tab === "orders" ? <AdminOrders /> : tab === "products" ? <AdminProducts /> : <AdminInventory />}
    </div>
  );
}
