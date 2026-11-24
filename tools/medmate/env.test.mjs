import assert from "node:assert/strict";
import test from "node:test";
import { parseEnvBundle } from "./env.mjs";

test("splits an environment bundle by app", () => {
  const apps = parseEnvBundle("--- clinic\nAPI_URL=https://example.com\n\n--- web\nNODE_ENV=production\n");
  assert.deepEqual(apps.get("clinic"), ["API_URL=https://example.com"]);
  assert.deepEqual(apps.get("web"), ["NODE_ENV=production"]);
});

test("rejects a variable before an app marker", () => {
  assert.throws(() => parseEnvBundle("API_URL=https://example.com\n"), /must start/);
});
