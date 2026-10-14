"use client";

import { STORAGE_KEYS } from "@/config/constants";
import { createStore } from "@/helper/store";
import type { CartItem } from "@/interface/cart.interface";

const EMPTY: CartItem[] = [];
const cartStore = createStore<CartItem[]>(EMPTY, STORAGE_KEYS.cart);

/** Giới hạn mỗi dòng để tránh gõ nhầm 1000; tồn kho thật do order-service kiểm (409 OUT_OF_STOCK). */
export const MAX_QTY_PER_ITEM = 10;

export function useCart() {
  const items = cartStore.use();

  return {
    items,
    totalCount: items.reduce((n, i) => n + i.quantity, 0),
    totalAmount: items.reduce((n, i) => n + i.price * i.quantity, 0),

    add(item: Omit<CartItem, "quantity">, quantity = 1) {
      cartStore.set((prev) => {
        const found = prev.find((i) => i.skuCode === item.skuCode);
        if (!found) return [...prev, { ...item, quantity: Math.min(quantity, MAX_QTY_PER_ITEM) }];
        return prev.map((i) =>
          i.skuCode === item.skuCode ? { ...i, quantity: Math.min(i.quantity + quantity, MAX_QTY_PER_ITEM) } : i,
        );
      });
    },

    setQuantity(skuCode: string, quantity: number) {
      cartStore.set((prev) =>
        prev
          .map((i) => (i.skuCode === skuCode ? { ...i, quantity: Math.min(quantity, MAX_QTY_PER_ITEM) } : i))
          .filter((i) => i.quantity > 0),
      );
    },

    remove(skuCode: string) {
      cartStore.set((prev) => prev.filter((i) => i.skuCode !== skuCode));
    },

    clear() {
      cartStore.set(EMPTY);
    },
  };
}
