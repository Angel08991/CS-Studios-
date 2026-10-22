"use strict";

const http = require("node:http");
const fs = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const { URL } = require("node:url");

const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || "0.0.0.0";
const DATA_DIR = process.env.IXKITS_DATA_DIR || path.join(__dirname, "data");
const DB_FILE = path.join(DATA_DIR, "catalog.json");
const PUBLISH_TOKEN = process.env.IXKITS_PUBLISH_TOKEN || "";
const ADMIN_TOKEN = process.env.IXKITS_ADMIN_TOKEN || "";
const INTERACTION_TOKEN = process.env.IXKITS_INTERACTION_TOKEN || "";
const MAX_BODY_BYTES = 700 * 1024;
const MAX_PROJECT_BYTES = 500 * 1024;
const PAGE_SIZE_MAX = 50;
const rateBuckets = new Map();

function send(res, status, payload) {
  const body = Buffer.from(JSON.stringify(payload));
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": body.length,
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "access-control-allow-origin": process.env.IXKITS_CORS_ORIGIN || "*",
    "access-control-allow-methods": "GET,POST,DELETE,OPTIONS",
    "access-control-allow-headers": "content-type,authorization"
  });
  res.end(body);
}

function bearer(req) {
  const header = String(req.headers.authorization || "");
  return header.startsWith("Bearer ") ? header.slice(7).trim() : "";
}

function constantTimeEqual(a, b) {
  if (!a || !b) return false;
  const aa = Buffer.from(a);
  const bb = Buffer.from(b);
  return aa.length === bb.length && crypto.timingSafeEqual(aa, bb);
}

function rateLimit(req, res, maximum = 120, windowMs = 60000) {
  const now = Date.now();
  const address = String(req.socket.remoteAddress || "unknown");
  let bucket = rateBuckets.get(address);
  if (!bucket || now - bucket.startedAt >= windowMs) bucket = { startedAt: now, count: 0 };
  bucket.count += 1;
  rateBuckets.set(address, bucket);
  if (rateBuckets.size > 10000) {
    for (const [key, value] of rateBuckets) if (now - value.startedAt > windowMs * 2) rateBuckets.delete(key);
  }
  if (bucket.count > maximum) {
    send(res, 429, { error: "Too many requests. Try again shortly." });
    return false;
  }
  return true;
}

async function readBody(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) {
      const error = new Error("Request body is too large.");
      error.status = 413;
      throw error;
    }
    chunks.push(chunk);
  }
  const raw = Buffer.concat(chunks).toString("utf8");
  try { return raw ? JSON.parse(raw) : {}; }
  catch {
    const error = new Error("Request body must be valid JSON.");
    error.status = 400;
    throw error;
  }
}

function text(value, max = 160) {
  return typeof value === "string" ? value.trim().replace(/\0/g, "").slice(0, max) : "";
}

function publicTemplate(template) {
  const { projectYaml, ratings, favorites, revisions, ...publicData } = template;
  const ratingValues = Object.values(ratings || {}).map(value => Number(value.value)).filter(value => value >= 1 && value <= 5);
  publicData.averageRating = ratingValues.length
    ? Math.round((ratingValues.reduce((sum, value) => sum + value, 0) / ratingValues.length) * 100) / 100
    : null;
  publicData.ratingCount = ratingValues.length;
  publicData.favoriteCount = Array.isArray(favorites) ? favorites.length : 0;
  return publicData;
}

function isValidProjectYaml(projectYaml) {
  if (typeof projectYaml !== "string" || Buffer.byteLength(projectYaml, "utf8") > MAX_PROJECT_BYTES) return false;
  if (!/^\s*(format-version|version)\s*:/m.test(projectYaml)) return false;
  if (!/^\s*pages\s*:/m.test(projectYaml)) return false;
  if (/^\s*(commands|command-execution|script|scripts)\s*:/mi.test(projectYaml)) return false;
  return true;
}

function slug(value) {
  return text(value, 100).toLowerCase().replace(/[^a-z0-9_-]+/g, "-").replace(/[-_]{2,}/g, "-").replace(/^[-_.]+|[-_.]+$/g, "").slice(0, 80);
}

let dbPromise;

async function loadDb() {
  if (!dbPromise) {
    dbPromise = (async () => {
      await fs.mkdir(DATA_DIR, { recursive: true });
      try {
        const parsed = JSON.parse(await fs.readFile(DB_FILE, "utf8"));
        if (!parsed || !Array.isArray(parsed.templates)) {
          throw new Error("Catalog database has an invalid structure.");
        }
        return parsed;
      } catch (error) {
        if (error.code === "ENOENT") {
          const fresh = { schemaVersion: 1, templates: [] };
          await saveDb(fresh);
          return fresh;
        }
        // Never silently replace an existing catalog that cannot be parsed.
        // Operators can recover the original file instead of losing published data.
        console.error("Catalog database is unreadable; refusing to overwrite it:", error.message);
        throw new Error("Catalog database is unreadable. Restore a valid catalog.json backup before retrying.", { cause: error });
      }
    })().catch(error => {
      dbPromise = undefined;
      throw error;
    });
  }
  return dbPromise;
}

let dbQueue = Promise.resolve();
async function saveDb(db) {
  await fs.mkdir(DATA_DIR, { recursive: true });
  const temp = DB_FILE + ".tmp";
  await fs.writeFile(temp, JSON.stringify(db, null, 2), { mode: 0o600 });
  await fs.rename(temp, DB_FILE);
}

function persist(db) {
  dbQueue = dbQueue.then(() => saveDb(db));
  return dbQueue;
}

function findTemplate(db, id) {
  return db.templates.find(item => item.id === id && item.status === "published");
}

function authRequired(req, res, expected, reason) {
  if (constantTimeEqual(bearer(req), expected)) return true;
  send(res, 401, { error: reason || "Valid bearer token required." });
  return false;
}

function configuredTokenRequired(req, res, expected, scope) {
  if (!expected) {
    send(res, 503, { error: scope + " token is not configured on this service." });
    return false;
  }
  return authRequired(req, res, expected, scope + " token is invalid.");
}

async function handler(req, res) {
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "access-control-allow-origin": process.env.IXKITS_CORS_ORIGIN || "*",
      "access-control-allow-methods": "GET,POST,DELETE,OPTIONS",
      "access-control-allow-headers": "content-type,authorization"
    });
    return res.end();
  }
  if (!rateLimit(req, res, req.method === "GET" ? 180 : 45)) return;
  const url = new URL(req.url, "http://" + (req.headers.host || "localhost"));
  const parts = url.pathname.split("/").filter(Boolean);

  if (req.method === "GET" && url.pathname === "/health") {
    return send(res, 200, { ok: true, service: "ixkits-catalog", version: "0.1.0", time: new Date().toISOString() });
  }
  if (parts[0] !== "api" || parts[1] !== "templates") {
    return send(res, 404, { error: "Route not found." });
  }

  const db = await loadDb();

  if (req.method === "GET" && parts.length === 2) {
    const query = text(url.searchParams.get("query"), 100).toLowerCase();
    const category = text(url.searchParams.get("category"), 60).toLowerCase();
    const version = text(url.searchParams.get("version"), 40).toLowerCase();
    const page = Math.max(1, Number.parseInt(url.searchParams.get("page") || "1", 10) || 1);
    const limit = Math.min(PAGE_SIZE_MAX, Math.max(1, Number.parseInt(url.searchParams.get("limit") || "20", 10) || 20));
    let matches = db.templates.filter(item => item.status === "published");
    if (query) matches = matches.filter(item => [item.title, item.author, item.description, item.category].some(value => String(value || "").toLowerCase().includes(query)));
    if (category) matches = matches.filter(item => String(item.category).toLowerCase() === category);
    if (version) matches = matches.filter(item => String(item.mcVersion).toLowerCase().includes(version));
    matches.sort((a, b) => String(b.publishedAt).localeCompare(String(a.publishedAt)));
    const start = (page - 1) * limit;
    return send(res, 200, { items: matches.slice(start, start + limit).map(publicTemplate), page, limit, total: matches.length });
  }

  const id = parts[2];
  const template = id ? findTemplate(db, id) : null;

  if (req.method === "GET" && parts.length === 3) {
    if (!template) return send(res, 404, { error: "Template not found." });
    return send(res, 200, publicTemplate(template));
  }

  if (req.method === "GET" && parts.length === 4 && parts[3] === "versions") {
    if (!template) return send(res, 404, { error: "Template not found." });
    const history = (Array.isArray(template.revisions) ? template.revisions : []).map(revision => ({
      revision: revision.revision, changelog: revision.changelog, author: revision.author,
      mcVersion: revision.mcVersion, publishedAt: revision.publishedAt
    }));
    history.push({
      revision: Number(template.revision) || 1,
      changelog: text(template.changelog, 300),
      author: template.author,
      mcVersion: template.mcVersion,
      publishedAt: template.updatedAt || template.publishedAt
    });
    return send(res, 200, { id: template.id, currentRevision: Number(template.revision) || 1, versions: history });
  }

  if (req.method === "POST" && parts.length === 4 && parts[3] === "versions") {
    if (!template) return send(res, 404, { error: "Template not found." });
    if (!configuredTokenRequired(req, res, PUBLISH_TOKEN, "Catalog publishing")) return;
    const body = await readBody(req);
    if (!isValidProjectYaml(body.projectYaml)) return send(res, 400, { error: "A valid IxKits project YAML under 500 KB is required." });
    const now = new Date().toISOString();
    template.revisions = Array.isArray(template.revisions) ? template.revisions : [];
    template.revisions.push({
      revision: Number(template.revision) || 1,
      changelog: text(template.changelog, 300),
      author: template.author,
      mcVersion: template.mcVersion,
      publishedAt: template.updatedAt || template.publishedAt,
      projectYaml: template.projectYaml
    });
    if (template.revisions.length > 30) template.revisions.splice(0, template.revisions.length - 30);
    template.revision = (Number(template.revision) || 1) + 1;
    template.projectYaml = body.projectYaml;
    template.changelog = text(body.changelog || "Updated template", 300);
    template.mcVersion = text(body.mcVersion || template.mcVersion, 40);
    template.updatedAt = now;
    await persist(db);
    return send(res, 200, publicTemplate(template));
  }

  if (req.method === "GET" && parts.length === 4 && parts[3] === "download") {
    if (!template) return send(res, 404, { error: "Template not found." });
    template.downloads = (Number(template.downloads) || 0) + 1;
    await persist(db);
    return send(res, 200, { id: template.id, title: template.title, revision: Number(template.revision) || 1, projectYaml: template.projectYaml, format: "ixkits-yaml" });
  }

  if (req.method === "POST" && parts.length === 2) {
    if (!configuredTokenRequired(req, res, PUBLISH_TOKEN, "Catalog publishing")) return;
    const body = await readBody(req);
    const title = text(body.title, 100);
    const author = text(body.author, 80);
    const category = slug(body.category);
    const description = text(body.description, 500);
    const mcVersion = text(body.mcVersion, 40);
    const license = text(body.license || "all-rights-reserved", 50);
    if (!title || !author || !category || !mcVersion || !isValidProjectYaml(body.projectYaml)) {
      return send(res, 400, { error: "Required fields: title, author, category, mcVersion, and a valid IxKits YAML project under 500 KB." });
    }
    const id = slug(title) + "-" + crypto.randomBytes(4).toString("hex");
    const now = new Date().toISOString();
    const created = {
      id, title, author, category, description, mcVersion, license,
      status: "published", publishedAt: now, updatedAt: now, revision: 1, changelog: "Initial publication",
      downloads: 0, projectYaml: body.projectYaml, revisions: [], ratings: {}, favorites: []
    };
    db.templates.push(created);
    await persist(db);
    return send(res, 201, publicTemplate(created));
  }

  if (req.method === "POST" && parts.length === 4 && parts[3] === "rate") {
    if (!configuredTokenRequired(req, res, INTERACTION_TOKEN, "Catalog interactions")) return;
    if (!template) return send(res, 404, { error: "Template not found." });
    const body = await readBody(req);
    const reviewerId = text(body.reviewerId, 80);
    const value = Number(body.value);
    if (!reviewerId || !Number.isInteger(value) || value < 1 || value > 5) {
      return send(res, 400, { error: "reviewerId and a rating value from 1 to 5 are required." });
    }
    template.ratings = template.ratings || {};
    template.ratings[reviewerId] = { value, updatedAt: new Date().toISOString() };
    await persist(db);
    return send(res, 200, publicTemplate(template));
  }

  if (req.method === "POST" && parts.length === 4 && parts[3] === "favorite") {
    if (!configuredTokenRequired(req, res, INTERACTION_TOKEN, "Catalog interactions")) return;
    if (!template) return send(res, 404, { error: "Template not found." });
    const body = await readBody(req);
    const userId = text(body.userId, 80);
    if (!userId) return send(res, 400, { error: "userId is required." });
    template.favorites = Array.isArray(template.favorites) ? template.favorites : [];
    const index = template.favorites.indexOf(userId);
    if (index >= 0) template.favorites.splice(index, 1);
    else if (template.favorites.length < 100000) template.favorites.push(userId);
    await persist(db);
    return send(res, 200, { id: template.id, favorite: index < 0, favoriteCount: template.favorites.length });
  }

  if (req.method === "DELETE" && parts.length === 3) {
    if (!configuredTokenRequired(req, res, ADMIN_TOKEN, "Catalog moderation")) return;
    const target = db.templates.find(item => item.id === id);
    if (!target) return send(res, 404, { error: "Template not found." });
    target.status = "removed";
    target.removedAt = new Date().toISOString();
    await persist(db);
    return send(res, 200, { ok: true, id });
  }

  return send(res, 405, { error: "Method not allowed." });
}

const server = http.createServer(async (req, res) => {
  try { await handler(req, res); }
  catch (error) {
    console.error("Catalog request failed:", error.message);
    send(res, error.status || 500, { error: error.status ? error.message : "Internal server error." });
  }
});

server.listen(PORT, HOST, () => console.log("IxKits catalog listening on " + HOST + ":" + PORT));
