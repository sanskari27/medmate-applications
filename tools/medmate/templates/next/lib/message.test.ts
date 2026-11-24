import assert from "node:assert/strict";
import test from "node:test";
import { message } from "./message.ts";

test("message includes the app name", () => {
  assert.equal(message("__NAME__"), "Hello from __NAME__");
});
