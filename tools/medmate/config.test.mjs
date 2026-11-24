import assert from "node:assert/strict";
import test from "node:test";
import { orderedGates, validateConfig } from "./config.mjs";

const valid = {
  name: "clinic",
  kind: "next",
  domain: "clinic.medmate.in",
  port: 3000,
  database: true,
  qualityGates: ["unit", "lint"],
};

test("accepts a complete app config", () => {
  assert.deepEqual(validateConfig(valid, "clinic"), []);
});

test("rejects a name that does not match the directory", () => {
  const errors = validateConfig(valid, "other");
  assert.ok(errors.some((error) => error.includes("directory")));
});

test("rejects an unknown kind and an unknown gate", () => {
  const errors = validateConfig(
    { ...valid, kind: "django", qualityGates: ["lint", "deploy"] },
    "clinic",
  );
  assert.ok(errors.some((error) => error.includes("kind")));
  assert.ok(errors.some((error) => error.includes("deploy")));
});

test("orders gates in the platform order", () => {
  assert.deepEqual(orderedGates(["docker", "lint", "unit", "format"]), [
    "format",
    "lint",
    "unit",
    "docker",
  ]);
});
