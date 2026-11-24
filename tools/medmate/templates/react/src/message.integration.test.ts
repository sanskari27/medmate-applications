import assert from "node:assert/strict";
import test from "node:test";
import { message } from "./message.ts";

test("message is ready for the page", () => {
  assert.match(message("__NAME__"), /^Hello from /);
});
