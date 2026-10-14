"use client";

import { useCallback, useEffect, useState, type DependencyList } from "react";

interface State<T> {
  key?: string;
  data?: T;
  error?: unknown;
}

/**
 * Tải dữ liệu theo deps. Giữ data cũ trong lúc tải lại (polling không nháy màn hình);
 * kết quả của request cũ về muộn bị bỏ qua (đổi trang/từ khóa nhanh không hiện nhầm dữ liệu).
 */
export function useAsync<T>(fn: () => Promise<T>, deps: DependencyList, enabled = true) {
  const [tick, setTick] = useState(0);
  const [state, setState] = useState<State<T>>({});
  const key = JSON.stringify([...deps, tick]);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    fn().then(
      (data) => !cancelled && setState({ key, data }),
      (error) => !cancelled && setState((s) => ({ key, data: s.data, error })),
    );
    return () => {
      cancelled = true;
    };
    // fn đổi mỗi lần render; deps (qua key) mới là thứ quyết định tải lại
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, enabled]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  const setData = useCallback((data: T) => setState((s) => ({ ...s, data })), []);

  return {
    data: state.data,
    error: state.key === key ? state.error : undefined,
    loading: enabled && state.key !== key,
    reload,
    setData,
  };
}
