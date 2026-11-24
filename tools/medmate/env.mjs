import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

export function parseEnvBundle(text) {
  const apps = new Map();
  let current = null;
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (line.length === 0) continue;
    const marker = line.match(/^---\s*([a-z][a-z0-9_]{1,30})\s*$/);
    if (marker) {
      current = marker[1];
      if (!apps.has(current)) apps.set(current, []);
      continue;
    }
    if (!current) {
      throw new Error(
        'Environment bundle must start with a line like "--- clinic" before any variables',
      );
    }
    if (!/^[A-Za-z_][A-Za-z0-9_]*=/.test(line) || line.startsWith("#")) {
      throw new Error(`Invalid environment line for ${current}: ${line}`);
    }
    apps.get(current).push(line);
  }
  return apps;
}

export async function writeEnvFiles(repoRoot, apps) {
  const directory = path.join(repoRoot, ".secrets");
  await mkdir(directory, { recursive: true, mode: 0o700 });
  for (const [name, lines] of apps) {
    const file = path.join(directory, `${name}.env`);
    await writeFile(file, `${lines.join("\n")}\n`, { mode: 0o600 });
  }
}
