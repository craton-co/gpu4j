#!/usr/bin/env pwsh
# Local CI runner in Docker mirroring .github/workflows/ci.yml across JDK 17, 21, and 25.
param(
    [string[]]$JavaVersions = @("17", "21", "25")
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$CacheVolume = "gpu4j-m2-cache"

docker volume create $CacheVolume | Out-Null

$Results = @()
foreach ($Jdk in $JavaVersions) {
    $Image = "maven:3.9-eclipse-temurin-$Jdk"
    Write-Host "`n================================================================" -ForegroundColor Cyan
    Write-Host "  Running CI job in Docker: $Image" -ForegroundColor Cyan
    Write-Host "================================================================" -ForegroundColor Cyan

    $Cmd = @'
set -euo pipefail
mvn -B -ntp -Prelease clean verify -Dgpg.skip=true
VER="$(mvn -N -q help:evaluate -Dexpression=project.version -DforceStdout)"
java -cp "gpu4j-core/target/gpu4j-core-${VER}.jar" examples/Demo.java
'@
    # Strip CR in case script is edited with CRLF on Windows
    $Cmd = $Cmd -replace "`r", ""

    docker run --rm `
        -v "${RepoRoot}:/workspace" `
        -v "${CacheVolume}:/root/.m2" `
        -w /workspace `
        $Image `
        bash -c $Cmd

    if ($LASTEXITCODE -ne 0) {
        Write-Host "[FAIL] JDK $Jdk failed with exit code $LASTEXITCODE" -ForegroundColor Red
        exit $LASTEXITCODE
    }
    $Results += "JDK ${Jdk}: PASS"
}

Write-Host "`n================================================================" -ForegroundColor Green
Write-Host "  All Local CI Jobs Passed:" -ForegroundColor Green
$Results | ForEach-Object { Write-Host "    - $_" -ForegroundColor Green }
Write-Host "================================================================" -ForegroundColor Green
