import { api } from "@/helper/api";
import type { PageData } from "@/interface/api.interface";
import type { BrandSummary, CategorySummary, ProductDetail, ProductSku, ProductSummary } from "@/interface/product.interface";

export type ProductStatus = "ACTIVE" | "INACTIVE";

export interface ProductInput {
  name: string;
  slug?: string;
  description?: string;
  categoryId: string;
  brandId: string;
  basePrice: number;
  status: ProductStatus;
}

export interface SkuInput {
  skuCode: string;
  price: number;
  attributes: Record<string, string>;
}

/** API quản trị sản phẩm (product-service, cần role ADMIN) + danh mục / thương hiệu công khai. */
export const productAdminService = {
  categories: () => api<CategorySummary[]>("/products/categories", { auth: false }),
  brands: () => api<BrandSummary[]>("/products/brands", { auth: false }),

  /** Mọi trạng thái (cả sản phẩm đã ngừng bán), mới cập nhật trước. */
  list: (q: string, status: ProductStatus | "", page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (q.trim()) params.set("q", q.trim());
    if (status) params.set("status", status);
    return api<PageData<ProductSummary>>(`/admin/products?${params}`);
  },

  get: (id: string) => api<ProductDetail>(`/admin/products/${id}`),

  /** Tạo sản phẩm kèm ít nhất 1 SKU; SKU mới tự có dòng tồn kho = 0 ở order-service (event product.sku.created). */
  create: (input: ProductInput, skus: SkuInput[]) =>
    api<ProductDetail>("/admin/products", {
      method: "POST",
      body: { ...input, slug: input.slug || null, skus: skus.map((s) => ({ ...s, imageUrl: null })) },
    }),

  update: (id: string, input: Omit<ProductInput, "slug">) => api<ProductDetail>(`/admin/products/${id}`, { method: "PATCH", body: input }),

  /** "Xóa" = ngừng bán (status INACTIVE): giữ lịch sử đơn hàng, có thể mở bán lại. */
  setStatus: (id: string, status: ProductStatus) => api<ProductDetail>(`/admin/products/${id}`, { method: "PATCH", body: { status } }),

  addSku: (productId: string, sku: SkuInput) =>
    api<ProductSku>(`/admin/products/${productId}/skus`, { method: "POST", body: { ...sku, imageUrl: null } }),

  updateSku: (skuCode: string, price: number, attributes: Record<string, string>) =>
    api<ProductSku>(`/admin/products/skus/${encodeURIComponent(skuCode)}`, { method: "PATCH", body: { price, attributes } }),

  deleteSku: (skuCode: string) => api<void>(`/admin/products/skus/${encodeURIComponent(skuCode)}`, { method: "DELETE" }),
};

/** "Màu: Đen; Dung lượng: 256GB" <-> { Màu: "Đen", "Dung lượng": "256GB" } */
export function parseAttributes(text: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const part of text.split(";")) {
    const i = part.indexOf(":");
    if (i <= 0) continue;
    const key = part.slice(0, i).trim();
    const value = part.slice(i + 1).trim();
    if (key && value) out[key] = value;
  }
  return out;
}

export function formatAttributes(attrs: Record<string, unknown> | null | undefined): string {
  return Object.entries(attrs ?? {})
    .map(([k, v]) => `${k}: ${String(v)}`)
    .join("; ");
}
