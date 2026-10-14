"use client";

import Link from "next/link";
import { FiMinus, FiPlus, FiShoppingCart, FiTrash2 } from "react-icons/fi";
import { LinkButton } from "@/app/components/button/Button";
import { ProductArt } from "@/app/components/card/ProductArt";
import { EmptyBlock, LoadingBlock, PageTitle } from "@/app/components/card/States";
import { formatCurrency } from "@/helper/format";
import { useHydrated } from "@/helper/store";
import { MAX_QTY_PER_ITEM, useCart } from "@/hooks/useCart";

export default function CartPage() {
  const hydrated = useHydrated();
  const { items, totalAmount, totalCount, setQuantity, remove } = useCart();

  if (!hydrated) return <LoadingBlock />;

  if (items.length === 0) {
    return (
      <EmptyBlock icon={<FiShoppingCart />} title="Giỏ hàng trống">
        <LinkButton href="/" className="mt-2">
          Tiếp tục mua sắm
        </LinkButton>
      </EmptyBlock>
    );
  }

  return (
    <div>
      <PageTitle title="Giỏ hàng" subtitle={`${totalCount} sản phẩm`} />
      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="space-y-3">
          {items.map((item) => (
            <div key={item.skuCode} className="card flex items-center gap-4 p-4">
              <ProductArt name={item.productName} seed={item.productId} imageUrl={item.imageUrl} className="h-20 w-20 shrink-0 rounded-xl" />
              <div className="min-w-0 flex-1">
                <Link href={`/product/${item.productId}`} className="block truncate font-semibold text-neutral-900 hover:text-neutral-500">
                  {item.productName}
                </Link>
                <div className="text-sm text-neutral-500">{item.skuLabel}</div>
                <div className="font-mono text-xs text-neutral-400">{item.skuCode}</div>
              </div>
              <div className="inline-flex items-center rounded-lg border border-neutral-300 bg-white">
                <button className="p-2 text-neutral-900 hover:text-neutral-500" onClick={() => setQuantity(item.skuCode, item.quantity - 1)} aria-label="Giảm">
                  <FiMinus size={14} />
                </button>
                <span className="w-8 text-center text-sm font-semibold">{item.quantity}</span>
                <button
                  className="p-2 text-neutral-900 hover:text-neutral-500 disabled:opacity-40"
                  disabled={item.quantity >= MAX_QTY_PER_ITEM}
                  onClick={() => setQuantity(item.skuCode, item.quantity + 1)}
                  aria-label="Tăng"
                >
                  <FiPlus size={14} />
                </button>
              </div>
              <div className="w-32 text-right font-semibold text-neutral-900">{formatCurrency(item.price * item.quantity)}</div>
              <button onClick={() => remove(item.skuCode)} className="p-2 text-neutral-500 hover:text-rose-600" aria-label="Xóa">
                <FiTrash2 />
              </button>
            </div>
          ))}
        </div>

        <aside className="card h-fit p-6 lg:sticky lg:top-24">
          <div className="flex justify-between text-sm text-neutral-500">
            <span>Tạm tính</span>
            <span>{formatCurrency(totalAmount)}</span>
          </div>
          <div className="mt-2 flex justify-between text-sm text-neutral-500">
            <span>Phí vận chuyển</span>
            <span className="text-emerald-600">Miễn phí</span>
          </div>
          <div className="mt-4 flex justify-between border-t border-neutral-200 pt-4 text-lg font-bold text-neutral-900">
            <span>Tổng cộng</span>
            <span className="text-neutral-900">{formatCurrency(totalAmount)}</span>
          </div>
          <p className="mt-1 text-xs text-neutral-500">Giá cuối cùng do server tính lại khi đặt hàng.</p>
          <LinkButton href="/checkout" size="lg" className="mt-6 w-full">
            Tiến hành đặt hàng
          </LinkButton>
        </aside>
      </div>
    </div>
  );
}
