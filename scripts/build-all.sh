#!/usr/bin/env bash
# Builds every VANTA product from a clean checkout: core -> client -> launcher -> website.
#
#   ./scripts/build-all.sh [--skip-client] [--skip-launcher] [--skip-website] [--skip-core] [--no-tests]
#
# --skip-client is for machines without access to the Mojang/Fabric hosts (the Fabric mod cannot be
# compiled there; GitHub Actions builds it). Every step prints a clear header and the script stops at
# the first failure. Requires JDK 21 and Node.js 22 on PATH; Gradle comes from the wrappers.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SKIP_CORE=0 SKIP_CLIENT=0 SKIP_LAUNCHER=0 SKIP_WEBSITE=0 RUN_TESTS=1
for arg in "$@"; do
  case "$arg" in
    --skip-core) SKIP_CORE=1 ;;
    --skip-client) SKIP_CLIENT=1 ;;
    --skip-launcher) SKIP_LAUNCHER=1 ;;
    --skip-website) SKIP_WEBSITE=1 ;;
    --no-tests) RUN_TESTS=0 ;;
    -h|--help)
      sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

STARTED=$(date +%s)
step() { printf '\n\033[1;35m==> %s\033[0m\n' "$*"; }
done_step() { printf '\033[1;32m    ok  %s (%ss)\033[0m\n' "$1" "$(( $(date +%s) - $2 ))"; }
require() { command -v "$1" >/dev/null 2>&1 || { echo "error: '$1' is required but not on PATH" >&2; exit 1; }; }

require java
require node
JAVA_MAJOR=$(java -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d. -f1)
if [ "${JAVA_MAJOR:-0}" != "21" ]; then
  echo "error: JDK 21 is required (found Java ${JAVA_MAJOR:-unknown}). See docs/java-21.md." >&2
  exit 1
fi
NODE_MAJOR=$(node --version | sed 's/^v//' | cut -d. -f1)
if [ "${NODE_MAJOR:-0}" -lt 22 ]; then
  echo "error: Node.js 22 or newer is required (found $(node --version))." >&2
  exit 1
fi

GRADLE_ARGS=(--no-daemon --console=plain)
if [ "$RUN_TESTS" -eq 0 ]; then GRADLE_ARGS+=(-x test); fi

if [ "$SKIP_CORE" -eq 0 ]; then
  T=$(date +%s); step "core: ./gradlew build (pure Java 21 library + unit tests)"
  (cd "$ROOT/core" && ./gradlew "${GRADLE_ARGS[@]}" build)
  done_step core "$T"
fi

if [ "$SKIP_CLIENT" -eq 0 ]; then
  T=$(date +%s); step "client: ./gradlew build (Fabric mod for Minecraft 1.21.11; needs Mojang + Fabric hosts)"
  (cd "$ROOT/client" && ./gradlew "${GRADLE_ARGS[@]}" build)
  done_step client "$T"
else
  step "client: skipped (--skip-client)"
fi

if [ "$SKIP_LAUNCHER" -eq 0 ]; then
  T=$(date +%s); step "launcher: ./gradlew build fatJar (JavaFX launcher + unit tests)"
  (cd "$ROOT/launcher" && ./gradlew "${GRADLE_ARGS[@]}" build fatJar)
  done_step launcher "$T"
fi

if [ "$SKIP_WEBSITE" -eq 0 ]; then
  T=$(date +%s); step "website: npm ci && npm run build"
  (cd "$ROOT/website" && npm ci --no-audit --no-fund && if [ "$RUN_TESTS" -eq 1 ]; then npm test; fi && npm run build)
  done_step website "$T"
fi

T=$(date +%s); step "release metadata: validate manifests, content front matter and documentation links"
node "$ROOT/scripts/release/validate-json.mjs" --quiet "$ROOT/shared/schemas/release-manifest.schema.json" "$ROOT"/shared/releases/*.json
# The Local AI manifest is a template (sizes 0, empty model digest) until the Local AI resolve workflow has run and its
# result was committed; that is not an error here or in CI. A resolved manifest must match its schema.
LOCAL_AI_STATUS=$(node "$ROOT/scripts/release/local-ai.mjs" status --root "$ROOT" || true)
if grep -q "not resolved yet" <<< "$LOCAL_AI_STATUS"; then
  if grep -q "template values" <<< "$LOCAL_AI_STATUS"; then
    echo "note: Local AI manifest not resolved yet (shared/local-ai/local-ai.json holds template values); run the Local AI resolve workflow"
  else
    echo "error: shared/local-ai/local-ai.json does not match shared/schemas/local-ai.schema.json" >&2
    echo "$LOCAL_AI_STATUS" >&2
    exit 1
  fi
else
  node "$ROOT/scripts/release/validate-json.mjs" --quiet "$ROOT/shared/schemas/local-ai.schema.json" "$ROOT/shared/local-ai/local-ai.json"
fi
node "$ROOT/scripts/release/validate-json.mjs" --quiet --front-matter "$ROOT/shared/schemas/doc-page.schema.json" "$ROOT"/docs/*.md
node "$ROOT/scripts/release/validate-json.mjs" --quiet --front-matter "$ROOT/shared/schemas/changelog-entry.schema.json" "$ROOT"/website/content/changelog/*.md
node "$ROOT/scripts/release/validate-json.mjs" --quiet --front-matter "$ROOT/shared/schemas/news-post.schema.json" "$ROOT"/website/content/news/*-*.md
node "$ROOT/scripts/release/check-links.mjs" --quiet "$ROOT/docs" "$ROOT/website/content"
done_step "release metadata" "$T"

printf '\n\033[1;32mAll requested builds finished in %ss.\033[0m\n' "$(( $(date +%s) - STARTED ))"
echo "Artifacts:"
[ "$SKIP_CORE" -eq 0 ] && ls -1 "$ROOT"/core/build/libs/*.jar 2>/dev/null | sed 's/^/  /'
[ "$SKIP_CLIENT" -eq 0 ] && ls -1 "$ROOT"/client/build/libs/*.jar 2>/dev/null | sed 's/^/  /'
[ "$SKIP_LAUNCHER" -eq 0 ] && ls -1 "$ROOT"/launcher/build/libs/*-all.jar 2>/dev/null | sed 's/^/  /'
[ "$SKIP_WEBSITE" -eq 0 ] && [ -d "$ROOT/website/dist" ] && echo "  $ROOT/website/dist/"
exit 0
