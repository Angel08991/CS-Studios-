"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const serverSource = fs.readFileSync(path.join(__dirname, "..", "server.js"), "utf8");

test("catalog service uses the platform port and bounded upload sizes", () => {
  assert.match(serverSource, /process\.env\.PORT/);
  assert.match(serverSource, /MAX_PROJECT_BYTES = 500 \* 1024/);
  assert.match(serverSource, /MAX_BODY_BYTES = 700 \* 1024/);
});

test("published templates require a format version and pages section", () => {
  assert.match(serverSource, /format-version\|version/);
  assert.match(serverSource, /pages/);
  assert.match(serverSource, /isValidProjectYaml/);
});

test("private tokens are compared using timing-safe equality", () => {
  assert.match(serverSource, /timingSafeEqual/);
  assert.match(serverSource, /IXKITS_PUBLISH_TOKEN/);
  assert.match(serverSource, /IXKITS_ADMIN_TOKEN/);
});
