"use client";

import { useState, type FormEvent } from "react";
import { FiChevronLeft, FiChevronRight, FiEdit2, FiEyeOff, FiPackage, FiPlus, FiRotateCcw, FiSearch } from "react-icons/fi";
import { toast } from "sonner";
import { Button, LinkButton } from "@/app/components/button/Button";
import { ProductArt } from "@/app/components/card/ProductArt";
import { EmptyBlock, ErrorBlock, LoadingBlock } from "@/app/components/card/States";
import { confirmAction } from "@/helper/alert";
import { errorMessage } from "@/helper/api";
import { formatCurrency, formatDateTime } from "@/helper/format";
import { useAsync } from "@/hooks/useAsync";
import type { ProductSummary } from "@/interface/product.interface";
import { productAdminService, type ProductStatus } from "@/services/product-admin.service";

const FILTERS: { id: ProductStatus | ""; label: string }[] = [
  { id: "", label: "Tất cả" },
  { id: "ACTIVE", label: "Đang bán" },
  { id: "INACTIVE", label: "Ngừng bán" },
];

const CHIP_OFF =
  "border border-neutral-300 bg-white text-neutral-900 hover:border-neutral-400 active:shadow-[0_8px_18px_-6px_rgba(0,0,0,0.28)]";

export function AdminProducts() {
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<ProductStatus | "">("");
  const [page, setPage] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);
  const { data, error, loading, reload } = useAsync(() => productAdminService.list(q, status, page), [q, status, page]);

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    setPage(0);
    setQ(search);
  };

  /** "Xóa" = ngừng bán: ẩn khỏi cửa hàng, không đặt được nữa, đơn cũ giữ nguyên; mở bán lại được. */
  const toggle = async (p: ProductSummary) => {
    const stop = p.status === "ACTIVE";
    const ok = await confirmAction(
      stop ? `Ngừng bán "${p.name}"?` : `Mở bán lại "${p.name}"?`,
      stop
        ? "Sản phẩm bị ẩn khỏi cửa hàng và không đặt được nữa. Đơn hàng cũ giữ nguyên; có thể mở bán lại sau."
        : "Sản phẩm hiện lại trên cửa hàng.",
      stop ? "Ngừng bán" : "Mở bán",
      stop,
    );
    if (!ok) return;
    setBusyId(p.id);
    try {
      await productAdminService.setStatus(p.id, stop ? "INACTIVE" : "ACTIVE");
      toast.success(stop ? "Đã ngừng bán sản phẩm" : "Đã mở bán lại sản phẩm");
      reload();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <form onSubmit={onSearch} className="relative w-full max-w-xs">
          <FiSearch className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-neutral-400" />
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Tìm theo tên..." className="input h-10 py-0 pl-9" />
        </form>
        {FILTERS.map((f) => (
          <button
            key={f.id || "ALL"}
            onClick={() => {
              setStatus(f.id);
              setPage(0);
            }}
            className={`rounded-full px-3.5 py-1.5 text-xs font-semibold transition ${
              status === f.id ? "border border-neutral-900 bg-neutral-900 text-white" : CHIP_OFF
            }`}
          >
            {f.label}
          </button>
        ))}
        <LinkButton href="/admin/products/new" size="sm" className="ml-auto" icon={<FiPlus />}>
          Thêm sản phẩm
        </LinkButton>
      </div>

      {error && !data ? (
        <ErrorBlock error={error} onRetry={reload} />
      ) : !data ? (
        <LoadingBlock label="Đang tải sản phẩm..." />
      ) : data.items.length === 0 ? (
        <EmptyBlock icon={<FiPackage />} title="Không có sản phẩm nào" />
      ) : (
        <>
          <div className={`card overflow-x-auto transition ${loading ? "opacity-60" : ""}`}>
            <table className="w-full min-w-[760px] text-sm">
              <thead className="border-b border-neutral-200 text-left text-xs tracking-wide text-neutral-500 uppercase">
                <tr>
                  <th className="px-5 py-3 font-medium">Sản phẩm</th>
                  <th className="px-5 py-3 font-medium">Danh mục · Thương hiệu</th>
                  <th className="px-5 py-3 text-right font-medium">Giá từ</th>
                  <th className="px-5 py-3 font-medium">Trạng thái</th>
                  <th className="px-5 py-3 font-medium">Cập nhật</th>
                  <th className="px-5 py-3 text-right font-medium">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-200">
                {data.items.map((p) => (
                  <tr key={p.id} className="hover:bg-neutral-50">
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-3">
                        <ProductArt name={p.name} seed={p.id} imageUrl={p.imageUrl} className="h-12 w-12 shrink-0 rounded-lg border border-neutral-200" />
                        <div className="min-w-0">
                          <div className="truncate font-semibold text-neutral-900">{p.name}</div>
                          <div className="truncate font-mono text-xs text-neutral-500">{p.slug}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-3 text-neutral-500">{[p.category?.name, p.brand?.name].filter(Boolean).join(" · ")}</td>
                    <td className="px-5 py-3 text-right font-semibold text-neutral-900">{formatCurrency(p.basePrice)}</td>
                    <td className="px-5 py-3">
                      <span
                        className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${
                          p.status === "ACTIVE" ? "bg-emerald-50 text-emerald-700 ring-emerald-200" : "bg-neutral-100 text-neutral-500 ring-neutral-200"
                        }`}
                      >
                        {p.status === "ACTIVE" ? "Đang bán" : "Ngừng bán"}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-neutral-500">{formatDateTime(p.updatedAt)}</td>
                    <td className="px-5 py-3">
                      <div className="flex justify-end gap-2">
                        <LinkButton href={`/admin/products/${p.id}`} variant="secondary" size="sm" icon={<FiEdit2 />}>
                          Sửa
                        </LinkButton>
                        <Button
                          variant={p.status === "ACTIVE" ? "danger" : "secondary"}
                          size="sm"
                          loading={busyId === p.id}
                          disabled={!!busyId}
                          icon={p.status === "ACTIVE" ? <FiEyeOff /> : <FiRotateCcw />}
                          onClick={() => toggle(p)}
                        >
                          {p.status === "ACTIVE" ? "Ngừng bán" : "Mở bán"}
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {data.totalPages > 1 && (
            <div className="mt-5 flex items-center justify-center gap-3 text-sm text-neutral-500">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)} icon={<FiChevronLeft />}>
                Trước
              </Button>
              <span>
                Trang {page + 1} / {data.totalPages} · {data.totalElements} sản phẩm
              </span>
              <Button variant="secondary" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
                Sau <FiChevronRight />
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
