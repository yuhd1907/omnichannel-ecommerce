"use client";

import { useAsync } from "@/hooks/useAsync";
import { productService, type ProductQuery } from "@/services/product.service";

export function useProducts(query: ProductQuery) {
  return useAsync(() => productService.list(query), [query.q ?? "", query.categoryId ?? "", query.page ?? 0, query.size ?? 12]);
}

export function useProduct(id: string) {
  return useAsync(() => productService.detail(id), [id]);
}
