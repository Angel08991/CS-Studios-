"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const net = require("node:net");
const { spawn } = require("node:child_process");

const serverPath = path.join(__dirname, "..", "server.js");
const serverSource = fs.readFileSync(serverPath, "utf8");
const publishToken = "test-publish-token";
const adminToken = "test-admin-token";
const interactionToken = "test-interaction-token";
let port;
let baseUrl;
let dataDir;
let child;

async function freePort() {
  const server = net.createServer();
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const address = server.address();
  const result = address.port;
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  return result;
}

test("catalog source contains upload limits and safe token comparison", () => {
  assert.match(serverSource, /MAX_PROJECT_BYTES = 500 \* 1024/);
  assert.match(serverSource, /MAX_BODY_BYTES = 700 \* 1024/);
  assert.match(serverSource, /MAX_RESOURCE_BYTES = 24 \* 1024/);
  assert.match(serverSource, /isValidResource/);
  assert.match(serverSource, /timingSafeEqual/);
  assert.match(serverSource, /configuredTokenRequired/);
  assert.match(serverSource, /IXKITS_PUBLISH_TOKEN/);
  assert.match(serverSource, /IXKITS_ADMIN_TOKEN/);
  assert.match(serverSource, /IXKITS_INTERACTION_TOKEN/);
  assert.match(serverSource, /format-version\|version/);
});

test("catalog API handles publish, search, ratings, favorites, downloads, and moderation", async t => {
  port = await freePort();
  baseUrl = "http://127.0.0.1:" + port;
  dataDir = await fsp.mkdtemp(path.join(os.tmpdir(), "ixkits-catalog-test-"));
  child = spawn(process.execPath, [serverPath], {
    env: {
      ...process.env,
      PORT: String(port),
      HOST: "127.0.0.1",
      IXKITS_DATA_DIR: dataDir,
      IXKITS_PUBLISH_TOKEN: publishToken,
      IXKITS_ADMIN_TOKEN: adminToken,
      IXKITS_INTERACTION_TOKEN: interactionToken,
      IXKITS_CORS_ORIGIN: "http://localhost"
    },
    stdio: "ignore"
  });
  t.after(async () => {
    if (child && child.exitCode === null) {
      child.kill("SIGTERM");
      await new Promise(resolve => {
        const timeout = setTimeout(resolve, 1000);
        child.once("exit", () => { clearTimeout(timeout); resolve(); });
      });
    }
    if (dataDir) await fsp.rm(dataDir, { recursive: true, force: true });
  });

  let healthy = false;
  for (let attempt = 0; attempt < 60; attempt++) {
    try {
      const response = await fetch(baseUrl + "/health");
      if (response.ok) { healthy = true; break; }
    } catch { }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert.equal(healthy, true, "catalog service should become healthy");

  const health = await fetch(baseUrl + "/health").then(response => response.json());
  assert.equal(health.service, "ixkits-catalog");

  const projectYaml = "format-version: 2\nname: Test PvP Kit\npages:\n  0:\n    10: DIAMOND_SWORD\n";
  const payload = {
    title: "Test PvP Kit",
    author: "CS Studios test",
    category: "pvp",
    description: "Integration test fixture",
    mcVersion: "1.21.11",
    license: "test",
    projectYaml
  };

  const rejected = await fetch(baseUrl + "/api/templates", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload)
  });
  assert.equal(rejected.status, 401);

  const publish = await fetch(baseUrl + "/api/templates", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: "Bearer " + publishToken
    },
    body: JSON.stringify(payload)
  });
  assert.equal(publish.status, 201);
  const created = await publish.json();
  assert.ok(created.id);
  assert.equal(created.title, payload.title);
  assert.equal("projectYaml" in created, false, "list-style response must not expose the full project YAML");

  const search = await fetch(baseUrl + "/api/templates?query=PvP&category=pvp&limit=10").then(response => response.json());
  assert.equal(search.total, 1);
  assert.equal(search.items[0].id, created.id);
  assert.equal(search.items[0].revision, 1);

  const revisedYaml = projectYaml + "# Revision two\n";
  const revise = await fetch(baseUrl + "/api/templates/" + created.id + "/versions", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: "Bearer " + publishToken
    },
    body: JSON.stringify({ projectYaml: revisedYaml, mcVersion: "1.21.11", changelog: "Test revision" })
  });
  assert.equal(revise.status, 200);
  assert.equal((await revise.json()).revision, 2);

  const versions = await fetch(baseUrl + "/api/templates/" + created.id + "/versions").then(response => response.json());
  assert.equal(versions.currentRevision, 2);
  assert.equal(versions.versions.length, 2);
  assert.equal(versions.versions[0].revision, 1);
  assert.equal(versions.versions[1].revision, 2);

  const deniedRating = await fetch(baseUrl + "/api/templates/" + created.id + "/rate", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ reviewerId: "spoofed-user", value: 1 })
  });
  assert.equal(deniedRating.status, 401, "ratings must require a trusted server token");

  const rating = await fetch(baseUrl + "/api/templates/" + created.id + "/rate", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: "Bearer " + interactionToken
    },
    body: JSON.stringify({ reviewerId: "tester-1", value: 5 })
  });
  assert.equal(rating.status, 200);
  assert.equal((await rating.json()).averageRating, 5);

  const favorite = await fetch(baseUrl + "/api/templates/" + created.id + "/favorite", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: "Bearer " + interactionToken
    },
    body: JSON.stringify({ userId: "tester-1" })
  });
  assert.equal(favorite.status, 200);
  assert.equal((await favorite.json()).favorite, true);

  const downloaded = await fetch(baseUrl + "/api/templates/" + created.id + "/download").then(response => response.json());
  assert.equal(downloaded.projectYaml, revisedYaml);
  assert.equal(downloaded.revision, 2);
  assert.equal(downloaded.format, "ixkits-yaml");

  const lorePayload = {
    type: "lore",
    title: "Cosmic Lore",
    author: "CS Studios test",
    description: "Community lore fixture",
    data: {
      display: "&d✦ &lObjeto Cósmico",
      display_en: "&d✦ &lCosmic Item",
      lore: ["&d✦ &lOBJETO CÓSMICO", "&7Nacido entre estrellas", "", "&b➤ &fUn lore de prueba"],
      lore_en: ["&d✦ &lCOSMIC ITEM", "&7Made among the stars", "", "&b➤ &fA test lore"]
    }
  };
  const deniedResource = await fetch(baseUrl + "/api/resources", {
    method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(lorePayload)
  });
  assert.equal(deniedResource.status, 401, "community resource publishing must require the publish token");

  const resourcePublish = await fetch(baseUrl + "/api/resources", {
    method: "POST",
    headers: { "content-type": "application/json", authorization: "Bearer " + publishToken },
    body: JSON.stringify(lorePayload)
  });
  assert.equal(resourcePublish.status, 201);
  const resourceCreated = await resourcePublish.json();
  assert.equal(resourceCreated.type, "lore");
  assert.equal(resourceCreated.title, lorePayload.title);
  assert.equal("data" in resourceCreated, false, "resource summaries must not expose resource data");

  const resourceSearch = await fetch(baseUrl + "/api/resources?type=lore&query=cosmic&limit=10").then(response => response.json());
  assert.equal(resourceSearch.total, 1);
  assert.equal(resourceSearch.items[0].id, resourceCreated.id);

  const resourceDownload = await fetch(baseUrl + "/api/resources/" + resourceCreated.id + "/download").then(response => response.json());
  assert.equal(resourceDownload.data.lore.length, 4);
  assert.equal(resourceDownload.data.lore[0], "&d✦ &lOBJETO CÓSMICO");
  assert.equal(resourceDownload.data.lore_en.length, 4);
  assert.equal(resourceDownload.data.lore_en[0], "&d✦ &lCOSMIC ITEM");
  assert.equal(resourceDownload.data.display, "&d✦ &lObjeto Cósmico");
  assert.equal(resourceDownload.data.display_en, "&d✦ &lCosmic Item");
  assert.equal(resourceDownload.downloads, 1);

  const invalidResource = await fetch(baseUrl + "/api/resources", {
    method: "POST",
    headers: { "content-type": "application/json", authorization: "Bearer " + publishToken },
    body: JSON.stringify({ type: "palette", title: "Bad Palette", author: "Tester", data: { color: "not-a-color" } })
  });
  assert.equal(invalidResource.status, 400, "resource payloads must be type-validated");

  const invalidEnchantments = await fetch(baseUrl + "/api/resources", {
    method: "POST",
    headers: { "content-type": "application/json", authorization: "Bearer " + publishToken },
    body: JSON.stringify({
      type: "enchantments",
      title: "Bad Enchantment Preset",
      author: "Tester",
      data: { "display.nested": 7 }
    })
  });
  assert.equal(invalidEnchantments.status, 400, "enchantment keys must use safe identifier syntax");

  const deniedDelete = await fetch(baseUrl + "/api/templates/" + created.id, { method: "DELETE" });
  assert.equal(deniedDelete.status, 401);

  const remove = await fetch(baseUrl + "/api/templates/" + created.id, {
    method: "DELETE",
    headers: { authorization: "Bearer " + adminToken }
  });
  assert.equal(remove.status, 200);

  const gone = await fetch(baseUrl + "/api/templates/" + created.id);
  assert.equal(gone.status, 404);

});


test("catalog refuses to overwrite a corrupted existing database", async t => {
  const corruptPort = await freePort();
  const corruptBaseUrl = "http://127.0.0.1:" + corruptPort;
  const corruptDataDir = await fsp.mkdtemp(path.join(os.tmpdir(), "ixkits-catalog-corrupt-"));
  const corruptFile = path.join(corruptDataDir, "catalog.json");
  const originalContents = "{ definitely not valid JSON";
  await fsp.writeFile(corruptFile, originalContents, "utf8");

  const corruptChild = spawn(process.execPath, [serverPath], {
    env: {
      ...process.env,
      PORT: String(corruptPort),
      HOST: "127.0.0.1",
      IXKITS_DATA_DIR: corruptDataDir,
      IXKITS_PUBLISH_TOKEN: publishToken,
      IXKITS_ADMIN_TOKEN: adminToken
    },
    stdio: "ignore"
  });

  t.after(async () => {
    if (corruptChild && corruptChild.exitCode === null) {
      corruptChild.kill("SIGTERM");
      await new Promise(resolve => {
        const timeout = setTimeout(resolve, 1000);
        corruptChild.once("exit", () => { clearTimeout(timeout); resolve(); });
      });
    }
    await fsp.rm(corruptDataDir, { recursive: true, force: true });
  });

  let healthy = false;
  for (let attempt = 0; attempt < 60; attempt++) {
    try {
      const response = await fetch(corruptBaseUrl + "/health");
      if (response.ok) { healthy = true; break; }
    } catch { }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert.equal(healthy, true, "catalog process should start even if the database needs recovery");

  const response = await fetch(corruptBaseUrl + "/api/templates");
  assert.equal(response.status, 500, "API should report database recovery is required");
  assert.equal(await fsp.readFile(corruptFile, "utf8"), originalContents,
    "the original catalog file must remain untouched for recovery");
});


test("catalog responds clearly when protected-action tokens are not configured", async t => {
  const missingTokenPort = await freePort();
  const missingTokenBaseUrl = "http://127.0.0.1:" + missingTokenPort;
  const missingTokenDataDir = await fsp.mkdtemp(path.join(os.tmpdir(), "ixkits-catalog-no-token-"));
  const missingTokenChild = spawn(process.execPath, [serverPath], {
    env: {
      ...process.env,
      PORT: String(missingTokenPort),
      HOST: "127.0.0.1",
      IXKITS_DATA_DIR: missingTokenDataDir
    },
    stdio: "ignore"
  });

  t.after(async () => {
    if (missingTokenChild && missingTokenChild.exitCode === null) {
      missingTokenChild.kill("SIGTERM");
      await new Promise(resolve => {
        const timeout = setTimeout(resolve, 1000);
        missingTokenChild.once("exit", () => { clearTimeout(timeout); resolve(); });
      });
    }
    await fsp.rm(missingTokenDataDir, { recursive: true, force: true });
  });

  let healthy = false;
  for (let attempt = 0; attempt < 60; attempt++) {
    try {
      const response = await fetch(missingTokenBaseUrl + "/health");
      if (response.ok) { healthy = true; break; }
    } catch { }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert.equal(healthy, true, "catalog process should start");

  const publish = await fetch(missingTokenBaseUrl + "/api/templates", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ title: "Kit", author: "Tester", category: "pvp", mcVersion: "1.21.11", projectYaml: "format-version: 2\npages: {}\n" })
  });
  assert.equal(publish.status, 503);
  assert.match((await publish.json()).error, /not configured/);

  const rating = await fetch(missingTokenBaseUrl + "/api/templates/missing/rate", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ reviewerId: "tester", value: 5 })
  });
  assert.equal(rating.status, 503);

  const moderation = await fetch(missingTokenBaseUrl + "/api/templates/missing", { method: "DELETE" });
  assert.equal(moderation.status, 503);
});
