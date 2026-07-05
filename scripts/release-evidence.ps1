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
}
if ($AllowUnpublishedRelease) {
    $releaseArgs += "--allow-unpublished"
}

Invoke-EvidenceCommand "release-front-door" $bash $releaseArgs
Invoke-EvidenceCommand "fastlane-metadata" $bash @((Convert-ToGitBashPath "scripts\check-fastlane-metadata.sh"))
Invoke-EvidenceCommand "backup-privacy-copy" $bash @((Convert-ToGitBashPath "scripts\check-backup-privacy-copy.sh"))
Invoke-EvidenceCommand "public-doc-version-pins" $python @("scripts/check-public-doc-version-pins.py")
Invoke-EvidenceCommand "live-doc-integrity" $python @("scripts/check-live-doc-integrity.py")
Invoke-EvidenceCommand "root-crash-logs" $bash @((Convert-ToGitBashPath "scripts\check-no-root-crash-logs.sh"))
Invoke-EvidenceCommand "repo-hygiene" $bash @((Convert-ToGitBashPath "scripts\check-repo-hygiene.sh"))
Invoke-EvidenceCommand "kotlin-build-cache-cve-guard" $python (Get-KotlinBuildCacheGuardArguments "gradle-local-gates" $gradleReleaseEvidenceArgs)

Invoke-EvidenceCommand "gradle-local-gates" (Join-Path $RepoRoot "gradlew.bat") ($gradleReleaseEvidenceArgs + @(
    ":app:verifyNoInternetPermission",
    ":app:verifyDataExtractionRules",
    ":app:testDebugUnitTest",
    ":app:lintDebug",
    ":app:assembleRelease",
    ":addons:dictionary-pack-sample:assembleRelease"
))

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
