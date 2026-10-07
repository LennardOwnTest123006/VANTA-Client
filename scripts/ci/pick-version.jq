# Picks the Modrinth version the client game test loads for one Performance pack member, from the array that
# GET /v2/project/<slug>/version returns. This is the shared selection rule of the in-game installer
# (core VersionSelector), the launcher (InstallPlanner via VersionSelector) and the release bundle
# (scripts/release/performance-pack.mjs selectVersion), so the game test runs the jars the release ships:
#   - only versions for $game and the fabric loader (case-insensitive) whose primary file (the file marked primary,
#     else the first) carries a 128-hex SHA-512 and a safe file name (not blank, at most 200 UTF-16 units as Java and
#     JavaScript count, no leading dot, no "..", none of / \ : < > " | ? * and no control character) are candidates;
#   - among them the newest release wins, else the newest beta, else the newest alpha, else the newest of any other
#     type (version_type compared case-insensitively, by date_published; a fractional second counts).
# Prints "<version id> <version number>" or nothing. scripts/release/performance-pack.test.mjs runs this file against
# fixtures and checks it agrees with selectVersion.
#
#   jq -r --arg game 1.21.11 -f scripts/ci/pick-version.jq versions.json
def primary_file: (.files // []) as $files
  | (($files | map(select(.primary == true)) | first) // ($files | first) // {});
# String length in UTF-16 units (what String.length gives in Java and JavaScript); jq's length counts code points.
def utf16_length: [explode[] | if . > 65535 then 2 else 1 end] | add // 0;
def safe_filename: type == "string" and (test("\\A\\s*\\z") | not) and utf16_length <= 200
  and (startswith(".") | not) and (contains("..") | not)
  and (test("[/\\\\:<>\"|?*\\x00-\\x1f\\x7f]") | not);
def installable: ((.game_versions // []) | index($game)) != null
  and ((.loaders // []) | map(strings | ascii_downcase) | index("fabric")) != null
  and (primary_file | ((.hashes.sha512 // "") | test("\\A[0-9a-fA-F]{128}\\z")) and (.filename | safe_filename));
# Sort key of date_published: seconds since the epoch including the fraction for the RFC 3339 UTC form Modrinth
# uses ("2026-03-02T10:00:00.123456Z" or without the fraction), else the string itself.
def published_key: (.date_published // "") as $d
  | ($d | capture("^(?<base>[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2})(?<frac>\\.[0-9]+)?Z$") // null) as $m
  | if $m == null then [0, $d] else [(($m.base + "Z") | fromdateiso8601) + (("0" + ($m.frac // "")) | tonumber), ""] end;
def channel: (.version_type | if type == "string" then (sub("^\\s+"; "") | sub("\\s+$"; "") | ascii_downcase) else "" end);
[.[] | select(installable)]
  | sort_by(published_key) | reverse
  | ((map(select(channel == "release")) | first)
     // (map(select(channel == "beta")) | first)
     // (map(select(channel == "alpha")) | first) // first)
  | if . == null then empty else "\(.id) \(.version_number)" end
