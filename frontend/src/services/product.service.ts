import { api } from "@/helper/api";
import type { PageData } from "@/interface/api.interface";
import type { ProductDetail, ProductSku, ProductSummary } from "@/interface/product.interface";

export interface ProductQuery {
  q?: string;
  categoryId?: string;
  page?: number;
  size?: number;
}

export const productService = {
  /** Có q: tìm toàn văn (tsvector, không phân biệt dấu) sắp theo độ liên quan. */
  list: ({ q, categoryId, page = 0, size = 12 }: ProductQuery = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (q?.trim()) params.set("q", q.trim());
    if (categoryId) params.set("categoryId", categoryId);
    return api<PageData<ProductSummary>>(`/products?${params}`, { auth: false });
  },

  detail: (id: string) => api<ProductDetail>(`/products/${id}`, { auth: false }),

  /** Admin: tải ảnh JPEG/PNG/WebP (tối đa 2 MB) cho một SKU. */
  uploadSkuImage: (skuCode: string, file: File) => {
    const form = new FormData();
    form.append("file", file);
    return api<ProductSku>(`/admin/products/skus/${encodeURIComponent(skuCode)}/image`, { method: "POST", body: form });
  },
};
