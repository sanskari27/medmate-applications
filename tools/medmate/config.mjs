export const KINDS = [
  "next",
  "react",
  "node-express",
  "python-fastapi",
  "java-springboot",
];

export const GATES = [
  "lint",
  "typecheck",
  "unit",
  "integration",
  "build",
  "docker",
];

const NAME = /^[a-z][a-z0-9_]{1,30}$/;
const DOMAIN =
  /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}$/i;

export function validateConfig(config, dirName) {
  const errors = [];
  if (config === null || typeof config !== "object" || Array.isArray(config)) {
    return ["medmate.json must be a JSON object"];
  }
  if (config.name !== dirName) {
    errors.push(`name must be "${dirName}" to match the directory`);
  }
  if (!NAME.test(String(config.name ?? ""))) {
    errors.push(
      "name must start with a letter and then use only lowercase letters, numbers, and underscores",
    );
  }
  if (!KINDS.includes(config.kind)) {
    errors.push(`kind must be one of: ${KINDS.join(", ")}`);
  }
  if (typeof config.domain !== "string" || !DOMAIN.test(config.domain)) {
    errors.push("domain must be a hostname, for example app.example.com");
  }
  if (!Number.isInteger(config.port) || config.port < 1 || config.port > 65535) {
    errors.push("port must be an integer from 1 to 65535");
  }
  if (typeof config.database !== "boolean") {
    errors.push("database must be true or false");
  }
  if (!Array.isArray(config.qualityGates)) {
    errors.push("qualityGates must be an array");
  } else {
    const unknown = config.qualityGates.filter((gate) => !GATES.includes(gate));
    if (unknown.length > 0) {
      errors.push(`unknown quality gates: ${unknown.join(", ")}`);
    }
    if (new Set(config.qualityGates).size !== config.qualityGates.length) {
      errors.push("qualityGates contains a duplicate");
    }
  }
  const allowed = new Set([
    "name",
    "kind",
    "domain",
    "port",
    "database",
    "qualityGates",
  ]);
  for (const key of Object.keys(config)) {
    if (!allowed.has(key)) errors.push(`unknown field "${key}"`);
  }
  return errors;
}

export function orderedGates(selected) {
  return GATES.filter((gate) => selected.includes(gate));
}
