FROM node:22-bookworm-slim AS deps
WORKDIR /app
COPY package.json ./
RUN corepack enable && corepack prepare pnpm@10.23.0 --activate && pnpm install

FROM deps AS source
COPY . .

FROM source AS lint
RUN pnpm lint

FROM source AS typecheck
RUN pnpm typecheck

FROM source AS unit
RUN pnpm unit

FROM source AS integration
RUN pnpm integration

FROM source AS build
RUN pnpm build

FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE __PORT__
