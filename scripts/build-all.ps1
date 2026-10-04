<#
.SYNOPSIS
  Builds every VANTA product from a clean checkout: core -> client -> launcher -> website.

.DESCRIPTION
  Windows counterpart of scripts/build-all.sh. Requires JDK 21 and Node.js 22 on PATH; Gradle comes
  from the wrappers. Stops at the first failure and prints a header per step.

.PARAMETER SkipClient
  Skip the Fabric mod (for machines without access to the Mojang/Fabric hosts; GitHub Actions builds it).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\build-all.ps1 -SkipClient
#>
[CmdletBinding()]
param(
  [switch]$SkipCore,
  [switch]$SkipClient,
  [switch]$SkipLauncher,
  [switch]$SkipWebsite,
  [switch]$NoTests
)

$ErrorActionPreference = 'Stop'
$Root = Resolve-Path (Join-Path $PSScriptRoot '..')
$Started = Get-Date

function Write-Step([string]$Message) { Write-Host "`n==> $Message" -ForegroundColor Magenta }
function Write-Done([string]$Name, [datetime]$Since) {
  $seconds = [int]((Get-Date) - $Since).TotalSeconds
  Write-Host "    ok  $Name (${seconds}s)" -ForegroundColor Green
}
function Invoke-Checked([string]$Dir, [string[]]$Command) {
  Push-Location $Dir
  try {
    & $Command[0] @($Command[1..($Command.Length - 1)])
    if ($LASTEXITCODE -ne 0) { throw "'$($Command -join ' ')' failed with exit code $LASTEXITCODE in $Dir" }
  } finally {
    Pop-Location
  }
}
function Require-Command([string]$Name) {
  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) { throw "'$Name' is required but not on PATH" }
}

Require-Command java
Require-Command node
Require-Command npm
$javaVersion = (& java -version 2>&1 | Select-String -Pattern 'version "([^"]+)"').Matches[0].Groups[1].Value
if (-not $javaVersion.StartsWith('21')) { throw "JDK 21 is required (found Java $javaVersion). See docs/java-21.md." }
$nodeMajor = [int]((& node --version).TrimStart('v').Split('.')[0])
if ($nodeMajor -lt 22) { throw "Node.js 22 or newer is required (found $(& node --version))." }

$gradleArgs = @('--no-daemon', '--console=plain')
if ($NoTests) { $gradleArgs += @('-x', 'test') }

if (-not $SkipCore) {
  $t = Get-Date; Write-Step 'core: gradlew build (pure Java 21 library + unit tests)'
  Invoke-Checked (Join-Path $Root 'core') (@('.\gradlew.bat') + $gradleArgs + @('build'))
  Write-Done 'core' $t
}

if (-not $SkipClient) {
  $t = Get-Date; Write-Step 'client: gradlew build (Fabric mod for Minecraft 1.21.11; needs Mojang + Fabric hosts)'
  Invoke-Checked (Join-Path $Root 'client') (@('.\gradlew.bat') + $gradleArgs + @('build'))
  Write-Done 'client' $t
} else {
  Write-Step 'client: skipped (-SkipClient)'
}

if (-not $SkipLauncher) {
  $t = Get-Date; Write-Step 'launcher: gradlew build fatJar (JavaFX launcher + unit tests)'
  Invoke-Checked (Join-Path $Root 'launcher') (@('.\gradlew.bat') + $gradleArgs + @('build', 'fatJar'))
  Write-Done 'launcher' $t
}

if (-not $SkipWebsite) {
  $t = Get-Date; Write-Step 'website: npm ci && npm run build'
  $web = Join-Path $Root 'website'
  Invoke-Checked $web @('npm', 'ci', '--no-audit', '--no-fund')
  if (-not $NoTests) { Invoke-Checked $web @('npm', 'test') }
  Invoke-Checked $web @('npm', 'run', 'build')
  Write-Done 'website' $t
}

$t = Get-Date; Write-Step 'release metadata: validate manifests, content front matter and documentation links'
$schemas = Join-Path $Root 'shared\schemas'
$validate = Join-Path $Root 'scripts\release\validate-json.mjs'
Invoke-Checked $Root (@('node', $validate, '--quiet', (Join-Path $schemas 'release-manifest.schema.json')) + (Get-ChildItem (Join-Path $Root 'shared\releases\*.json') | ForEach-Object FullName))
Invoke-Checked $Root (@('node', $validate, '--quiet', '--front-matter', (Join-Path $schemas 'doc-page.schema.json')) + (Get-ChildItem (Join-Path $Root 'docs\*.md') | ForEach-Object FullName))
Invoke-Checked $Root (@('node', $validate, '--quiet', '--front-matter', (Join-Path $schemas 'changelog-entry.schema.json')) + (Get-ChildItem (Join-Path $Root 'website\content\changelog\*.md') | ForEach-Object FullName))
Invoke-Checked $Root (@('node', $validate, '--quiet', '--front-matter', (Join-Path $schemas 'news-post.schema.json')) + (Get-ChildItem (Join-Path $Root 'website\content\news\*-*.md') | ForEach-Object FullName))
Invoke-Checked $Root @('node', (Join-Path $Root 'scripts\release\check-links.mjs'), '--quiet', (Join-Path $Root 'docs'), (Join-Path $Root 'website\content'))
Write-Done 'release metadata' $t

$total = [int]((Get-Date) - $Started).TotalSeconds
Write-Host "`nAll requested builds finished in ${total}s." -ForegroundColor Green
Write-Host 'Artifacts:'
if (-not $SkipCore) { Get-ChildItem (Join-Path $Root 'core\build\libs\*.jar') -ErrorAction SilentlyContinue | ForEach-Object { "  $($_.FullName)" } }
if (-not $SkipClient) { Get-ChildItem (Join-Path $Root 'client\build\libs\*.jar') -ErrorAction SilentlyContinue | ForEach-Object { "  $($_.FullName)" } }
if (-not $SkipLauncher) { Get-ChildItem (Join-Path $Root 'launcher\build\libs\*-all.jar') -ErrorAction SilentlyContinue | ForEach-Object { "  $($_.FullName)" } }
if (-not $SkipWebsite -and (Test-Path (Join-Path $Root 'website\dist'))) { "  $(Join-Path $Root 'website\dist')\" }
