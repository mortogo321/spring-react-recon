# syntax=docker/dockerfile:1.7
#
# The console: built with bun, served by nginx, which also proxies /api to the API container so the
# browser sees one origin. That is the same arrangement as the Vite dev proxy, which is the point —
# auth behaves identically in development and in the deployment.

# ---------------------------------------------------------------- build
FROM oven/bun:1.4-alpine AS build
WORKDIR /app

# Manifest and lockfile first: dependencies are the slow half and change far less often than source.
COPY frontend/package.json frontend/bun.lock ./
RUN bun install --frozen-lockfile

COPY frontend/ ./
# tsc runs as part of `build`, so a type error fails the image rather than shipping.
RUN bun run build

# ---------------------------------------------------------------- runtime
FROM nginx:1.29-alpine AS runtime

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

# nginx's own unprivileged image variant runs as uid 101; do the same here rather than as root.
RUN chown -R 101:101 /usr/share/nginx/html /var/cache/nginx /var/log/nginx \
    && touch /var/run/nginx.pid && chown 101:101 /var/run/nginx.pid
USER 101:101

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/healthz >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
