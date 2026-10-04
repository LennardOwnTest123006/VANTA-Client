#!/usr/bin/env bash
# Prints the public API (javap) of selected Minecraft 1.21.11 classes from Loom's Mojang-mapped jars.
# Development aid only: the environment that writes VANTA code cannot download Minecraft, so CI prints
# the signatures to the job log where they can be read back. Nothing is persisted to the repository.
#
#   dump-minecraft-api.sh index            -> prints a class index of the client packages
#   dump-minecraft-api.sh <class-list.txt> -> javap -public for each class in the list
set -uo pipefail
MODE="${1:?index|list-file}"
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
mapfile -t JARS < <(find "$CACHE/fabric-loom" -path "*minecraftMaven*" -name "*.jar" ! -name "*-sources*" 2>/dev/null | sort)
mapfile -t LIBS < <(find "$CACHE/modules-2" \( -name "joml-*.jar" -o -name "brigadier-*.jar" -o -name "authlib-*.jar" -o -name "datafixerupper-*.jar" \) ! -name "*-sources*" 2>/dev/null | sort)
if [ "${#JARS[@]}" -eq 0 ]; then
  echo "::warning::No Loom Minecraft jars found under $CACHE/fabric-loom (build probably failed before Loom set up Minecraft)"
  exit 0
fi
printf 'Using jars:\n'; printf '  %s\n' "${JARS[@]}" "${LIBS[@]}"
CP=$(IFS=:; echo "${JARS[*]}:${LIBS[*]}")
if [ "$MODE" = "index" ]; then
  echo "::group::Class index"
  for j in "${JARS[@]}"; do unzip -Z1 "$j" 2>/dev/null || true; done \
    | grep -E '^(net/minecraft/[A-Za-z]+\.class|net/minecraft/util/[A-Za-z]+\.class|net/minecraft/client/[A-Za-z]+\.class|net/minecraft/client/(gui|input|player|multiplayer|resources/language|renderer/texture|sounds)/|net/minecraft/client/renderer/[A-Za-z]+\.class|com/mojang/blaze3d/(platform|pipeline)/|net/minecraft/server/packs/repository/|net/minecraft/world/level/storage/Level[A-Za-z]*\.class)' \
    | grep -v '\$[0-9]' | sed 's/\.class$//' | sort -u
  echo "::endgroup::"
  exit 0
fi
while IFS= read -r cls; do
  [ -z "$cls" ] && continue
  echo "::group::$cls"
  javap -public -cp "$CP" "$cls" 2>&1 | grep -vE '^Compiled from|^\s*$' || echo "MISSING $cls"
  echo "::endgroup::"
done < "$MODE"
