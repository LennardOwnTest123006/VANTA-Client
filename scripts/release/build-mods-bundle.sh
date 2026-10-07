#!/usr/bin/env bash
# Builds vanta-client-<version>-mods.zip, the "unzip into .minecraft" download of a client release:
#
#   INSTALL.txt                          scripts/release/mods-bundle/INSTALL.txt with the pinned versions, the
#                                        Performance pack list and the notes about it filled in
#   PERFORMANCE-PACK.txt                 the resolver's summary (performance-pack.mjs)
#   SHA256SUMS                           sha256sum -c compatible, paths relative to the zip root
#   THIRD-PARTY-LICENSES.txt             licence texts of every third-party jar (performance-pack.mjs)
#   mods/vanta-client-<version>.jar      the client jar, unchanged
#   mods/fabric-api-<fabric api>.jar     the unmodified Fabric API jar, unchanged
#   mods/<performance pack jars>         the unmodified Modrinth release files performance-pack.mjs resolved,
#                                        downloaded and verified (in pack order)
#   performance-pack.json                the resolved pack, machine-readable (shared/schemas/performance-pack.schema.json)
#
#   scripts/release/build-mods-bundle.sh --version <version> --client-jar <jar> --fabric-api-jar <jar> \
#       --pack-dir <dir> --out <dir>
#
# --pack-dir is the --out directory of `node scripts/release/performance-pack.mjs`: it holds performance-pack.json,
# PERFORMANCE-PACK.txt, THIRD-PARTY-LICENSES.txt and mods/<jar> for every item of the JSON. Every pack jar is checked
# against the SHA-512 recorded in performance-pack.json before it is staged, and THIRD-PARTY-LICENSES.txt must hold a
# "File:     mods/<jar>" section for every third-party jar of the zip (each pack jar and the Fabric API jar), so the
# bundle never ships a jar without its notice. Minecraft and Fabric Loader versions come from client/gradle.properties;
# the Fabric API version comes from the jar's file name and must match fabric_api_version there. The script verifies
# SHA256SUMS before zipping and again on a fresh extraction of the finished zip, and checks the zip holds exactly the
# expected entries.
# Needs bash, node, zip, unzip and sha256sum/sha512sum (or shasum). Prints the zip path on the last line.
set -euo pipefail

usage() {
  echo "Usage: $0 --version <version> --client-jar <jar> --fabric-api-jar <jar> --pack-dir <dir> --out <dir>" >&2
  exit 2
}

VERSION="" CLIENT_JAR="" FAPI_JAR="" PACK_DIR="" OUT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="${2:-}"; shift 2 || usage ;;
    --client-jar) CLIENT_JAR="${2:-}"; shift 2 || usage ;;
    --fabric-api-jar) FAPI_JAR="${2:-}"; shift 2 || usage ;;
    --pack-dir) PACK_DIR="${2:-}"; shift 2 || usage ;;
    --out) OUT="${2:-}"; shift 2 || usage ;;
    -h|--help) usage ;;
    *) echo "error: unknown argument '$1'" >&2; usage ;;
  esac
done
if [ -z "$VERSION" ] || [ -z "$CLIENT_JAR" ] || [ -z "$FAPI_JAR" ] || [ -z "$PACK_DIR" ] || [ -z "$OUT" ]; then
  usage
fi

fail() { echo "error: $*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEMPLATE="$SCRIPT_DIR/mods-bundle/INSTALL.txt"
PROPS="$ROOT/client/gradle.properties"

prop() { sed -n "s/^$1=//p" "$PROPS" | tr -d '\r' | head -n 1; }
MINECRAFT_VERSION="$(prop minecraft_version)"
LOADER_VERSION="$(prop loader_version)"
PINNED_FAPI="$(prop fabric_api_version)"

SAFE='^[0-9A-Za-z.+-]+$'
for value in "$VERSION" "$MINECRAFT_VERSION" "$LOADER_VERSION" "$PINNED_FAPI"; do
  [[ "$value" =~ $SAFE ]] || fail "unexpected version value '$value' (client/gradle.properties or --version)"
done

[ -f "$CLIENT_JAR" ] || fail "client jar '$CLIENT_JAR' does not exist"
[ -s "$CLIENT_JAR" ] || fail "client jar '$CLIENT_JAR' is empty"
[ -f "$FAPI_JAR" ] || fail "Fabric API jar '$FAPI_JAR' does not exist"
[ -s "$FAPI_JAR" ] || fail "Fabric API jar '$FAPI_JAR' is empty"
CLIENT_NAME="vanta-client-${VERSION}.jar"
[ "$(basename "$CLIENT_JAR")" = "$CLIENT_NAME" ] || fail "client jar must be named $CLIENT_NAME, got $(basename "$CLIENT_JAR")"
FAPI_NAME="$(basename "$FAPI_JAR")"
FABRIC_API_VERSION="${FAPI_NAME#fabric-api-}"
FABRIC_API_VERSION="${FABRIC_API_VERSION%.jar}"
[ "$FAPI_NAME" = "fabric-api-${PINNED_FAPI}.jar" ] || fail "Fabric API jar must be fabric-api-${PINNED_FAPI}.jar (client/gradle.properties), got $FAPI_NAME"

[ -d "$PACK_DIR" ] || fail "pack dir '$PACK_DIR' does not exist (run scripts/release/performance-pack.mjs --out <dir> first)"
PACK_JSON="$PACK_DIR/performance-pack.json"
for name in performance-pack.json PERFORMANCE-PACK.txt THIRD-PARTY-LICENSES.txt; do
  [ -s "$PACK_DIR/$name" ] || fail "pack dir has no $name (or it is empty)"
done

if command -v sha256sum >/dev/null 2>&1; then
  SHA256=(sha256sum)
elif command -v shasum >/dev/null 2>&1; then
  SHA256=(shasum -a 256)
else
  fail "neither sha256sum nor shasum is available"
fi
if command -v sha512sum >/dev/null 2>&1; then
  SHA512=(sha512sum)
elif command -v shasum >/dev/null 2>&1; then
  SHA512=(shasum -a 512)
else
  fail "neither sha512sum nor shasum is available"
fi
command -v node >/dev/null 2>&1 || fail "node is not installed (needed to read performance-pack.json)"
command -v zip >/dev/null 2>&1 || fail "zip is not installed"
command -v unzip >/dev/null 2>&1 || fail "unzip is not installed"

# performance-pack.json -> one tab-separated line per item (slug, file, title, version, licence id, sha512) and per
# excluded member (prefixed with "excluded"), in pack order. Only node parses JSON here; nothing else about it is
# trusted. Titles and slugs go into INSTALL.txt, so they must be plain printable text without tabs or line breaks.
# Pack file names must pass the one safe-name rule the resolver and the in-game installer apply
# (performance-pack.mjs isSafeFilename, the same as core ModrinthFile.isSafeFilename: no path separator or ':', no
# '..', no leading dot, no control character, none of <>"|?*, at most 200 characters) and end in .jar; spaces and
# parentheses are allowed, exactly as Modrinth names release files. Every later use quotes "$file".
PACK_ROWS="$(node --input-type=module -e '
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
const { isSafeFilename } = await import(pathToFileURL(process.argv[2]).href);
const pack = JSON.parse(readFileSync(process.argv[1], "utf8"));
if (pack.schemaVersion !== 1 || !Array.isArray(pack.items) || !Array.isArray(pack.excluded)) {
  console.error("performance-pack.json: unexpected shape"); process.exit(1);
}
const plain = (value, what) => {
  if (typeof value !== "string" || value.trim() === "" || /[^\x20-\x7e]/.test(value)) {
    console.error(`performance-pack.json: ${what} is not plain printable text: ${JSON.stringify(value)}`); process.exit(1);
  }
  return value;
};
const jar = (value) => {
  if (!isSafeFilename(value) || !/\.jar$/i.test(value) || value.startsWith("-")) {
    console.error(`performance-pack.json names an unsafe pack file '\''${String(value)}'\''`); process.exit(1);
  }
  return value;
};
for (const i of pack.items) console.log(["item", plain(i.slug, "slug"), jar(i.file), plain(i.title, "title"), plain(i.versionNumber, "versionNumber"), plain(i.license?.id, "license.id"), i.sha512].join("\t"));
for (const e of pack.excluded) console.log(["excluded", plain(e.title, "excluded title"), plain(e.license, "excluded licence"), e.reason].join("\t"));
' "$PACK_JSON" "$SCRIPT_DIR/performance-pack.mjs")" || fail "could not read $PACK_JSON"

PACK_FILES=()
PACK_SLUGS=()
PACK_TITLES=()
PACK_LIST=""
PACK_EXCLUDED=""
while IFS=$'\t' read -r kind a b c d e f; do
  case "$kind" in
    item)
      slug="$a"; file="$b"; title="$c"; number="$d"; licence="$e"; sha="$f"
      # The node listing already applied the shared rule; this only guards the file name's use as a path and as a
      # command argument (sha512sum, cmp) should the listing ever change.
      case "$file" in
        ''|-*|.*|*/*|*\\*|*..*) fail "performance-pack.json names an unsafe pack file '$file'" ;;
      esac
      [ "$file" != "$CLIENT_NAME" ] && [ "$file" != "$FAPI_NAME" ] || fail "pack file '$file' collides with a bundle jar"
      for seen in "${PACK_FILES[@]+"${PACK_FILES[@]}"}"; do
        [ "$seen" != "$file" ] || fail "performance-pack.json lists '$file' twice"
      done
      [[ "$sha" =~ ^[0-9a-f]{128}$ ]] || fail "performance-pack.json has no SHA-512 for '$file'"
      [ -s "$PACK_DIR/mods/$file" ] || fail "pack jar '$PACK_DIR/mods/$file' is missing or empty"
      actual="$(cd "$PACK_DIR/mods" && "${SHA512[@]}" "$file" | cut -d' ' -f1)"
      [ "$actual" = "$sha" ] || fail "pack jar '$file': SHA-512 $actual does not match performance-pack.json ($sha)"
      PACK_FILES+=("$file")
      PACK_SLUGS+=("$slug")
      PACK_TITLES+=("$title")
      PACK_LIST="${PACK_LIST}   - ${file}   (${title} ${number}, ${licence})"$'\n'
      ;;
    excluded)
      PACK_EXCLUDED="${PACK_EXCLUDED}   - ${a} is NOT in this archive: its licence does not allow redistribution"$'\n'
      PACK_EXCLUDED="${PACK_EXCLUDED}     (${b}). The game offers it with one click under Mods & Shaders"$'\n'
      PACK_EXCLUDED="${PACK_EXCLUDED}     (Performance pack card); nothing is downloaded without a click."$'\n'
      ;;
    *) fail "unexpected row in performance-pack.json listing: $kind" ;;
  esac
done <<< "$PACK_ROWS"
[ "${#PACK_FILES[@]}" -gt 0 ] || fail "performance-pack.json lists no bundled items"
[ -n "$PACK_EXCLUDED" ] || PACK_EXCLUDED="   - Every Performance pack mod is in this archive."$'\n'

# Every third-party jar of the zip needs its section in THIRD-PARTY-LICENSES.txt ("File:     mods/<jar>", the line
# performance-pack.mjs writes); the Fabric API notice is there only when the resolver ran with --fabric-api-jar.
NOTICES="$PACK_DIR/THIRD-PARTY-LICENSES.txt"
for file in "${PACK_FILES[@]}" "$FAPI_NAME"; do
  # Every ERE metacharacter in the name is escaped (Modrinth file names may hold parentheses, plus signs and spaces).
  pattern="$(printf '%s' "$file" | sed 's/[][\\.^$*+?(){}|]/\\&/g')"
  grep -qE "^File: +mods/${pattern}\$" "$NOTICES" \
    || fail "THIRD-PARTY-LICENSES.txt has no section for mods/$file (run performance-pack.mjs with --fabric-api-jar)"
done

# The notes under the pack list are generated from what is really in the archive: the Iris/Sodium pairing only when
# both are bundled, and the duplicate-mod warning names exactly the bundled mods.
has_slug() { local s; for s in "${PACK_SLUGS[@]}"; do [ "$s" = "$1" ] && return 0; done; return 1; }
PACK_NOTES=""
if has_slug sodium && has_slug iris; then
  PACK_NOTES="${PACK_NOTES}   - Iris and Sodium belong together: the Iris and Sodium builds in this archive were resolved as a pair"$'\n'
  PACK_NOTES="${PACK_NOTES}     (Iris requires Sodium). Always copy or update the two together."$'\n'
fi
TITLE_LIST=""
for i in "${!PACK_TITLES[@]}"; do
  if [ "$i" -eq 0 ]; then TITLE_LIST="${PACK_TITLES[$i]}"
  elif [ "$i" -eq $((${#PACK_TITLES[@]} - 1)) ]; then TITLE_LIST="${TITLE_LIST} or ${PACK_TITLES[$i]}"
  else TITLE_LIST="${TITLE_LIST}, ${PACK_TITLES[$i]}"
  fi
done
PACK_NOTES="${PACK_NOTES}   WARNING: your mods folder must not already contain another copy of one of these mods:"$'\n'
PACK_NOTES="${PACK_NOTES}   ${TITLE_LIST}."$'\n'
PACK_NOTES="${PACK_NOTES}   Fabric refuses to start when two copies of one mod id are present (\"Duplicate mod\" error). Delete the"$'\n'
PACK_NOTES="${PACK_NOTES}   older jar before you copy the new one; a leftover \".jar.disabled\" file is fine."$'\n'

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
BUNDLE="$WORK/bundle"
mkdir -p "$BUNDLE/mods"
cp "$CLIENT_JAR" "$BUNDLE/mods/$CLIENT_NAME"
cp "$FAPI_JAR" "$BUNDLE/mods/$FAPI_NAME"
for file in "${PACK_FILES[@]}"; do
  cp "$PACK_DIR/mods/$file" "$BUNDLE/mods/$file"
done
cp "$PACK_DIR/PERFORMANCE-PACK.txt" "$BUNDLE/PERFORMANCE-PACK.txt"
cp "$PACK_DIR/THIRD-PARTY-LICENSES.txt" "$BUNDLE/THIRD-PARTY-LICENSES.txt"
cp "$PACK_JSON" "$BUNDLE/performance-pack.json"

# The scalar placeholders go through sed; the three multi-line blocks replace the line that holds only the placeholder.
printf '%s' "$PACK_LIST" > "$WORK/pack-list.txt"
printf '%s' "$PACK_EXCLUDED" > "$WORK/pack-excluded.txt"
printf '%s' "$PACK_NOTES" > "$WORK/pack-notes.txt"
sed -e "s|{VERSION}|${VERSION}|g" \
    -e "s|{MINECRAFT_VERSION}|${MINECRAFT_VERSION}|g" \
    -e "s|{LOADER_VERSION}|${LOADER_VERSION}|g" \
    -e "s|{FABRIC_API_VERSION}|${FABRIC_API_VERSION}|g" \
    "$TEMPLATE" \
  | awk -v list="$WORK/pack-list.txt" -v excluded="$WORK/pack-excluded.txt" -v notes="$WORK/pack-notes.txt" '
      function emit(file,   line) { while ((getline line < file) > 0) print line; close(file) }
      $0 == "{PACK_LIST}" { emit(list); next }
      $0 == "{PACK_EXCLUDED}" { emit(excluded); next }
      $0 == "{PACK_NOTES}" { emit(notes); next }
      { print }' > "$BUNDLE/INSTALL.txt"
if grep -nE '\{[A-Z_]+\}' "$BUNDLE/INSTALL.txt" >&2; then
  fail "INSTALL.txt still contains placeholders"
fi
# Long Modrinth file names can push a generated list line past the reviewed width; that is cosmetic, so warn only.
awk 'length > 120 { print "warning: INSTALL.txt:" FNR ": " length " columns (longer than 120)" }' "$BUNDLE/INSTALL.txt" >&2

MOD_ENTRIES=("mods/$CLIENT_NAME" "mods/$FAPI_NAME")
for file in "${PACK_FILES[@]}"; do
  MOD_ENTRIES+=("mods/$file")
done
TEXT_ENTRIES=(INSTALL.txt PERFORMANCE-PACK.txt THIRD-PARTY-LICENSES.txt performance-pack.json)
(cd "$BUNDLE" && "${SHA256[@]}" "${MOD_ENTRIES[@]}" "${TEXT_ENTRIES[@]}" > SHA256SUMS)
(cd "$BUNDLE" && "${SHA256[@]}" -c SHA256SUMS >&2) || fail "SHA256SUMS does not verify in the staging folder"

mkdir -p "$OUT"
ZIP="$(cd "$OUT" && pwd)/vanta-client-${VERSION}-mods.zip"
rm -f "$ZIP"
ENTRIES=(INSTALL.txt PERFORMANCE-PACK.txt SHA256SUMS THIRD-PARTY-LICENSES.txt mods/ "${MOD_ENTRIES[@]}" performance-pack.json)
(cd "$BUNDLE" && zip -X -q "$ZIP" "${ENTRIES[@]}")

EXPECTED="$(printf '%s\n' "${ENTRIES[@]}")"
ACTUAL="$(unzip -Z1 "$ZIP")"
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "expected entries:" >&2; echo "$EXPECTED" >&2
  echo "actual entries:" >&2; echo "$ACTUAL" >&2
  fail "$ZIP does not contain exactly the expected entries"
fi
CHECK="$WORK/check"
mkdir -p "$CHECK"
unzip -q "$ZIP" -d "$CHECK"
(cd "$CHECK" && "${SHA256[@]}" -c SHA256SUMS >&2) || fail "SHA256SUMS does not verify after extracting $ZIP"
cmp -s "$CLIENT_JAR" "$CHECK/mods/$CLIENT_NAME" || fail "the client jar in the zip differs from $CLIENT_JAR"
cmp -s "$FAPI_JAR" "$CHECK/mods/$FAPI_NAME" || fail "the Fabric API jar in the zip differs from $FAPI_JAR"
for file in "${PACK_FILES[@]}"; do
  cmp -s "$PACK_DIR/mods/$file" "$CHECK/mods/$file" || fail "the pack jar $file in the zip differs from $PACK_DIR/mods/$file"
done

echo "SHA256SUMS:" >&2
cat "$BUNDLE/SHA256SUMS" >&2
unzip -l "$ZIP" >&2
echo "$ZIP"
