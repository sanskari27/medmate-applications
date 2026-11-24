import { parseArgs } from "node:util";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { addApp } from "./add.mjs";
import { GATES, KINDS } from "./config.mjs";
import { deploy } from "./deploy.mjs";
import { parseEnvBundle, writeEnvFiles } from "./env.mjs";
import { readFile } from "node:fs/promises";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const [command, ...rest] = process.argv.slice(2);

try {
  if (command === "add") {
    const { values } = parseArgs({
      args: rest,
      options: {
        name: { type: "string" },
        kind: { type: "string" },
        domain: { type: "string" },
        port: { type: "string" },
        database: { type: "string" },
        gates: { type: "string" },
      },
      strict: true,
    });
    const missing = ["name", "kind", "domain", "port", "database", "gates"].filter(
      (key) => values[key] === undefined,
    );
    if (missing.length > 0) {
      throw new Error(
        `Missing --${missing.join(", --")}\nKinds: ${KINDS.join(", ")}\nGates: ${GATES.join(", ")}, or none`,
      );
    }
    await addApp(values);
  } else if (command === "deploy") {
    await deploy(repoRoot);
  } else if (command === "load-env") {
    const text = rest[0] ? await readFile(rest[0], "utf8") : await readStdin();
    const apps = parseEnvBundle(text);
    await writeEnvFiles(repoRoot, apps);
    console.log(`Wrote ${apps.size} env file${apps.size === 1 ? "" : "s"}`);
  } else {
    console.log(`Usage:
  pnpm medmate add --name <name> --kind <kind> --domain <host> --port <port> --database <true|false> --gates <lint,typecheck,unit,integration,build,docker|none>
  pnpm medmate deploy
  pnpm medmate load-env [file]`);
    process.exit(command ? 1 : 0);
  }
} catch (error) {
  console.error(error.message);
  process.exit(1);
}

async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return Buffer.concat(chunks).toString("utf8");
}
