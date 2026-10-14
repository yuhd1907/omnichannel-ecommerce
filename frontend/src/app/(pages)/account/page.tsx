"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { FiLock, FiMapPin, FiPlus, FiUser } from "react-icons/fi";
import { toast } from "sonner";
import { Button, LinkButton } from "@/app/components/button/Button";
import { EmptyBlock, ErrorBlock, LoadingBlock, PageTitle } from "@/app/components/card/States";
import { errorMessage } from "@/helper/api";
import { formatDateTime } from "@/helper/format";
import { useAsync } from "@/hooks/useAsync";
import { useAuth } from "@/hooks/useAuth";
import type { Address } from "@/interface/user.interface";
import { addressSchema, type AddressInput } from "@/schemas/auth.schema";
import { authService } from "@/services/auth.service";

export default function AccountPage() {
  const { user, hydrated, openLogin } = useAuth();
  if (!hydrated) return <LoadingBlock />;
  if (!user) {
    return (
      <EmptyBlock icon={<FiLock />} title="Đăng nhập để xem tài khoản">
        <Button className="mt-2" onClick={openLogin}>
          Đăng nhập
        </Button>
      </EmptyBlock>
    );
  }
  return <Account />;
}

function Account() {
  // Lấy lại từ server (GET /users/me) thay vì chỉ dùng bản lưu lúc đăng nhập
  const me = useAsync(() => authService.me(), []);
  const addresses = useAsync(() => authService.addresses(), []);

  return (
    <div>
      <PageTitle
        title="Tài khoản của tôi"
        action={
          <LinkButton href="/orders" variant="secondary" size="sm">
            Đơn hàng của tôi
          </LinkButton>
        }
      />
      <div className="grid gap-6 lg:grid-cols-[360px_1fr]">
        <section className="card h-fit p-6">
          <h2 className="mb-4 flex items-center gap-2 font-semibold text-neutral-900">
            <FiUser /> Thông tin
          </h2>
          {me.error && !me.data ? (
            <ErrorBlock error={me.error} onRetry={me.reload} />
          ) : !me.data ? (
            <LoadingBlock label="" />
          ) : (
            <dl className="space-y-3 text-sm">
              <Row label="Họ tên" value={me.data.fullName} />
              <Row label="Email" value={me.data.email} />
              <Row label="Số điện thoại" value={me.data.phone || "—"} />
              <Row label="Vai trò" value={me.data.roles.includes("ADMIN") ? "Quản trị viên" : "Khách hàng"} />
              <Row label="Ngày tạo" value={formatDateTime(me.data.createdAt)} />
            </dl>
          )}
        </section>

        <section className="card p-6">
          <h2 className="mb-4 flex items-center gap-2 font-semibold text-neutral-900">
            <FiMapPin /> Sổ địa chỉ
          </h2>
          {addresses.error && !addresses.data ? (
            <ErrorBlock error={addresses.error} onRetry={addresses.reload} />
          ) : !addresses.data ? (
            <LoadingBlock label="" />
          ) : (
            <AddressBook addresses={addresses.data} onCreated={addresses.reload} />
          )}
        </section>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4 border-b border-neutral-100 pb-3 last:border-0">
      <dt className="text-neutral-500">{label}</dt>
      <dd className="text-right font-medium text-neutral-900">{value}</dd>
    </div>
  );
}

function AddressBook({ addresses, onCreated }: { addresses: Address[]; onCreated: () => void }) {
  const [adding, setAdding] = useState(false);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AddressInput>({ resolver: zodResolver(addressSchema), defaultValues: { isDefault: addresses.length === 0 } });

  const onSubmit = async (v: AddressInput) => {
    try {
      await authService.createAddress(v);
      toast.success("Đã thêm địa chỉ");
      reset({ isDefault: false });
      setAdding(false);
      onCreated();
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const fields: { name: Exclude<keyof AddressInput, "isDefault">; label: string; wide?: boolean }[] = [
    { name: "recipientName", label: "Người nhận" },
    { name: "phone", label: "Số điện thoại" },
    { name: "addressLine", label: "Số nhà, đường", wide: true },
    { name: "ward", label: "Phường/Xã" },
    { name: "district", label: "Quận/Huyện" },
    { name: "city", label: "Tỉnh/Thành phố" },
  ];

  return (
    <div className="space-y-3">
      {addresses.length === 0 && !adding && <p className="text-sm text-neutral-500">Chưa có địa chỉ nào.</p>}
      {addresses.map((a) => (
        <div key={a.id} className="rounded-xl border border-neutral-200 p-4 text-sm">
          <div className="font-semibold text-neutral-900">
            {a.recipientName} · {a.phone}
            {a.isDefault && <span className="ml-2 rounded bg-neutral-900 px-1.5 py-0.5 text-[10px] text-white">MẶC ĐỊNH</span>}
          </div>
          <div className="text-neutral-500">{[a.addressLine, a.ward, a.district, a.city].join(", ")}</div>
        </div>
      ))}

      {adding ? (
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="rounded-xl border border-dashed border-neutral-300 p-4">
          <div className="grid gap-3 sm:grid-cols-2">
            {fields.map((f) => (
              <div key={f.name} className={f.wide ? "sm:col-span-2" : ""}>
                <label className="label" htmlFor={`acc-${f.name}`}>{f.label}</label>
                <input id={`acc-${f.name}`} className="input" {...register(f.name)} />
                {errors[f.name] && <p className="field-error">{errors[f.name]?.message}</p>}
              </div>
            ))}
          </div>
          <label className="mt-3 flex items-center gap-2 text-sm text-neutral-700">
            <input type="checkbox" className="accent-neutral-900" {...register("isDefault")} /> Đặt làm địa chỉ mặc định
          </label>
          <div className="mt-4 flex gap-2">
            <Button type="submit" size="sm" loading={isSubmitting}>
              Lưu địa chỉ
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={() => setAdding(false)}>
              Hủy
            </Button>
          </div>
        </form>
      ) : (
        <Button variant="secondary" size="sm" icon={<FiPlus />} onClick={() => setAdding(true)}>
          Thêm địa chỉ
        </Button>
      )}
    </div>
  );
}
