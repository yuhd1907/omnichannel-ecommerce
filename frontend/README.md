# Frontend — gom.

Web cho khách và quản trị của Omnichannel E-Commerce. Next.js 16 (App Router), TypeScript, Tailwind CSS v4.

## Chạy

```powershell
npm ci
npm run dev        # http://localhost:3001 (3000 là Grafana)
```

Cần backend đang chạy (`docker compose up -d --wait` ở gốc repo). Trình duyệt chỉ gọi `/api/v1/*` cùng origin; `next.config.ts` proxy sang API Gateway `GATEWAY_URL` (mặc định `http://localhost:8080`, đọc lúc build). Trên VPS, Nginx chuyển `/api/v1/` thẳng tới gateway.

| Biến | Dùng ở | Ghi chú |
| --- | --- | --- |
| `GATEWAY_URL` | build (rewrites) + route `/api/mock-gateway` | Docker: `http://api-gateway:8080` |
| `PAYMENT_WEBHOOK_SECRET` | route `/api/mock-gateway` (server) | Phải trùng payment-service. Dev có giá trị mặc định; `next start` bắt buộc đặt |

Kiểm tra trước khi đẩy code (CI chạy y hệt):

```powershell
npx next typegen; npx tsc --noEmit; npm run lint; npm run build
```

## Cấu trúc

```
src/
├── app/
│   ├── (pages)/            # cửa hàng, sản phẩm, giỏ, đặt hàng, đơn hàng (+ thanh toán, theo dõi Saga), quản trị, VNPAY return
│   ├── api/mock-gateway/   # cổng thanh toán MOCK: ký webhook HMAC-SHA256 phía server (secret không xuống trình duyệt)
│   ├── components/         # header, footer, button, card, modal
│   ├── icon.svg            # favicon
│   └── layout.tsx, providers.tsx, globals.css
├── config/                 # hằng số, nhãn trạng thái, thông báo lỗi theo ErrorCode
├── helper/                 # gọi API (tự refresh token), định dạng, localStorage, store
├── hooks/                  # useAuth, useCart, useProducts, useAsync
├── interface/              # kiểu dữ liệu khớp DTO backend
├── schemas/                # zod schema cho form
└── services/               # gọi từng nhóm API
```

Thiết kế: nền trắng đơn sắc; nút có viền có bóng xám khi bấm, nút không viền đậm khi chưa di chuột. Font Be Vietnam Pro.
