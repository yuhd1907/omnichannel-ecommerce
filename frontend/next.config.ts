import type { NextConfig } from "next";

// Trình duyệt chỉ gọi /api/v1/* cùng origin; Next proxy sang API Gateway.
// Backend không bật CORS nên gọi thẳng http://localhost:8080 từ trình duyệt sẽ bị chặn.
// Lưu ý: rewrites được đọc lúc `next build` -> đổi GATEWAY_URL thì phải build lại.
const GATEWAY_URL = (process.env.GATEWAY_URL ?? "http://localhost:8080").replace(/\/$/, "");

const nextConfig: NextConfig = {
  output: "standalone",
  // Khong de `next dev` tu sinh AGENTS.md (huong dan cho AI agent) vao repo
  agentRules: false,
  async rewrites() {
    return [{ source: "/api/v1/:path*", destination: `${GATEWAY_URL}/api/v1/:path*` }];
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
