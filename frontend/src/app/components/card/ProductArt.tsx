"use client";

/* eslint-disable @next/next/no-img-element -- ảnh SKU là URL tùy ý do admin nhập, không cấu hình được remotePatterns trước */

// Sản phẩm (ProductSummaryDto) không có ảnh; SKU có thể có imageUrl. Không có ảnh thì vẽ nền
// gradient theo id + chữ cái đầu, để mỗi sản phẩm vẫn nhận ra được. Ảnh lỗi (URL chết) cũng rơi về nền này.

import { useState } from "react";

// Các tông xám (đơn sắc); sản phẩm khác nhau lệch tông nhẹ để vẫn phân biệt được
const PALETTES = [
  ["#f5f5f5", "#e5e5e5"],
  ["#fafafa", "#d4d4d4"],
  ["#eeeeee", "#f7f7f7"],
  ["#e7e5e4", "#fafaf9"],
  ["#f4f4f5", "#d4d4d8"],
];

function hash(s: string) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}

export function ProductArt({
  name,
  seed,
  imageUrl,
  className = "",
}: {
  name: string;
  seed: string;
  imageUrl?: string | null;
  className?: string;
}) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  if (imageUrl && failedUrl !== imageUrl) {
    return (
      <div className={`overflow-hidden bg-neutral-100 ${className}`}>
        <img src={imageUrl} alt={name} className="h-full w-full object-cover" loading="lazy" onError={() => setFailedUrl(imageUrl)} />
      </div>
    );
  }
  const [from, to] = PALETTES[hash(seed) % PALETTES.length];
  const initials = name
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0])
    .join("")
    .toUpperCase();
  return (
    <div
      className={`relative flex items-center justify-center overflow-hidden ${className}`}
      style={{ background: `linear-gradient(145deg, ${from}, ${to})` }}
    >
      <span className="relative text-5xl font-extrabold tracking-[-0.05em] text-neutral-900/80">{initials}</span>
    </div>
  );
}
