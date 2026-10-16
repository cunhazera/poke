# syntax=docker/dockerfile:1

# ---- build: type-check and bundle (tests run in CI and with `npm test`) ----
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- runtime: static files + reverse proxy for /api, non-root ----
FROM nginxinc/nginx-unprivileged:1.29-alpine
# The official image renders templates/*.template with the environment at startup (only variables that are set).
ENV API_UPSTREAM=http://app:8080
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
