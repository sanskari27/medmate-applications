import { execFileSync } from "node:child_process";
import { access, mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { orderedGates, validateConfig } from "./config.mjs";
import { mergeEnvLines } from "./env.mjs";
import { assertDomainPointsHere, publicIpv4 } from "./dns.mjs";

const RESOLVERS = ["1.1.1.1", "8.8.8.8"];
const CADDY_SITES = "/opt/stack/caddy/sites";
const CREATE_DB = "/opt/stack/postgres/create-app-db.sh";
const DB_ENV_DIR = "/opt/stack/postgres/apps";
const STORAGE_ENV_DIR = "/opt/stack/storage/apps";

export async function loadApps(repoRoot) {
  const appsDir = path.join(repoRoot, "apps");
  let entries = [];
  try {
    entries = await readdir(appsDir, { withFileTypes: true });
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw error;
  }
  const apps = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const configPath = path.join(appsDir, entry.name, "medmate.json");
    try {
      await access(configPath);
    } catch {
      throw new Error(`${entry.name} has no medmate.json`);
    }
    const config = JSON.parse(await readFile(configPath, "utf8"));
    const errors = validateConfig(config, entry.name);
    if (errors.length > 0) {
      throw new Error(`${configPath}:\n- ${errors.join("\n- ")}`);
    }
    apps.push({ directory: path.join(appsDir, entry.name), config });
  }
  const domains = new Map();
  for (const app of apps) {
    const previous = domains.get(app.config.domain);
    if (previous) {
      throw new Error(
        `${app.config.name} and ${previous} both use ${app.config.domain}`,
      );
    }
    domains.set(app.config.domain, app.config.name);
  }
  return apps;
}

export async function deploy(repoRoot) {
  const apps = await loadApps(repoRoot);
  if (apps.length === 0) {
    console.log("No apps to deploy.");
    return;
  }
  const address = publicIpv4();
  for (const app of apps) {
    assertDomainPointsHere(app.config.domain, RESOLVERS, address);
    console.log(`${app.config.domain} points at ${address}`);
  }
  for (const app of apps) {
    for (const gate of orderedGates(app.config.qualityGates)) {
      console.log(`Running ${app.config.name}:${gate}`);
      execFileSync("pnpm", ["exec", "nx", "run", `${app.config.name}:${gate}`], {
        cwd: repoRoot,
        stdio: "inherit",
      });
    }
  }
  for (const app of apps) {
    if (app.config.database) await ensureDatabase(repoRoot, app.config.name);
    await ensureStorage(repoRoot, app.config.name);
    await writeCompose(app);
    execFileSync(
      "docker",
      ["compose", "-f", composePath(app), "up", "-d", "--build", "--remove-orphans"],
      { cwd: repoRoot, stdio: "inherit" },
    );
    await writeCaddySite(app.config);
  }
  execFileSync(
    "docker",
    ["exec", "caddy-caddy-1", "caddy", "reload", "--config", "/etc/caddy/Caddyfile"],
    { stdio: "inherit" },
  );
  console.log(`Deployed ${apps.map((app) => app.config.name).join(", ")}`);
}

async function ensureDatabase(repoRoot, name) {
  const dbEnv = path.join(DB_ENV_DIR, `${name}.env`);
  try {
    await access(dbEnv);
  } catch {
    execFileSync(CREATE_DB, [name], { stdio: "inherit" });
  }
  const created = await readFile(dbEnv, "utf8");
  const url = created.match(/^DATABASE_URL=(.*)$/m)?.[1];
  if (!url) throw new Error(`${dbEnv} does not contain DATABASE_URL`);
  const secret = path.join(repoRoot, ".secrets", `${name}.env`);
  await mkdir(path.dirname(secret), { recursive: true, mode: 0o700 });
  let current = "";
  try {
    current = await readFile(secret, "utf8");
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
  if (!/^DATABASE_URL=/m.test(current)) {
    const next = `${current.trim()}${current.trim() ? "\n" : ""}DATABASE_URL=${url}\n`;
    await writeFile(secret, next, { mode: 0o600 });
  }
}

async function ensureStorage(repoRoot, name) {
  const source = path.join(STORAGE_ENV_DIR, `${name}.env`);
  let incoming = "";
  try {
    incoming = await readFile(source, "utf8");
  } catch (error) {
    if (error.code === "ENOENT") return;
    throw error;
  }
  const secret = path.join(repoRoot, ".secrets", `${name}.env`);
  await mkdir(path.dirname(secret), { recursive: true, mode: 0o700 });
  let current = "";
  try {
    current = await readFile(secret, "utf8");
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
  const next = mergeEnvLines(current, incoming);
  if (next !== current && next !== `${current.trim()}\n`) {
    await writeFile(secret, next, { mode: 0o600 });
  }
}

function composePath(app) {
  return path.join(app.directory, ".medmate", "compose.yaml");
}

async function writeCompose(app) {
  const { name, database } = app.config;
  const networks = database
    ? `      web:
        aliases:
          - ${name}
      data: {}`
    : `      web:
        aliases:
          - ${name}`;
  const networkDefs = database
    ? `  web:
    external: true
    name: web
  data:
    external: true
    name: data`
    : `  web:
    external: true
    name: web`;
  const yaml = `services:
  ${name}:
    build:
      context: ..
      dockerfile: ../Dockerfile
      target: runtime
    image: medmate-${name}
    restart: unless-stopped
    env_file:
      - path: ../../../.secrets/${name}.env
        required: false
    networks:
${networks}
networks:
${networkDefs}
`;
  const file = composePath(app);
  await mkdir(path.dirname(file), { recursive: true });
  await writeFile(file, yaml);
}

async function writeCaddySite(config) {
  const file = path.join(CADDY_SITES, `${config.domain}.caddy`);
  const body = `${config.domain} {
	encode zstd gzip
	header {
		X-Content-Type-Options nosniff
		Referrer-Policy strict-origin-when-cross-origin
		X-Frame-Options DENY
		Strict-Transport-Security "max-age=15552000"
	}
	reverse_proxy ${config.name}:${config.port}
}
`;
  await writeFile(file, body);
}
