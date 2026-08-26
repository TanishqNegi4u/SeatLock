# ── Multi-Stage Build: Builder Stage ─────────────────────────────────
# Base Image Choice: node:20-alpine
# Why: Node 20 LTS Alpine image (~130MB) contains the modern npm ecosystem required
# for React 18 production compilation, minimizing CI/CD download and build times.
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# ── Multi-Stage Build: Runtime Stage ─────────────────────────────────
# Base Image Choice: nginx:alpine
# Why: High-performance, lightweight web server (~23MB total image size).
# Serves static React assets with zero Node.js runtime overhead and acts as an
# in-cluster reverse proxy for /api and /ws endpoints.
FROM nginx:alpine AS runtime
COPY --from=builder /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
