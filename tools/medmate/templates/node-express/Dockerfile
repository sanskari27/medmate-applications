FROM node:22-bookworm-slim AS deps
WORKDIR /app
COPY package.json ./
RUN corepack enable && corepack prepare pnpm@10.23.0 --activate && pnpm install

FROM deps AS source
COPY . .

FROM source AS format
RUN pnpm format

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

FROM node:22-bookworm-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=__PORT__
COPY --from=build /app ./
EXPOSE __PORT__
CMD ["node", "dist/index.js"]
