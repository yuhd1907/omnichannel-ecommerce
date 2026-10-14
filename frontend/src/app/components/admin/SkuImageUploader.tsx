"use client";

import { useState } from "react";
import { FiUpload } from "react-icons/fi";
import { toast } from "sonner";
import { ProductArt } from "@/app/components/card/ProductArt";
import { errorMessage } from "@/helper/api";
import { productService } from "@/services/product.service";

const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;

/** Ảnh SKU: xem trước + nút tải ảnh (JPEG/PNG/WebP ≤ 2 MB); server kiểm tra lại chữ ký file. */
export function SkuImageUploader({
  skuCode,
  name,
  imageUrl,
  onUploaded,
}: {
  skuCode: string;
  name: string;
  imageUrl: string | null;
  onUploaded?: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [current, setCurrent] = useState(imageUrl);

  const onPick = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    if (!IMAGE_TYPES.includes(file.type)) {
      toast.error("Chỉ nhận ảnh JPEG, PNG hoặc WebP");
      return;
    }
    if (file.size > MAX_IMAGE_BYTES) {
      toast.error("Ảnh tối đa 2 MB");
      return;
    }
    setBusy(true);
    try {
      const sku = await productService.uploadSkuImage(skuCode, file);
      setCurrent(sku.imageUrl);
      toast.success(`Đã cập nhật ảnh ${skuCode}`);
      onUploaded?.();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex items-center gap-3">
      <ProductArt name={name} seed={skuCode} imageUrl={current} className="h-20 w-20 shrink-0 rounded-xl border border-neutral-200" />
      <label
        className={`inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-xl border border-neutral-300 bg-white px-3 text-xs font-semibold text-neutral-900 transition hover:border-neutral-400 active:shadow-[0_8px_18px_-6px_rgba(0,0,0,0.28)] ${
          busy ? "pointer-events-none opacity-50" : ""
        }`}
      >
        <FiUpload /> {busy ? "Đang tải..." : current ? "Đổi ảnh" : "Tải ảnh"}
        <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={onPick} disabled={busy} />
      </label>
    </div>
  );
}

