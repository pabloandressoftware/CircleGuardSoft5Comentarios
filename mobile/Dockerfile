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

FROM nginxinc/nginx-unprivileged:1.30-alpine
# HU-20: parchea los paquetes de Alpine con CVE criticos corregidos (Trivy bloquea la etiqueta si no).
USER root
# La SPA es estatica: se retiran los modulos dinamicos (njs, xslt, geoip, image-filter) que no usa y
# cuya version 1.30.7 aun no esta en el repositorio de Alpine (fijaban nginx a 1.30.6); sin ellos
# apk puede subir nginx a >=1.30.7, que corrige los CVE criticos.
RUN apk del nginx-module-geoip nginx-module-image-filter nginx-module-njs nginx-module-xslt && \
    apk add --no-cache 'nginx>=1.30.7' && \
    apk upgrade --no-cache
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/ /usr/share/nginx/html/
EXPOSE 8080
USER 101
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -q -T 4 -O /dev/null http://127.0.0.1:8080/
