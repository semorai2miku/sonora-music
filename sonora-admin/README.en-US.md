# Sonora Music Admin

The Sonora Music administration console is built with Vue 3, TypeScript, Element Plus, Pinia, Vite, and pure-admin-thin.

It manages client users, artists, albums, songs, playlists, banners, uploads, and content status. Authentication uses the Sonora backend's JWT and dynamic route APIs.

## Development

Requirements: Node.js `^20.19.0` or `>=22.13.0`, pnpm 9+, and the backend at `http://localhost:8080`.

```bash
pnpm install
pnpm dev
```

Open `http://localhost:8848`. The development credentials are `sonora-music` / `admin123`.

## Build

```bash
pnpm typecheck
pnpm build
```

See the [project README](../README.md), [API reference](../docs/API.md), and [deployment guide](../docs/DEPLOYMENT.md).

This module is based on `pure-admin/pure-admin-thin` and retains its MIT license. See `LICENSE` for details.
