import assert from "node:assert/strict";
import test from "node:test";
import { message } from "./message.ts";

test("message is ready for the server", () => {
  assert.match(message("__NAME__"), /^Hello from /);
});
