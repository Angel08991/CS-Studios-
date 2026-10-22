# IxKits Community Catalog API

Minimal REST service for the IxKits Minecraft plugin. Uses only Node.js built-in modules.

## Run locally

- Node.js 20 or newer
- npm start
- Health check: http://localhost:3000/health

Set environment variables before starting:

- PORT: server port (platform-provided on Render)
- IXKITS_PUBLISH_TOKEN: required Bearer token for publishing templates
- IXKITS_ADMIN_TOKEN: required Bearer token for removing templates
- IXKITS_DATA_DIR: persistent directory for catalog.json; mount a persistent disk here in production
- IXKITS_CORS_ORIGIN: optional allowed browser origin; default * is convenient for a development API

Do not commit real tokens. Use different random secrets for publish and admin tokens.

## API

- GET /health
- GET /api/templates?query=&category=&version=&page=1&limit=20
- GET /api/templates/:id
- GET /api/templates/:id/versions — list current and previous revision metadata
- GET /api/templates/:id/download — download the current revision
- POST /api/templates/:id/versions — publish a new revision using the publish token; retains up to 30 older revisions
- POST /api/templates — Bearer publish token. JSON fields: title, author, category, description, mcVersion, license, projectYaml.
- POST /api/templates/:id/rate — JSON: reviewerId, value (1–5)
- POST /api/templates/:id/favorite — JSON: userId; toggles favorite
- DELETE /api/templates/:id — Bearer admin token

The project payload is stored as inert text and returned as inert text; this server does not load YAML, execute commands, or run uploaded content. Revisions preserve previous YAML payloads and changelogs for up to 30 older versions per template. The game plugin's importer must still validate the downloaded project before use.

## Security and deployment notes

This is a small MVP, not a fully moderated commercial marketplace. Public users can currently submit their own reviewerId/userId; ratings and favorites are therefore convenience signals, not verified identities. Before wide public launch, add real account authentication, stronger abuse controls, content moderation and a database such as PostgreSQL. In-memory IP rate limiting resets on restart, so put a proxy/CDN with request limits in front of the service for a public deployment.

On Render, create a Web Service from this folder, Build Command npm install, Start Command npm start, set the environment variables, and attach a persistent disk at the configured data directory.
