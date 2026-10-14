import Link from "next/link";
import { formatCurrency } from "@/helper/format";
import type { ProductSummary } from "@/interface/product.interface";
import { ProductArt } from "@/app/components/card/ProductArt";

export function ProductCard({ product }: { product: ProductSummary }) {
  return (
    <Link
      href={`/product/${product.id}`}
      className="card group flex flex-col overflow-hidden transition hover:-translate-y-0.5 hover:border-neutral-400"
    >
      <ProductArt name={product.name} seed={product.id} imageUrl={product.imageUrl} className="aspect-[4/3]" />
      <div className="flex flex-1 flex-col gap-2 p-4">
        <div className="flex items-center gap-2 text-[11px] font-medium tracking-wide text-neutral-500 uppercase">
          {product.category && <span>{product.category.name}</span>}
          {product.brand && (
            <>
              <span className="text-neutral-300">•</span>
              <span>{product.brand.name}</span>
            </>
          )}
        </div>
        <h3 className="line-clamp-2 font-semibold text-neutral-900 group-hover:text-neutral-500">{product.name}</h3>
        {product.description && <p className="line-clamp-2 text-sm text-neutral-500">{product.description}</p>}
        <div className="mt-auto flex items-end justify-between pt-2">
          <div>
            <div className="text-[11px] text-neutral-500">Giá từ</div>
            <div className="text-lg font-bold text-neutral-900">{formatCurrency(product.basePrice)}</div>
          </div>
          <span className="text-xs font-semibold text-neutral-500 group-hover:text-neutral-500">Xem chi tiết →</span>
        </div>
      </div>
    </Link>
  );
}
