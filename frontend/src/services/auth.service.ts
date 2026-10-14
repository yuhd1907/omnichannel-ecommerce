import { api } from "@/helper/api";
import type { Address, AuthTokens, User } from "@/interface/user.interface";
import type { AddressInput, RegisterInput } from "@/schemas/auth.schema";

export const authService = {
  login: (email: string, password: string) =>
    api<AuthTokens>("/auth/login", { method: "POST", body: { email, password }, auth: false }),

  register: (input: RegisterInput) =>
    api<User>("/auth/register", {
      method: "POST",
      body: { email: input.email, password: input.password, fullName: input.fullName, phone: input.phone || null },
      auth: false,
    }),

  logout: (refreshToken: string) => api<void>("/auth/logout", { method: "POST", body: { refreshToken }, auth: false }),

  me: () => api<User>("/users/me"),

  addresses: () => api<Address[]>("/users/me/addresses"),

  createAddress: (input: AddressInput) => api<Address>("/users/me/addresses", { method: "POST", body: input }),
};
