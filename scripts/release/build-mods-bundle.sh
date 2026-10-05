#!/usr/bin/env bash
# Builds vanta-client-<version>-mods.zip, the "unzip into .minecraft" download of a client release:
#
#   INSTALL.txt                          scripts/release/mods-bundle/INSTALL.txt with the pinned versions filled in
#   SHA256SUMS                           sha256sum -c compatible, paths relative to the zip root
#   mods/vanta-client-<version>.jar      the client jar, unchanged
#   mods/fabric-api-<fabric api>.jar     the unmodified Fabric API jar, unchanged
#
#   scripts/release/build-mods-bundle.sh --version <version> --client-jar <jar> --fabric-api-jar <jar> --out <dir>
#
# Minecraft and Fabric Loader versions come from client/gradle.properties; the Fabric API version comes from the
# jar's file name and must match fabric_api_version there. The script verifies SHA256SUMS before zipping and again
# on a fresh extraction of the finished zip, and checks the zip holds exactly the expected entries.
# Needs bash, zip, unzip and sha256sum (or shasum). Prints the zip path on the last line.
set -euo pipefail

usage() {
  echo "Usage: $0 --version <version> --client-jar <jar> --fabric-api-jar <jar> --out <dir>" >&2
  exit 2
}

VERSION="" CLIENT_JAR="" FAPI_JAR="" OUT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="${2:-}"; shift 2 || usage ;;
    --client-jar) CLIENT_JAR="${2:-}"; shift 2 || usage ;;
    --fabric-api-jar) FAPI_JAR="${2:-}"; shift 2 || usage ;;
    --out) OUT="${2:-}"; shift 2 || usage ;;
    -h|--help) usage ;;
    *) echo "error: unknown argument '$1'" >&2; usage ;;
  esac
done
if [ -z "$VERSION" ] || [ -z "$CLIENT_JAR" ] || [ -z "$FAPI_JAR" ] || [ -z "$OUT" ]; then
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

if command -v sha256sum >/dev/null 2>&1; then
  SHA256=(sha256sum)
elif command -v shasum >/dev/null 2>&1; then
  SHA256=(shasum -a 256)
else
  fail "neither sha256sum nor shasum is available"
fi
command -v zip >/dev/null 2>&1 || fail "zip is not installed"
command -v unzip >/dev/null 2>&1 || fail "unzip is not installed"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
BUNDLE="$WORK/bundle"
mkdir -p "$BUNDLE/mods"
cp "$CLIENT_JAR" "$BUNDLE/mods/$CLIENT_NAME"
cp "$FAPI_JAR" "$BUNDLE/mods/$FAPI_NAME"
sed -e "s|{VERSION}|${VERSION}|g" \
    -e "s|{MINECRAFT_VERSION}|${MINECRAFT_VERSION}|g" \
    -e "s|{LOADER_VERSION}|${LOADER_VERSION}|g" \
    -e "s|{FABRIC_API_VERSION}|${FABRIC_API_VERSION}|g" \
    "$TEMPLATE" > "$BUNDLE/INSTALL.txt"
if grep -nE '\{[A-Z_]+\}' "$BUNDLE/INSTALL.txt" >&2; then
  fail "INSTALL.txt still contains placeholders"
fi

(cd "$BUNDLE" && "${SHA256[@]}" "mods/$CLIENT_NAME" "mods/$FAPI_NAME" INSTALL.txt > SHA256SUMS)
(cd "$BUNDLE" && "${SHA256[@]}" -c SHA256SUMS >&2) || fail "SHA256SUMS does not verify in the staging folder"

mkdir -p "$OUT"
ZIP="$(cd "$OUT" && pwd)/vanta-client-${VERSION}-mods.zip"
rm -f "$ZIP"
(cd "$BUNDLE" && zip -X -q "$ZIP" INSTALL.txt SHA256SUMS mods/ "mods/$CLIENT_NAME" "mods/$FAPI_NAME")

EXPECTED="$(printf '%s\n' INSTALL.txt SHA256SUMS mods/ "mods/$CLIENT_NAME" "mods/$FAPI_NAME")"
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

echo "SHA256SUMS:" >&2
cat "$BUNDLE/SHA256SUMS" >&2
unzip -l "$ZIP" >&2
echo "$ZIP"
