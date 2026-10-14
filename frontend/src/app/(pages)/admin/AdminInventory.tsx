"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState, type FormEvent } from "react";
import { useForm } from "react-hook-form";
import { FiArchive, FiSearch } from "react-icons/fi";
import { toast } from "sonner";
import { Button } from "@/app/components/button/Button";
import { EmptyBlock, ErrorBlock, LoadingBlock } from "@/app/components/card/States";
import { confirmAction } from "@/helper/alert";
import { ApiError, errorMessage } from "@/helper/api";
import { formatCurrency, skuLabel } from "@/helper/format";
import { useAsync } from "@/hooks/useAsync";
import { useProduct, useProducts } from "@/hooks/useProducts";
import type { Inventory } from "@/interface/order.interface";
import { adjustInventorySchema, type AdjustInventoryInput } from "@/schemas/admin.schema";
import { adminService } from "@/services/order.service";

/** 404 = SKU chưa có dòng tồn kho (chưa nhập hàng lần nào) -> hiển thị 0, điều chỉnh sẽ tạo mới. */
const fetchInventory = (sku: string) =>
  adminService.inventory(sku).catch((e) => (e instanceof ApiError && e.status === 404 ? null : Promise.reject(e)));

export function AdminInventory() {
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [productId, setProductId] = useState<string | null>(null);
  const [lookup, setLookup] = useState("");
  const [skuCode, setSkuCode] = useState<string | null>(null);
  const products = useProducts({ q, size: 50 });

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    setQ(search);
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[320px_1fr]">
      <aside className="card h-fit p-4">
        <form onSubmit={onSearch} className="relative mb-3">
          <FiSearch className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-neutral-500" />
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Tìm sản phẩm..." className="input pl-9" />
        </form>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (lookup.trim()) {
              setSkuCode(lookup.trim());
              setProductId(null);
            }
          }}
          className="mb-4 flex gap-2"
        >
          <input value={lookup} onChange={(e) => setLookup(e.target.value)} placeholder="Hoặc nhập mã SKU" className="input font-mono text-xs" />
          <Button type="submit" size="sm" variant="secondary" className="h-[42px]">
            Xem
          </Button>
        </form>

        {products.error && !products.data ? (
          <ErrorBlock error={products.error} onRetry={products.reload} />
        ) : !products.data ? (
          <LoadingBlock />
        ) : (
          <ul className="max-h-[520px] space-y-1 overflow-y-auto pr-1">
            {products.data.items.map((p) => (
              <li key={p.id}>
                <button
                  onClick={() => {
                    setProductId(p.id);
                    setSkuCode(null);
                  }}
                  className={`w-full rounded-lg px-3 py-2 text-left text-sm transition ${
                    productId === p.id ? "bg-neutral-100 text-neutral-900" : "text-neutral-700 hover:bg-neutral-100"
                  }`}
                >
                  <div className="truncate font-medium">{p.name}</div>
                  <div className="text-xs text-neutral-500">{p.category?.name}</div>
                </button>
              </li>
            ))}
            {products.data.items.length === 0 && <li className="px-3 py-6 text-center text-sm text-neutral-500">Không có sản phẩm</li>}
          </ul>
        )}
      </aside>

      <div>
        {skuCode ? (
          <SkuInventoryCard key={skuCode} skuCode={skuCode} label={skuCode} />
        ) : productId ? (
          <ProductInventory key={productId} productId={productId} />
        ) : (
          <EmptyBlock icon={<FiArchive />} title="Chọn sản phẩm để xem tồn kho">
            Tồn kho nằm ở order-service (bảng inventory), tách khỏi catalog của product-service.
          </EmptyBlock>
        )}
      </div>
    </div>
  );
}

function ProductInventory({ productId }: { productId: string }) {
  const product = useProduct(productId);
  if (product.error && !product.data) return <ErrorBlock error={product.error} onRetry={product.reload} />;
  if (!product.data) return <LoadingBlock />;
  const p = product.data;
  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-xl font-bold text-neutral-900">{p.name}</h2>
        <p className="text-sm text-neutral-500">{p.skus.length} phiên bản</p>
      </div>
      {p.skus.map((s) => (
        <SkuInventoryCard key={s.skuCode} skuCode={s.skuCode} label={skuLabel(s.attributes, s.skuCode)} price={s.price} />
      ))}
    </div>
  );
}

function SkuInventoryCard({ skuCode, label, price }: { skuCode: string; label: string; price?: number }) {
  const inv = useAsync(() => fetchInventory(skuCode), [skuCode]);

  return (
    <div className="card p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="font-semibold text-neutral-900">{label}</div>
          <div className="font-mono text-xs text-neutral-500">{skuCode}</div>
          {price !== undefined && <div className="mt-1 text-sm text-neutral-900">{formatCurrency(price)}</div>}
        </div>
        {inv.error && inv.data === undefined ? (
          <span className="text-sm text-rose-700">{errorMessage(inv.error)}</span>
        ) : inv.data === undefined ? (
          <LoadingBlock label="" />
        ) : (
          <Stats inventory={inv.data} />
        )}
      </div>
      {inv.data !== undefined && <AdjustForm skuCode={skuCode} onAdjusted={(i) => inv.setData(i)} />}
    </div>
  );
}

function Stats({ inventory }: { inventory: Inventory | null }) {
  const items = [
    { label: "Còn bán", value: inventory?.availableQty ?? 0, cls: "text-emerald-700" },
    { label: "Đang giữ", value: inventory?.reservedQty ?? 0, cls: "text-amber-700" },
    { label: "Version", value: inventory ? `v${inventory.version}` : "—", cls: "text-neutral-500" },
  ];
  return (
    <div className="flex gap-6">
      {items.map((i) => (
        <div key={i.label} className="text-right">
          <div className="text-[11px] tracking-wide text-neutral-500 uppercase">{i.label}</div>
          <div className={`text-2xl font-bold ${i.cls}`}>{i.value}</div>
        </div>
      ))}
      {!inventory && <div className="self-center text-xs text-neutral-500">Chưa nhập kho</div>}
    </div>
  );
}

function AdjustForm({ skuCode, onAdjusted }: { skuCode: string; onAdjusted: (i: Inventory) => void }) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AdjustInventoryInput>({ resolver: zodResolver(adjustInventorySchema), defaultValues: { reason: "Nhập hàng" } });

  const onSubmit = async (v: AdjustInventoryInput) => {
    const verb = v.delta > 0 ? `nhập thêm ${v.delta}` : `giảm ${-v.delta}`;
    if (!(await confirmAction(`Điều chỉnh ${skuCode}?`, `Sẽ ${verb} sản phẩm. Lý do: ${v.reason}`, "Điều chỉnh"))) return;
    try {
      onAdjusted(await adminService.adjust(skuCode, v.delta, v.reason));
      toast.success(`Đã điều chỉnh tồn kho ${skuCode}`);
      reset({ delta: undefined, reason: v.reason });
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="mt-4 flex flex-wrap items-start gap-3 border-t border-neutral-200 pt-4" noValidate>
      <div className="w-36">
        <input type="number" step={1} placeholder="+10 / -2" className="input" {...register("delta", { valueAsNumber: true })} />
        {errors.delta && <p className="field-error">{errors.delta.message}</p>}
      </div>
      <div className="min-w-48 flex-1">
        <input placeholder="Lý do điều chỉnh" className="input" {...register("reason")} />
        {errors.reason && <p className="field-error">{errors.reason.message}</p>}
      </div>
      <Button type="submit" variant="secondary" loading={isSubmitting} className="h-[42px]">
        Điều chỉnh
      </Button>
    </form>
  );
}
