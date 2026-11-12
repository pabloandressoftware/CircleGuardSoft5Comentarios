# syntax=docker/dockerfile:1
FROM node:22-bookworm-slim AS build
WORKDIR /app
ENV CI=true EXPO_NO_TELEMETRY=1
# La misma imagen web sirve en dev, stage y prod: llama a /api/v1/... en su origen.
ARG EXPO_PUBLIC_API_BASE_URL=/
ENV EXPO_PUBLIC_API_BASE_URL=${EXPO_PUBLIC_API_BASE_URL}
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npx --no-install expo export --platform web --output-dir dist

FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/ /usr/share/nginx/html/
EXPOSE 8080
USER 101
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -q -T 4 -O /dev/null http://127.0.0.1:8080/
