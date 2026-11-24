import assert from "node:assert/strict";
import test from "node:test";
import { mergeEnvLines, parseEnvBundle } from "./env.mjs";

test("splits an environment bundle by app", () => {
  const apps = parseEnvBundle("--- clinic\nAPI_URL=https://example.com\n\n--- web\nNODE_ENV=production\n");
  assert.deepEqual(apps.get("clinic"), ["API_URL=https://example.com"]);
  assert.deepEqual(apps.get("web"), ["NODE_ENV=production"]);
});

test("rejects a variable before an app marker", () => {
  assert.throws(() => parseEnvBundle("API_URL=https://example.com\n"), /must start/);
});

test("keeps existing values when merging storage credentials", () => {
  const merged = mergeEnvLines(
    "DATABASE_URL=postgres://local\nS3_BUCKET=custom\n",
    "S3_BUCKET=pharmacy_server\nS3_ENDPOINT=http://storage:3900\n# comment\n",
  );
  assert.equal(
    merged,
    "DATABASE_URL=postgres://local\nS3_BUCKET=custom\nS3_ENDPOINT=http://storage:3900\n",
  );
});
