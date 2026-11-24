FROM node:22-bookworm-slim AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM deps AS source
COPY . .

FROM source AS format
RUN npm run format

FROM source AS lint
RUN npm run lint

FROM source AS typecheck
RUN npm run typecheck

FROM source AS unit
RUN npm run unit

FROM source AS build
ENV VITE_SITE_URL=https://admin.medmate.in
ENV VITE_API_BASE_URL=https://pharmacy-server.medmate.in
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 3205
