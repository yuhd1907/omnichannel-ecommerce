import type { ReactNode } from "react";
import { Spinner } from "@/app/components/button/Button";
import { errorMessage } from "@/helper/api";

export function LoadingBlock({ label = "Đang tải..." }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-20 text-neutral-500">
      <Spinner className="h-5 w-5 text-neutral-900" />
      <span className="text-sm">{label}</span>
    </div>
  );
}

export function ErrorBlock({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="card mx-auto max-w-lg p-8 text-center">
      <div className="text-lg font-semibold text-rose-700">Không tải được dữ liệu</div>
      <p className="mt-2 text-sm text-neutral-500">{errorMessage(error)}</p>
      {onRetry && (
        <button onClick={onRetry} className="mt-5 text-sm font-semibold text-neutral-900 hover:text-neutral-500">
          Thử lại
        </button>
      )}
    </div>
  );
}

export function EmptyBlock({ icon, title, children }: { icon: ReactNode; title: string; children?: ReactNode }) {
  return (
    <div className="card mx-auto flex max-w-lg flex-col items-center p-10 text-center">
      <div className="mb-4 text-4xl text-neutral-400">{icon}</div>
      <div className="text-lg font-semibold text-neutral-800">{title}</div>
      {children && <div className="mt-3 text-sm text-neutral-500">{children}</div>}
    </div>
  );
}

export function PageTitle({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-neutral-900 sm:text-3xl">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-neutral-500">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}
