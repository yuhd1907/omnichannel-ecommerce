"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { FiCreditCard, FiLock, FiMapPin, FiPlus, FiShoppingCart } from "react-icons/fi";
import { toast } from "sonner";
import { Button, choiceClass, LinkButton } from "@/app/components/button/Button";
import { EmptyBlock, ErrorBlock, LoadingBlock, PageTitle } from "@/app/components/card/States";
import { ApiError, errorMessage } from "@/helper/api";
import { formatCurrency, newId } from "@/helper/format";
import { useAsync } from "@/hooks/useAsync";
import { useAuth } from "@/hooks/useAuth";
import { useCart } from "@/hooks/useCart";
import type { PaymentMethod } from "@/interface/payment.interface";
import type { Address } from "@/interface/user.interface";
import { addressSchema, type AddressInput } from "@/schemas/auth.schema";
import { authService } from "@/services/auth.service";
import { orderService } from "@/services/order.service";

const METHODS: { id: PaymentMethod; title: string; desc: string }[] = [
  { id: "COD", title: "Thanh toán khi nhận hàng (COD)", desc: "Đơn được xác nhận ngay, trả tiền mặt cho nhân viên giao hàng" },
  { id: "VNPAY", title: "VNPAY", desc: "Thẻ ATM / Internet Banking / quét mã VNPAY-QR bằng app ngân hàng" },
];

export default function CheckoutPage() {
  const { user, hydrated, openLogin } = useAuth();
  const cart = useCart();
  // Đặt xong thì giỏ bị xóa trước khi chuyển trang -> giữ màn hình chờ, không nháy "giỏ trống"
  const [placed, setPlaced] = useState(false);

  if (!hydrated || placed) return <LoadingBlock label={placed ? "Đang mở đơn hàng..." : undefined} />;
  if (cart.items.length === 0) {
    return (
      <EmptyBlock icon={<FiShoppingCart />} title="Chưa có sản phẩm để đặt">
        <LinkButton href="/" className="mt-2">
          Về cửa hàng
        </LinkButton>
      </EmptyBlock>
    );
  }
  if (!user) {
    return (
      <EmptyBlock icon={<FiLock />} title="Đăng nhập để đặt hàng">
        <p>Giỏ hàng của bạn vẫn được giữ nguyên.</p>
        <Button className="mt-4" onClick={openLogin}>
          Đăng nhập / Đăng ký
        </Button>
      </EmptyBlock>
    );
  }
  return <Checkout onPlaced={() => setPlaced(true)} />;
}

function Checkout({ onPlaced }: { onPlaced: () => void }) {
  const router = useRouter();
  const cart = useCart();
  const addresses = useAsync(() => authService.addresses(), []);
  const [addressId, setAddressId] = useState<string | null>(null);
  const [method, setMethod] = useState<PaymentMethod>("COD");
  const [placing, setPlacing] = useState(false);
  const idem = useRef<{ payload: string; key: string } | null>(null);

  const list = addresses.data ?? [];
  const selectedId = addressId ?? list.find((a) => a.isDefault)?.id ?? list[0]?.id ?? null;

  const placeOrder = async () => {
    if (!selectedId) {
      toast.error("Vui lòng chọn hoặc thêm địa chỉ giao hàng");
      return;
    }
    const req = {
      channel: "WEB" as const,
      addressId: selectedId,
      items: cart.items.map((i) => ({ skuCode: i.skuCode, quantity: i.quantity })),
    };
    // Cùng nội dung -> dùng lại key (bấm lại sau khi mất mạng không tạo đơn thứ 2); nội dung đổi -> key mới
    const payload = JSON.stringify(req);
    if (idem.current?.payload !== payload) idem.current = { payload, key: newId() };

    setPlacing(true);
    try {
      const { order, replayed } = await orderService.create(req, idem.current.key);
      idem.current = null;
      onPlaced();
      cart.clear();
      toast.success(replayed ? "Đơn hàng đã được tạo trước đó" : "Đặt hàng thành công, đang giữ hàng cho bạn");
      router.push(`/orders/${order.id}?pay=${method}`);
    } catch (err) {
      // Lỗi nghiệp vụ (4xx) -> lần sau là yêu cầu mới; lỗi mạng/5xx -> giữ key để retry an toàn
      if (err instanceof ApiError && err.status >= 400 && err.status < 500) idem.current = null;
      toast.error(errorMessage(err));
      setPlacing(false);
    }
  };

  return (
    <div>
      <PageTitle title="Đặt hàng" subtitle="Kiểm tra địa chỉ, chọn phương thức thanh toán và xác nhận" />
      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <div className="space-y-6">
          <section className="card p-6">
            <h2 className="mb-4 flex items-center gap-2 font-semibold text-neutral-900">
              <FiMapPin className="text-neutral-900" /> Địa chỉ giao hàng
            </h2>
            {addresses.error && !addresses.data ? (
              <ErrorBlock error={addresses.error} onRetry={addresses.reload} />
            ) : !addresses.data ? (
              <LoadingBlock label="Đang tải địa chỉ..." />
            ) : (
              <AddressPicker
                addresses={list}
                selectedId={selectedId}
                onSelect={setAddressId}
                onCreated={(a) => {
                  addresses.setData([...list, a]);
                  setAddressId(a.id);
                }}
              />
            )}
          </section>

          <section className="card p-6">
            <h2 className="mb-4 flex items-center gap-2 font-semibold text-neutral-900">
              <FiCreditCard className="text-neutral-900" /> Phương thức thanh toán
            </h2>
            <div className="grid gap-3 sm:grid-cols-2">
              {METHODS.map((m) => (
                <button
                  key={m.id}
                  onClick={() => setMethod(m.id)}
                  className={`p-4 ${choiceClass(method === m.id)}`}
                >
                  <div className="font-semibold text-neutral-900">{m.title}</div>
                  <div className="mt-1 text-xs text-neutral-500">{m.desc}</div>
                </button>
              ))}
            </div>
          </section>
        </div>

        <aside className="card h-fit p-6 lg:sticky lg:top-24">
          <h2 className="mb-4 font-semibold text-neutral-900">Đơn hàng</h2>
          <div className="space-y-3">
            {cart.items.map((i) => (
              <div key={i.skuCode} className="flex justify-between gap-3 text-sm">
                <div className="min-w-0">
                  <div className="truncate text-neutral-800">{i.productName}</div>
                  <div className="text-xs text-neutral-500">
                    {i.skuLabel} × {i.quantity}
                  </div>
                </div>
                <div className="shrink-0 text-neutral-700">{formatCurrency(i.price * i.quantity)}</div>
              </div>
            ))}
          </div>
          <div className="mt-5 flex justify-between border-t border-neutral-200 pt-4 text-lg font-bold text-neutral-900">
            <span>Tổng cộng</span>
            <span className="text-neutral-900">{formatCurrency(cart.totalAmount)}</span>
          </div>
          <Button size="lg" className="mt-6 w-full" loading={placing} disabled={!selectedId} onClick={placeOrder}>
            Đặt hàng
          </Button>
          <p className="mt-3 text-xs leading-relaxed text-neutral-500">
            Hàng được giữ 15 phút sau khi đặt. Quá hạn chưa thanh toán, đơn tự hủy và trả hàng về kho.
          </p>
        </aside>
      </div>
    </div>
  );
}

function AddressPicker({
  addresses,
  selectedId,
  onSelect,
  onCreated,
}: {
  addresses: Address[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onCreated: (a: Address) => void;
}) {
  const [adding, setAdding] = useState(addresses.length === 0);

  return (
    <div className="space-y-3">
      {addresses.map((a) => (
        <label
          key={a.id}
          className={`flex cursor-pointer gap-3 p-4 ${choiceClass(selectedId === a.id)}`}
        >
          <input type="radio" name="address" className="mt-1 accent-neutral-900" checked={selectedId === a.id} onChange={() => onSelect(a.id)} />
          <div className="text-sm">
            <div className="font-semibold text-neutral-900">
              {a.recipientName} · {a.phone}
              {a.isDefault && <span className="ml-2 rounded bg-neutral-200 px-1.5 py-0.5 text-[10px] text-neutral-900">MẶC ĐỊNH</span>}
            </div>
            <div className="text-neutral-500">{[a.addressLine, a.ward, a.district, a.city].join(", ")}</div>
          </div>
        </label>
      ))}

      {adding ? (
        <AddressForm
          canCancel={addresses.length > 0}
          onCancel={() => setAdding(false)}
          onCreated={(a) => {
            onCreated(a);
            setAdding(false);
          }}
        />
      ) : (
        <Button variant="ghost" size="sm" icon={<FiPlus />} onClick={() => setAdding(true)}>
          Thêm địa chỉ mới
        </Button>
      )}
    </div>
  );
}

function AddressForm({ canCancel, onCancel, onCreated }: { canCancel: boolean; onCancel: () => void; onCreated: (a: Address) => void }) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<AddressInput>({ resolver: zodResolver(addressSchema), defaultValues: { isDefault: !canCancel } });

  const onSubmit = async (v: AddressInput) => {
    try {
      onCreated(await authService.createAddress(v));
      toast.success("Đã lưu địa chỉ");
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
    <form onSubmit={handleSubmit(onSubmit)} className="rounded-xl bg-neutral-50 p-4 ring-1 ring-neutral-300" noValidate>
      <div className="grid gap-3 sm:grid-cols-2">
        {fields.map((f) => (
          <div key={f.name} className={f.wide ? "sm:col-span-2" : ""}>
            <label className="label" htmlFor={`addr-${f.name}`}>{f.label}</label>
            <input id={`addr-${f.name}`} className="input" {...register(f.name)} />
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
        {canCancel && (
          <Button type="button" variant="ghost" size="sm" onClick={onCancel}>
            Hủy
          </Button>
        )}
      </div>
    </form>
  );
}
