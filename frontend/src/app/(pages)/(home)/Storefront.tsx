"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { FiChevronLeft, FiChevronRight, FiSearch, FiZap } from "react-icons/fi";
import { LogoMark } from "@/app/components/header/Logo";
import { ProductCard } from "@/app/components/card/ProductCard";
import { EmptyBlock, ErrorBlock, LoadingBlock } from "@/app/components/card/States";
import { useProducts } from "@/hooks/useProducts";
import { useAsync } from "@/hooks/useAsync";
import { productAdminService } from "@/services/product-admin.service";

const PAGE_SIZE = 12;

export function Storefront() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const q = params.get("q") ?? "";
  const categoryId = params.get("categoryId") ?? "";
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0);

  const { data, error, loading, reload } = useProducts({ q, categoryId, page, size: PAGE_SIZE });

  const categories = useAsync(() => productAdminService.categories(), []).data ?? [];

  const go = (next: Record<string, string | number | null>) => {
    const sp = new URLSearchParams(params.toString());
    for (const [k, v] of Object.entries(next)) {
      if (v === null || v === "" || v === 0) sp.delete(k);
      else sp.set(k, String(v));
    }
    router.push(sp.size ? `${pathname}?${sp}` : pathname, { scroll: false });
  };

  return (
    <div>
      {!q && page === 0 && !categoryId && (
        <section className="relative mb-10 overflow-hidden rounded-3xl bg-neutral-50 p-8 ring-1 ring-neutral-200 sm:p-14">
          <LogoMark size={260} className="pointer-events-none absolute -right-10 -bottom-16 hidden text-neutral-200/70 md:block" />
          <div className="relative max-w-2xl">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-neutral-900 px-3 py-1 text-xs font-semibold text-white">
              <FiZap /> Flash sale chống bán vượt kho
            </span>
            <h1 className="mt-5 text-4xl leading-[1.1] font-extrabold tracking-[-0.03em] text-neutral-900 sm:text-6xl">
              Mua sắm đa kênh,
              <br />
              <span className="text-neutral-400">tồn kho luôn chính xác.</span>
            </h1>
            <p className="mt-4 text-neutral-500">
              Đơn hàng giữ kho bằng Redisson lock + UPDATE có điều kiện, thanh toán qua Saga (RabbitMQ + Outbox), tự hủy và trả hàng
              sau 15 phút nếu chưa thanh toán.
            </p>
          </div>
        </section>
      )}

      <div className="mb-6 flex flex-wrap items-center gap-2">
        <Chip active={!categoryId} onClick={() => go({ categoryId: null, page: null })}>
          Tất cả
        </Chip>
        {categories.map((c) => (
          <Chip key={c.id} active={categoryId === c.id} onClick={() => go({ categoryId: c.id, page: null })}>
            {c.name}
          </Chip>
        ))}
        {q && (
          <span className="ml-auto flex items-center gap-2 text-sm text-neutral-500">
            <FiSearch /> Kết quả cho <b className="text-neutral-800">“{q}”</b>
            <button onClick={() => go({ q: null, page: null })} className="text-neutral-900 hover:text-neutral-500">
              Xóa
            </button>
          </span>
        )}
      </div>

      {error && !data ? (
        <ErrorBlock error={error} onRetry={reload} />
      ) : !data ? (
        <LoadingBlock label="Đang tải sản phẩm..." />
      ) : data.items.length === 0 ? (
        <EmptyBlock icon={<FiSearch />} title="Không tìm thấy sản phẩm">
          Thử từ khóa khác — tìm kiếm không phân biệt dấu, gõ dở vẫn khớp.
        </EmptyBlock>
      ) : (
        <>
          <div className={`grid grid-cols-1 gap-5 transition sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 ${loading ? "opacity-60" : ""}`}>
            {data.items.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>

          {data.totalPages > 1 && (
            <div className="mt-8 flex items-center justify-center gap-3 text-sm">
              <PageButton disabled={page === 0} onClick={() => go({ page: page - 1 })} label="Trang trước">
                <FiChevronLeft />
              </PageButton>
              <span className="text-neutral-500">
                Trang <b className="text-neutral-800">{page + 1}</b> / {data.totalPages} · {data.totalElements} sản phẩm
              </span>
              <PageButton disabled={page + 1 >= data.totalPages} onClick={() => go({ page: page + 1 })} label="Trang sau">
                <FiChevronRight />
              </PageButton>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
        active ? "border border-neutral-900 bg-neutral-900 text-white" : "border border-neutral-300 bg-white text-neutral-900 hover:border-neutral-400 active:shadow-[0_8px_18px_-6px_rgba(0,0,0,0.28)]"
      }`}
    >
      {children}
    </button>
  );
}

function PageButton({ disabled, onClick, label, children }: { disabled: boolean; onClick: () => void; label: string; children: React.ReactNode }) {
  return (
    <button
      disabled={disabled}
      onClick={onClick}
      aria-label={label}
      className="flex h-9 w-9 items-center justify-center rounded-lg border border-neutral-300 bg-white text-neutral-900 hover:border-neutral-400 active:shadow-[0_8px_18px_-6px_rgba(0,0,0,0.28)] disabled:opacity-40 disabled:shadow-none"
    >
      {children}
    </button>
  );
}
