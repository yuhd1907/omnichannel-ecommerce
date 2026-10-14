# Next.js standalone (output: "standalone" trong next.config.ts). Build context: ./frontend
# GATEWAY_URL dùng cho rewrites /api/v1/* và được ghi vào bản build -> truyền lúc build.
# PAYMENT_WEBHOOK_SECRET (cổng MOCK ký webhook) là biến runtime, không nằm trong image.

FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

FROM node:24-alpine AS builder
WORKDIR /app
ARG GATEWAY_URL=http://api-gateway:8080
ENV GATEWAY_URL=$GATEWAY_URL NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:24-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3001 HOSTNAME=0.0.0.0
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder --chown=app:app /app/.next/standalone ./
COPY --from=builder --chown=app:app /app/.next/static ./.next/static
COPY --from=builder --chown=app:app /app/public ./public
USER app
EXPOSE 3001
CMD ["node", "server.js"]
