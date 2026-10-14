"use client";

import { useCallback } from "react";
import { STORAGE_KEYS } from "@/config/constants";
import { decodeJwt, hasAdminRole } from "@/helper/jwt";
import { readString, write } from "@/helper/storage";
import { createStore, useHydrated } from "@/helper/store";
import type { User } from "@/interface/user.interface";
import type { RegisterInput } from "@/schemas/auth.schema";
import { authService } from "@/services/auth.service";

const userStore = createStore<User | null>(null, STORAGE_KEYS.user);

/** Modal đăng nhập dùng chung: trang nào cần đăng nhập cũng mở được. */
export const authModalStore = createStore<"login" | "register" | null>(null);

export function clearSession() {
  write(STORAGE_KEYS.accessToken, null);
  write(STORAGE_KEYS.refreshToken, null);
  userStore.set(null);
}

export function useAuth() {
  const user = userStore.use();
  const hydrated = useHydrated();

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await authService.login(email, password);
    write(STORAGE_KEYS.accessToken, tokens.accessToken);
    if (tokens.refreshToken) write(STORAGE_KEYS.refreshToken, tokens.refreshToken);
    try {
      userStore.set(await authService.me());
    } catch {
      // /users/me lỗi thì vẫn dựng profile tối thiểu từ claim của access token
      const claims = decodeJwt(tokens.accessToken);
      userStore.set({
        id: claims?.sub ?? "",
        email: claims?.email ?? email,
        fullName: email.split("@")[0],
        phone: null,
        status: "ACTIVE",
        roles: claims?.roles ?? [],
        createdAt: new Date().toISOString(),
      });
    }
    return userStore.get()!;
  }, []);

  const register = useCallback(
    async (input: RegisterInput) => {
      await authService.register(input);
      return login(input.email, input.password);
    },
    [login],
  );

  const logout = useCallback(async () => {
    const refreshToken = readString(STORAGE_KEYS.refreshToken);
    clearSession();
    if (refreshToken) await authService.logout(refreshToken).catch(() => undefined);
  }, []);

  return {
    user,
    hydrated,
    isAuthenticated: !!user,
    isAdmin: hasAdminRole(user?.roles),
    login,
    register,
    logout,
    openLogin: () => authModalStore.set("login"),
  };
}
