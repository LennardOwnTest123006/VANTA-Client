# Picks the Modrinth version the client game test loads for one Performance pack member, from the array that
# GET /v2/project/<slug>/version returns. This is the shared selection rule of the in-game installer
# (core VersionSelector), the launcher (InstallPlanner via VersionSelector) and the release bundle
# (scripts/release/performance-pack.mjs selectVersion), so the game test runs the jars the release ships:
#   - only versions for $game and the fabric loader whose primary file (the file marked primary, else the first)
#     carries a 128-hex SHA-512 and a safe file name (not empty, at most 200 characters, no leading dot, no "..",
#     none of / \ : < > " | ? * and no control character) are candidates;
#   - among them the newest release wins, else the newest beta, else the newest of any type (by date_published).
# Prints "<version id> <version number>" or nothing. scripts/release/performance-pack.test.mjs runs this file against
# fixtures and checks it agrees with selectVersion.
#
#   jq -r --arg game 1.21.11 -f scripts/ci/pick-version.jq versions.json
def primary_file: (.files // []) as $files
  | (($files | map(select(.primary == true)) | first) // ($files | first) // {});
def safe_filename: type == "string" and length > 0 and length <= 200
  and (startswith(".") | not) and (contains("..") | not)
  and (test("[/\\\\:<>\"|?*\\x00-\\x1f\\x7f]") | not);
def installable: ((.game_versions // []) | index($game)) != null
  and ((.loaders // []) | map(ascii_downcase) | index("fabric")) != null
  and (primary_file | ((.hashes.sha512 // "") | test("^[0-9a-fA-F]{128}$")) and (.filename | safe_filename));
[.[] | select(installable)]
  | sort_by(.date_published) | reverse
  | ((map(select(.version_type == "release")) | first)
     // (map(select(.version_type == "beta")) | first) // first)
  | if . == null then empty else "\(.id) \(.version_number)" end
