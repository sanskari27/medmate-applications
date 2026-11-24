# medmate-applications

Nx workspace for Medmate apps. A push to `main` deploys every app on this VPS with Docker. There are no apps in the repo yet.

## Add an app

Run this on the server, inside the repo:

```bash
pnpm medmate add \
  --name clinic \
  --kind next \
  --domain clinic.medmate.in \
  --port 3000 \
  --database true \
  --gates lint,typecheck,unit,integration,build,docker
```

`--gates none` turns every gate off. The fields are all required.

| Field | Meaning |
|---|---|
| `name` | Directory name and container name. Lowercase, starts with a letter, then letters, numbers, or underscores. |
| `kind` | `next`, `react`, `node-express`, `python-fastapi`, or `java-springboot`. |
| `domain` | Exact hostname to serve. A subdomain is just a hostname. |
| `port` | Port the process listens on inside the container. |
| `database` | `true` creates a private Postgres database and grants `medmate_remote` access. |
| `qualityGates` | Any of `lint`, `typecheck`, `unit`, `integration`, `build`, `docker`, in that order. |

`react` is a Vite single-page app served by its own container. `node-express` is TypeScript. `python-fastapi` is FastAPI. `java-springboot` is Maven and Java 21.

The app config is `apps/<name>/medmate.json`.

## Deploy rules

Deploy checks every app before it changes a running container.

DNS for each domain must already be an A record to this server's public IPv4 address. The check uses 1.1.1.1 and 8.8.8.8. If either resolver does not return only that address, deploy stops with an error and does not build.

Each selected gate runs as a Docker build target. A failing gate stops the deploy before containers are replaced.

`database: true` calls `/opt/stack/postgres/create-app-db.sh`. The app container receives `DATABASE_URL` for host `postgres` on the private `data` network. Your laptop still uses `medmate_remote` and chooses the database name.

Caddy on this server receives a site file for the domain and proxies to the container.

## GitHub

These Actions secrets are required:

| Secret | Value |
|---|---|
| `MEDMATE_SSH_PRIVATE_KEY` | Private key that can SSH to the server as `medmate` |
| `MEDMATE_HOST` | Server address |
| `MEDMATE_USER` | `medmate` |
| `MEDMATE_APP_ENVS` | Optional env bundle |

`MEDMATE_APP_ENVS` format:

```text
--- clinic
API_TOKEN=replace-me
--- web
PUBLIC_URL=https://web.example.com
```

The files are written to `.secrets/` on the server and are not committed.

The server pulls `main` with its own read-only GitHub deploy key. The public half of that key has to be added to the GitHub repository before the first automatic deploy.
