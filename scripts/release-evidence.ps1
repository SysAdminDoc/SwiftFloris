param(
    [string]$OutputDir = "",
    [string]$JavaHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot",
    [string]$AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk",
    [switch]$StrictRelease,
    [switch]$AllowUnpublishedRelease,
    [switch]$SkipOsvScan,
    [switch]$SkipReproducibleApk
)

$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $RepoRoot

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
    $OutputDir = Join-Path $RepoRoot "build\release-evidence\$stamp"
}
$OutputDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDir)
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$SummaryPath = Join-Path $OutputDir "summary.txt"
$CommandPath = Join-Path $OutputDir "commands.txt"
Set-Content -Path $SummaryPath -Value @(
    "SwiftFloris local release evidence",
    "Started: $((Get-Date).ToUniversalTime().ToString("o"))",
    "Repository: $RepoRoot"
)
Set-Content -Path $CommandPath -Value @()

function Add-Summary {
    param([string]$Line)
    Add-Content -Path $SummaryPath -Value $Line
}

function Convert-ToGitBashPath {
    param([string]$Path)
    return ((Resolve-Path $Path).Path -replace "\\", "/")
}

function Get-GitBash {
    $candidates = @(
        "C:\Program Files\Git\bin\bash.exe",
        "C:\Program Files\Git\usr\bin\bash.exe"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    $bash = Get-Command bash.exe -ErrorAction SilentlyContinue
    if ($bash) {
        return $bash.Source
    }
    throw "Git Bash is required for the repository shell gates."
}

function Get-Python {
    $python = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($python) {
        return $python.Source
    }
    $py = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($py) {
        return $py.Source
    }
    throw "Python is required for scripts/osv-release-gate.py."
}

function Invoke-EvidenceCommand {
    param(
        [string]$Name,
        [string]$Executable,
        [string[]]$Arguments = @(),
        [int[]]$AllowedExitCodes = @(0)
    )

    $logPath = Join-Path $OutputDir "$Name.log"
    $display = @($Executable) + $Arguments -join " "
    Add-Content -Path $CommandPath -Value "$Name`t$display"
    Write-Host "==> $Name"
    Add-Summary "${Name}: RUN $display"

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $Executable @Arguments 2>&1 | Tee-Object -FilePath $logPath
        $exitCode = if ($null -eq $global:LASTEXITCODE) { 0 } else { $global:LASTEXITCODE }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($AllowedExitCodes -notcontains [int]$exitCode) {
        Add-Summary "${Name}: FAIL exit=$exitCode log=$logPath"
        throw "$Name failed with exit code $exitCode. See $logPath"
    }
    Add-Summary "${Name}: OK exit=$exitCode log=$logPath"
}

function Assert-StrictReleaseSigning {
    $requiredEnv = @(
        "KEYSTORE_PATH",
        "SIGNING_KEYSTORE_PASSWORD",
        "SIGNING_KEY_ALIAS",
        "SIGNING_KEY_PASSWORD"
    )
    $missingEnv = @()
    foreach ($name in $requiredEnv) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if ([string]::IsNullOrWhiteSpace($value)) {
            $missingEnv += $name
        }
    }
    if ($missingEnv.Count -gt 0) {
        throw "StrictRelease requires release signing environment variables: $($missingEnv -join ', ')"
    }

    $keystorePath = [Environment]::GetEnvironmentVariable("KEYSTORE_PATH")
    if (-not (Test-Path $keystorePath)) {
        throw "StrictRelease KEYSTORE_PATH does not exist: $keystorePath"
    }

    Add-Summary "StrictRelease signing: release keystore material present"
}

function Get-OsvScanArguments {
    param([string]$OsvExecutable, [string]$OutputPath)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $OsvExecutable scan --help *> $null
        $scanExit = if ($null -eq $global:LASTEXITCODE) { 0 } else { $global:LASTEXITCODE }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($scanExit -eq 0) {
        return @(
            "scan",
            "source",
            "-r",
            "--format", "json",
            "--output-file", $OutputPath,
            "."
        )
    }

    return @(
        "--recursive",
        "--skip-git",
        "--format", "json",
        "--output", $OutputPath,
        "."
    )
}

$commit = (& git rev-parse HEAD).Trim()
$status = & git status --short
Add-Summary "Commit: $commit"
if ($status) {
    Add-Summary "Working tree: dirty"
    $status | Set-Content -Path (Join-Path $OutputDir "git-status.txt")
} else {
    Add-Summary "Working tree: clean"
}

if (Test-Path $JavaHome) {
    $env:JAVA_HOME = $JavaHome
    Add-Summary "JAVA_HOME: $JavaHome"
} elseif ($env:JAVA_HOME) {
    Add-Summary "JAVA_HOME: $env:JAVA_HOME"
} else {
    throw "JAVA_HOME is not set and default JavaHome does not exist: $JavaHome"
}

if (Test-Path $AndroidSdk) {
    $env:ANDROID_HOME = $AndroidSdk
    $env:ANDROID_SDK_ROOT = $AndroidSdk
    $sdkProperty = ($AndroidSdk -replace "\\", "/") -replace ":", "\:"
    $localProperties = Join-Path $RepoRoot "local.properties"
    $localPropertiesNeedsUpdate = $true
    if (Test-Path $localProperties) {
        $existingSdkLine = Get-Content $localProperties | Where-Object { $_ -match "^sdk\.dir=" } | Select-Object -First 1
        if ($existingSdkLine -eq "sdk.dir=$sdkProperty") {
            $localPropertiesNeedsUpdate = $false
        }
    }
    if ($localPropertiesNeedsUpdate) {
        Set-Content -Path $localProperties -Value "sdk.dir=$sdkProperty"
        Add-Summary "local.properties: updated sdk.dir for local release evidence"
    }
    Add-Summary "ANDROID_HOME: $AndroidSdk"
} elseif ($env:ANDROID_HOME -or $env:ANDROID_SDK_ROOT) {
    Add-Summary "ANDROID_HOME: $env:ANDROID_HOME"
    Add-Summary "ANDROID_SDK_ROOT: $env:ANDROID_SDK_ROOT"
} else {
    throw "Android SDK not found. Pass -AndroidSdk or set ANDROID_HOME."
}

$bash = Get-GitBash
$python = Get-Python

$manualGateManifest = Join-Path $RepoRoot "scripts\release-evidence-manual-gates.tsv"
if (-not (Test-Path $manualGateManifest)) {
    throw "Release-evidence manual-gate manifest is missing: $manualGateManifest"
}
$manualGateEntries = @()
foreach ($line in Get-Content $manualGateManifest) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith("#")) {
        continue
    }
    $fields = $line -split "`t", 2
    if ($fields.Count -ne 2 -or [string]::IsNullOrWhiteSpace($fields[0]) -or [string]::IsNullOrWhiteSpace($fields[1])) {
        throw "Malformed release-evidence manual-gate entry: $line"
    }
    $manualGateEntries += [pscustomobject]@{
        Path = $fields[0].Trim()
        Reason = $fields[1].Trim()
    }
}
foreach ($entry in $manualGateEntries) {
    if (-not (Test-Path (Join-Path $RepoRoot $entry.Path))) {
        throw "Manual release-evidence gate does not exist: $($entry.Path)"
    }
    Add-Summary "Manual gate (operator-run): $($entry.Path) — $($entry.Reason)"
}

$selfTestScripts = Get-ChildItem -LiteralPath (Join-Path $RepoRoot "scripts") -Filter "test-*.py" -File | Sort-Object Name
foreach ($selfTest in $selfTestScripts) {
    $relativePath = "scripts/$($selfTest.Name)"
    Invoke-EvidenceCommand "self-test-$($selfTest.BaseName)" $python @($relativePath)
}

$gradleReleaseEvidenceArgs = @(
    "--no-daemon",
    "--no-build-cache",
    "--rerun-tasks",
    "-Dorg.gradle.caching=false",
    "-Dkotlin.caching.enabled=false"
)
$gradleMitigation = $gradleReleaseEvidenceArgs -join " "
Add-Summary "Kotlin build-cache mitigation: gradle-local-gates use $gradleMitigation; remove after final Kotlin 2.4.20+ and compatible KSP ship."

function Get-KotlinBuildCacheGuardArguments {
    param(
        [string]$Label,
        [string[]]$GradleArguments
    )

    $guardArguments = @(
        "scripts/check-kotlin-build-cache-cve-guard.py",
        "--label",
        $Label
    )
    foreach ($gradleArgument in $GradleArguments) {
        $guardArguments += "--gradle-arg=$gradleArgument"
    }
    return $guardArguments
}

$releaseArgs = @((Convert-ToGitBashPath "scripts\check-release-front-door.sh"))
if ($StrictRelease) {
    Add-Summary "StrictRelease: default release-front-door mode is already strict"
    Assert-StrictReleaseSigning
} else {
    Add-Summary "Signing: non-strict evidence allows debug-signed local APKs; pass -StrictRelease to require release signing material"
}
if ($AllowUnpublishedRelease) {
    $releaseArgs += "--allow-unpublished"
}

Invoke-EvidenceCommand "release-front-door" $bash $releaseArgs
Invoke-EvidenceCommand "fastlane-metadata" $bash @((Convert-ToGitBashPath "scripts\check-fastlane-metadata.sh"))
Invoke-EvidenceCommand "backup-privacy-copy" $bash @((Convert-ToGitBashPath "scripts\check-backup-privacy-copy.sh"))
Invoke-EvidenceCommand "fork-identity" $bash @((Convert-ToGitBashPath "scripts\check-fork-identity.sh"))
Invoke-EvidenceCommand "layout-json" $python @("scripts/check-layout-json.py")
Invoke-EvidenceCommand "locale-coverage" $python @("scripts/check-locale-coverage.py", "--check")
# --check-published also resolves the newest release and requires an asset to
# match each Obtainium filter. A filter that matches nothing is silent in
# Obtainium: users simply stop being offered updates.
Invoke-EvidenceCommand "public-doc-version-pins" $python @("scripts/check-public-doc-version-pins.py", "--check-published")
Invoke-EvidenceCommand "trust-capability-gate" $python @("scripts/check-trust-capabilities.py")
Invoke-EvidenceCommand "security-dependency-freshness" $python @("scripts/check-security-dependency-freshness.py")
Invoke-EvidenceCommand "runblocking-allowlist" $python @("scripts/check-runblocking-allowlist.py")
Invoke-EvidenceCommand "pointerinput-unit-allowlist" $python @("scripts/check-pointerinput-unit-allowlist.py")
Invoke-EvidenceCommand "live-doc-integrity" $python @("scripts/check-live-doc-integrity.py")
# --check-published resolves the recipe binary URL against the real GitHub
# release. Release time is exactly when that asset is supposed to exist, and
# a 404 there means F-Droid downloads nothing and compares nothing.
Invoke-EvidenceCommand "fdroid-recipe" $python @("scripts/check-fdroid-recipe.py", ".", "--check-published")
Invoke-EvidenceCommand "root-crash-logs" $bash @((Convert-ToGitBashPath "scripts\check-no-root-crash-logs.sh"))
Invoke-EvidenceCommand "repo-hygiene" $bash @((Convert-ToGitBashPath "scripts\check-repo-hygiene.sh"))
Invoke-EvidenceCommand "kotlin-build-cache-cve-guard" $python (Get-KotlinBuildCacheGuardArguments "gradle-local-gates" $gradleReleaseEvidenceArgs)
Invoke-EvidenceCommand "targetsdk37-shadow" $python @("scripts/verify-targetsdk37-shadow.py", "--shadow-target", "37")

Invoke-EvidenceCommand "gradle-local-gates" (Join-Path $RepoRoot "gradlew.bat") ($gradleReleaseEvidenceArgs + @(
    ":app:verifyNoInternetPermission",
    ":app:testDebugUnitTest",
    ":app:lintDebug",
    ":app:assembleRelease",
    ":app:verifyReleaseDevtoolsIsolation",
    ":addons:dictionary-pack-sample:assembleRelease"
))

# The app ships SQLCipher native libraries across four ABIs, and until now
# only addon APKs were checked for 16 KB page compatibility. A library linked
# for 4 KB pages does not load on an Android 15+ 16 KB device; it crashes at
# first use.
$releaseApkDir = Join-Path $RepoRoot "app\build\outputs\apk\release"
# Named by AGP's own metadata, not matched by glob: a signed APK left over
# from an earlier run would otherwise win the sort and get attested in place of
# the artifact this run built.
$releaseMetadata = Join-Path $releaseApkDir "output-metadata.json"
if (-not (Test-Path $releaseMetadata)) {
    throw "Release APK metadata was not produced: $releaseMetadata"
}
$releaseApkName = (Get-Content $releaseMetadata -Raw | ConvertFrom-Json).elements[0].outputFile
$releaseApk = Join-Path $releaseApkDir $releaseApkName
if (-not (Test-Path $releaseApk)) {
    throw "Release APK named by $releaseMetadata is missing: $releaseApk"
}
Add-Summary "Release APK: $releaseApk"
Invoke-EvidenceCommand "app-apk-16kb-alignment" $python @(
    "scripts/check-apk-16kb-alignment.py",
    $releaseApk
)

$sampleAddonApk = Join-Path $RepoRoot "addons\dictionary-pack-sample\build\outputs\apk\release\dictionary-pack-sample-release.apk"
if (-not (Test-Path $sampleAddonApk)) {
    throw "Sample dictionary-pack APK was not produced: $sampleAddonApk"
}
Add-Summary "Sample addon APK: $sampleAddonApk"
Invoke-EvidenceCommand "addon-sample-apk-validation" $bash @(
    (Convert-ToGitBashPath "scripts\verify-addon-apk.sh"),
    ((Resolve-Path $sampleAddonApk).Path -replace "\\", "/")
)

if ($SkipOsvScan) {
    Add-Summary "osv-scan: SKIPPED by parameter"
} else {
    $osv = Get-Command osv-scanner.exe -ErrorAction SilentlyContinue
    if (-not $osv) {
        $osv = Get-Command osv-scanner -ErrorAction SilentlyContinue
    }
    if (-not $osv) {
        throw "osv-scanner is required. Install with: go install github.com/google/osv-scanner/v2/cmd/osv-scanner@latest"
    }
    $osvResult = Join-Path $OutputDir "osv-result.json"
    Invoke-EvidenceCommand "osv-scan" $osv.Source (Get-OsvScanArguments $osv.Source $osvResult) @(0, 1)
    Invoke-EvidenceCommand "osv-release-gate" $python @(
        "scripts/osv-release-gate.py",
        $osvResult
    )
}

if ($SkipReproducibleApk) {
    Add-Summary "reproducible-apk: SKIPPED by parameter"
} else {
    $reproDir = Join-Path $OutputDir "reproducible-apk"
    Invoke-EvidenceCommand "reproducible-apk" $bash @(
        (Convert-ToGitBashPath "scripts\verify-reproducible-apk.sh"),
        ($reproDir -replace "\\", "/")
    )
}

Add-Summary "Completed: $((Get-Date).ToUniversalTime().ToString("o"))"
Write-Host "Local release evidence written to $OutputDir"
