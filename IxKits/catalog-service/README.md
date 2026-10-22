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
- IXKITS_INTERACTION_TOKEN: required Bearer token for submitting ratings and favorites
- IXKITS_DATA_DIR: persistent directory for catalog.json; mount a persistent disk here in production
- IXKITS_CORS_ORIGIN: optional allowed browser origin; default * is convenient for a development API

Do not commit real tokens. Use separate random secrets for publish, admin, and interaction tokens.

## API

- GET /health
- GET /api/templates?query=&category=&version=&page=1&limit=20
- GET /api/templates/:id
- GET /api/templates/:id/versions — list current and previous revision metadata
- GET /api/templates/:id/download — download the current revision
- POST /api/templates/:id/versions — publish a new revision using the publish token; retains up to 30 older revisions
- POST /api/templates — Bearer publish token. JSON fields: title, author, category, description, mcVersion, license, projectYaml.
- POST /api/templates/:id/rate — interaction Bearer token; JSON: reviewerId, value (1–5)
- POST /api/templates/:id/favorite — interaction Bearer token; JSON: userId; toggles favorite
- DELETE /api/templates/:id — Bearer admin token

The project payload is stored as inert text and returned as inert text; this server does not load YAML, execute commands, or run uploaded content. Revisions preserve previous YAML payloads and changelogs for up to 30 older versions per template. The game plugin's importer must still validate the downloaded project before use.

## Security and deployment notes

This is a small MVP, not a fully moderated commercial marketplace. Rating and favorite writes require the interaction token, so random public callers cannot directly submit arbitrary reviewerId/userId values. The service still trusts the server plugin that holds this token; this is not a replacement for full user-account authentication. Before wide public launch, add real account authentication, stronger abuse controls, content moderation and a database such as PostgreSQL. In-memory IP rate limiting resets on restart, so put a proxy/CDN with request limits in front of the service for a public deployment.

The repository includes a root-level render.yaml Blueprint which creates a Web Service, a persistent disk, and randomly generated publish, admin, and interaction tokens. Import the repository in Render and review the service plan and disk cost before confirming; the Blueprint chooses the Starter plan to support persistent storage. If you deploy manually, use this folder as Root Directory, Build Command npm install, Start Command npm start, and configure a persistent disk at IXKITS_DATA_DIR.
