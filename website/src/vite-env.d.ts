/// <reference types="vite/client" />

/**
 * Typed `import.meta.env` for the VANTA website. All variables are optional; see `.env.example`
 * and `src/lib/env.ts` for defaults and validation.
 */
interface ImportMetaEnv {
  readonly VITE_DOWNLOAD_LAUNCHER_URL?: string;
  readonly VITE_DOWNLOAD_CLIENT_URL?: string;
  readonly VITE_RELEASES_BASE_URL?: string;
  readonly VITE_SUPPORT_EMAIL?: string;
  readonly VITE_DISCORD_URL?: string;
  readonly VITE_GITHUB_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
