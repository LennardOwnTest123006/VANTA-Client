#!/usr/bin/env bash
# Prints the public API (javap) of selected Minecraft 1.21.11 classes from Loom's Mojang-mapped jars.
# Development aid only: the environment that writes VANTA code cannot download Minecraft, so CI prints
# the signatures to the job log where they can be read back. Nothing is persisted to the repository.
set -euo pipefail
LIST="${1:-$(dirname "$0")/api-classes.txt}"
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/fabric-loom"
mapfile -t JARS < <(find "$CACHE" -path "*minecraftMaven*" -name "*.jar" ! -name "*-sources*" 2>/dev/null | sort)
if [ "${#JARS[@]}" -eq 0 ]; then
  echo "::warning::No Loom Minecraft jars found under $CACHE (build probably failed before Loom set up Minecraft)"
  { find "$CACHE" -maxdepth 3 -type d 2>/dev/null || true; } | head -50
  exit 0
fi
printf 'Using jars:\n'; printf '  %s\n' "${JARS[@]}"
CP=$(IFS=:; echo "${JARS[*]}")
echo "::group::Class index (client gui/input/platform)"
for j in "${JARS[@]}"; do unzip -Z1 "$j" 2>/dev/null || true; done \
  | grep -E '^(net/minecraft/client/(gui|input|[A-Za-z]+\.class)|com/mojang/blaze3d/platform|net/minecraft/client/renderer/[A-Za-z]+\.class|net/minecraft/client/multiplayer/[A-Za-z]+\.class|net/minecraft/server/packs/repository)' \
  | grep -v '\$[0-9]' | sed 's/\.class$//' | sort -u
echo "::endgroup::"
while IFS= read -r cls; do
  [ -z "$cls" ] && continue
  echo "::group::$cls"
  javap -public -cp "$CP" "$cls" 2>&1 | grep -vE '^Compiled from|^\s*$' || echo "MISSING $cls"
  echo "::endgroup::"
done < "$LIST"
