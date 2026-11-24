import { cp, mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { validateConfig } from "./config.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const templates = path.join(path.dirname(fileURLToPath(import.meta.url)), "templates");

export async function addApp(options) {
  const config = {
    name: options.name,
    kind: options.kind,
    domain: options.domain,
    port: Number(options.port),
    database: options.database === true || options.database === "true",
    qualityGates: options.gates === "none" ? [] : options.gates.split(",").filter(Boolean),
  };
  if (options.database !== "true" && options.database !== "false") {
    throw new Error("database must be true or false");
  }
  const errors = validateConfig(config, config.name);
  if (errors.length > 0) throw new Error(errors.join("\n"));
  const destination = path.join(root, "apps", config.name);
  try {
    await mkdir(destination);
  } catch (error) {
    if (error.code === "EEXIST") throw new Error(`apps/${config.name} already exists`);
    throw error;
  }
  const values = {
    __NAME__: config.name,
    __PORT__: String(config.port),
    __DOMAIN__: config.domain,
  };
  await copyTemplate(path.join(templates, config.kind), destination, values);
  const project = await readFile(path.join(templates, "project.json"), "utf8");
  await writeFile(path.join(destination, "project.json"), replaceTokens(project, values));
  await writeFile(
    path.join(destination, "medmate.json"),
    `${JSON.stringify(config, null, 2)}\n`,
  );
  console.log(`Created apps/${config.name}`);
}

async function copyTemplate(source, destination, values) {
  const entries = await readdir(source, { withFileTypes: true });
  for (const entry of entries) {
    const from = path.join(source, entry.name);
    const to = path.join(destination, replaceTokens(entry.name, values));
    if (entry.isDirectory()) {
      await mkdir(to, { recursive: true });
      await copyTemplate(from, to, values);
      continue;
    }
    const text = await readFile(from, "utf8");
    await writeFile(to, replaceTokens(text, values));
  }
}

function replaceTokens(text, values) {
  return Object.entries(values).reduce(
    (result, [token, value]) => result.replaceAll(token, value),
    text,
  );
}
