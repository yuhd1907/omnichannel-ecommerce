// product-service: ProductSummaryDto, ProductDetailDto, ProductSkuDto

export interface CategorySummary {
  id: string;
  name: string;
  slug: string;
}

export interface BrandSummary {
  id: string;
  name: string;
  slug: string;
}

export interface ProductSummary {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  category: CategorySummary | null;
  brand: BrandSummary | null;
  basePrice: number;
  /** Ảnh đại diện (tự lấy từ ảnh SKU đầu tiên được tải lên); null nếu chưa có */
  imageUrl: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProductSku {
  id: string;
  skuCode: string;
  attributes: Record<string, unknown> | null;
  price: number;
  imageUrl: string | null;
  updatedAt: string;
}

export interface ProductDetail extends ProductSummary {
  skus: ProductSku[];
}
