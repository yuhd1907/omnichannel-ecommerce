import { useSyncExternalStore } from "react";
import { readJson, write } from "@/helper/storage";

export interface Store<T> {
  get(): T;
  set(next: T | ((prev: T) => T)): void;
  use(): T;
}

/**
 * Store nhỏ đọc bằng useSyncExternalStore. Có `persistKey` thì đồng bộ localStorage (kể cả giữa các tab).
 * Server render dùng `initial` -> client hydrate xong mới đọc localStorage, không lệch HTML.
 */
export function createStore<T>(initial: T, persistKey?: string): Store<T> {
  let value: T | undefined = persistKey ? undefined : initial;
  const listeners = new Set<() => void>();

  const get = () => {
    if (value === undefined) value = readJson<T>(persistKey!, initial);
    return value;
  };

  const set = (next: T | ((prev: T) => T)) => {
    value = typeof next === "function" ? (next as (prev: T) => T)(get()) : next;
    if (persistKey) write(persistKey, value as object | null);
    listeners.forEach((l) => l());
  };

  const subscribe = (listener: () => void) => {
    listeners.add(listener);
    const onStorage = (e: StorageEvent) => {
      if (persistKey && e.key === persistKey) {
        value = undefined;
        listener();
      }
    };
    window.addEventListener("storage", onStorage);
    return () => {
      listeners.delete(listener);
      window.removeEventListener("storage", onStorage);
    };
  };

  const getServerSnapshot = () => initial;

  return {
    get,
    set,
    // Hook gắn với store: chỉ gọi trong component
    use:() => useSyncExternalStore(subscribe, get, getServerSnapshot),
  };
}

/** true sau khi client hydrate xong (dùng để tránh nháy "chưa đăng nhập" khi tải trang). */
const noopSubscribe = () => () => {};
export function useHydrated(): boolean {
  return useSyncExternalStore(noopSubscribe, () => true, () => false);
}
