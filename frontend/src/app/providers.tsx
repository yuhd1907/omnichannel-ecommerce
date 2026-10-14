"use client";

import { useEffect, type ReactNode } from "react";
import { Toaster, toast } from "sonner";
import { AuthModal } from "@/app/components/modal/AuthModal";
import { SESSION_EXPIRED_EVENT } from "@/helper/api";
import { authModalStore, clearSession } from "@/hooks/useAuth";

export function Providers({ children }: { children: ReactNode }) {
  useEffect(() => {
    const onExpired = () => {
      clearSession();
      toast.warning("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
      authModalStore.set("login");
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  return (
    <>
      {children}
      <AuthModal />
      {/* offset top: nằm dưới header sticky (h-16) */}
      <Toaster theme="light" position="top-right" richColors closeButton offset={{ top: 76, right: 16 }} />
    </>
  );
}
