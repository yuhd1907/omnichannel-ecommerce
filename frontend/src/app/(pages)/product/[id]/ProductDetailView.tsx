"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { FiArrowLeft, FiMinus, FiPlus, FiShoppingCart, FiZap } from "react-icons/fi";
import { toast } from "sonner";
import { Button, choiceClass } from "@/app/components/button/Button";
import { ProductArt } from "@/app/components/card/ProductArt";
import { ErrorBlock, LoadingBlock } from "@/app/components/card/States";
import { formatCurrency, skuLabel } from "@/helper/format";
import { MAX_QTY_PER_ITEM, useCart } from "@/hooks/useCart";
import { useProduct } from "@/hooks/useProducts";
import type { ProductDetail, ProductSku } from "@/interface/product.interface";

export function ProductDetailView({ id }: { id: string }) {
  const { data: product, error, reload } = useProduct(id);

  if (error && !product) return <ErrorBlock error={error} onRetry={reload} />;
  if (!product) return <LoadingBlock label="Đang tải sản phẩm..." />;
  return <Detail key={product.id} product={product} />;
}

function Detail({ product }: { product: ProductDetail }) {
  const router = useRouter();
  const cart = useCart();
  const [skuCode, setSkuCode] = useState(product.skus[0]?.skuCode ?? "");
  const [qty, setQty] = useState(1);
  const sku: ProductSku | undefined = product.skus.find((s) => s.skuCode === skuCode);

  const addToCart = () => {
    if (!sku) return;
    cart.add(
      {
        productId: product.id,
        productName: product.name,
        skuCode: sku.skuCode,
        skuLabel: skuLabel(sku.attributes, sku.skuCode),
        price: sku.price,
        imageUrl: sku.imageUrl,
      },
      qty,
    );
  };

  return (
    <div>
      <Link href="/" className="mb-6 inline-flex items-center gap-2 text-sm text-neutral-900 hover:text-neutral-500">
        <FiArrowLeft /> Quay lại cửa hàng
      </Link>

      <div className="grid gap-8 lg:grid-cols-2">
        <ProductArt name={product.name} seed={product.id} imageUrl={sku?.imageUrl ?? product.imageUrl} className="card aspect-square" />

        <div className="flex flex-col">
          <div className="text-xs font-semibold tracking-wider text-neutral-500 uppercase">
            {[product.category?.name, product.brand?.name].filter(Boolean).join(" · ")}
          </div>
          <h1 className="mt-2 text-3xl font-black tracking-tight text-neutral-900">{product.name}</h1>
          <div className="mt-4 text-3xl font-bold text-neutral-900">{formatCurrency(sku?.price ?? product.basePrice)}</div>
          {product.description && <p className="mt-5 leading-relaxed text-neutral-500">{product.description}</p>}

          {product.skus.length === 0 ? (
            <p className="mt-8 rounded-xl bg-amber-50 p-4 text-sm text-amber-700">Sản phẩm chưa có phiên bản nào để bán.</p>
          ) : (
            <>
              <div className="mt-8">
                <div className="label">Phiên bản</div>
                <div className="flex flex-wrap gap-2">
                  {product.skus.map((s) => (
                    <button
                      key={s.skuCode}
                      onClick={() => setSkuCode(s.skuCode)}
                      className={`px-4 py-2.5 text-sm text-neutral-900 ${choiceClass(s.skuCode === skuCode)}`}
                    >
                      <div className="font-semibold">{skuLabel(s.attributes, s.skuCode)}</div>
                      <div className="text-xs text-neutral-500">{formatCurrency(s.price)}</div>
                    </button>
                  ))}
                </div>
                {sku && <div className="mt-2 font-mono text-xs text-neutral-500">SKU: {sku.skuCode}</div>}
              </div>

              <div className="mt-6">
                <div className="label">Số lượng</div>
                <div className="inline-flex items-center rounded-xl border border-neutral-300 bg-white">
                  <button className="p-3 text-neutral-900 hover:text-neutral-500 disabled:opacity-40" disabled={qty <= 1} onClick={() => setQty(qty - 1)} aria-label="Giảm">
                    <FiMinus />
                  </button>
                  <span className="w-10 text-center font-semibold">{qty}</span>
                  <button
                    className="p-3 text-neutral-900 hover:text-neutral-500 disabled:opacity-40"
                    disabled={qty >= MAX_QTY_PER_ITEM}
                    onClick={() => setQty(qty + 1)}
                    aria-label="Tăng"
                  >
                    <FiPlus />
                  </button>
                </div>
              </div>

              <div className="mt-8 flex flex-wrap gap-3">
                <Button
                  size="lg"
                  variant="secondary"
                  icon={<FiShoppingCart />}
                  onClick={() => {
                    addToCart();
                    toast.success(`Đã thêm ${product.name} vào giỏ`, { action: { label: "Xem giỏ", onClick: () => router.push("/cart") } });
                  }}
                >
                  Thêm vào giỏ
                </Button>
                <Button
                  size="lg"
                  icon={<FiZap />}
                  onClick={() => {
                    addToCart();
                    router.push("/checkout");
                  }}
                >
                  Mua ngay
                </Button>
              </div>
              <p className="mt-4 text-xs text-neutral-500">Tồn kho được kiểm và giữ chỗ khi đặt hàng; hết hàng sẽ được báo ngay.</p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
