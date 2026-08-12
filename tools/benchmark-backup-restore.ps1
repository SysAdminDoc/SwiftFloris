param(
    [int]$Iterations = 5,
    [string]$Serial = "",
    [string]$ApkPath = "app/build/outputs/apk/benchmark/app-benchmark.apk",
    [string]$OutputPath = "docs/benchmark-results/baseline-$(Get-Date -Format yyyy-MM-dd)-backup-restore.json"
)

$ErrorActionPreference = "Stop"

$PackageName = "dev.patrickgold.florisboard.bench"
$ActivityComponent = "$PackageName/dev.patrickgold.florisboard.benchmark.BenchmarkBackupRestoreActivity"
$AdbSerialArgs = @()
if ($Serial.Trim().Length -gt 0) {
    $AdbSerialArgs = @("-s", $Serial)
}

function Invoke-Adb {
    & adb @script:AdbSerialArgs @args
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($args -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Invoke-AdbText {
    $output = & adb @script:AdbSerialArgs @args
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($args -join ' ') failed with exit code $LASTEXITCODE"
    }
    return ($output -join "`n")
}

. (Join-Path $PSScriptRoot "benchmark-device.ps1")

function Get-Median([double[]]$Values) {
    if ($Values.Count -eq 0) {
        return $null
    }
    $sorted = @($Values | Sort-Object)
    $middle = [int][Math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) {
        return $sorted[$middle]
    }
    return ($sorted[$middle - 1] + $sorted[$middle]) / 2.0
}

$resolvedApk = Resolve-Path -LiteralPath $ApkPath
$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

Invoke-Adb install -r $resolvedApk

$device = Get-BenchmarkDeviceMetadata

$runs = @()
for ($i = 1; $i -le $Iterations; $i++) {
    Invoke-Adb shell logcat -c
    Invoke-Adb shell am force-stop $PackageName
    Invoke-Adb shell input keyevent KEYCODE_HOME
    Start-Sleep -Milliseconds 500

    $startOutput = Invoke-AdbText shell am start -W -n $ActivityComponent
    Start-Sleep -Seconds 6
    $perfLog = Invoke-AdbText logcat -d -s SwiftFlorisPerf:I

    $backupMatch = [regex]::Match(
        $perfLog,
        "swiftfloris\.backup\.createMs=([0-9.]+)\s+archiveBytes=(\d+)\s+sections=(\d+)\s+profile=([^\s]+)"
    )
    if (-not $backupMatch.Success) {
        throw "No backup duration marker found in iteration $i"
    }
    $prepareMatch = [regex]::Match(
        $perfLog,
        "swiftfloris\.restore\.prepareMs=([0-9.]+)\s+archiveBytes=(\d+)\s+profile=([^\s]+)"
    )
    if (-not $prepareMatch.Success) {
        throw "No restore prepare marker found in iteration $i"
    }
    $applyMatch = [regex]::Match(
        $perfLog,
        "swiftfloris\.restore\.applyMs=([0-9.]+)\s+selectedSections=(\d+)\s+restoredSections=(\d+)\s+missingSections=(\d+)\s+failedSections=(\d+)\s+strategy=([^\s]+)\s+profile=([^\s]+)"
    )
    if (-not $applyMatch.Success) {
        throw "No restore apply marker found in iteration $i"
    }
    $totalMatch = [regex]::Match(
        $perfLog,
        "swiftfloris\.restore\.totalMs=([0-9.]+)\s+archiveBytes=(\d+)\s+profile=([^\s]+)"
    )
    if (-not $totalMatch.Success) {
        throw "No restore total marker found in iteration $i"
    }

    $runs += [ordered]@{
        iteration = $i
        profile = $backupMatch.Groups[4].Value
        backupCreateMs = [double]$backupMatch.Groups[1].Value
        archiveBytes = [double]$backupMatch.Groups[2].Value
        selectedBackupSections = [double]$backupMatch.Groups[3].Value
        restorePrepareMs = [double]$prepareMatch.Groups[1].Value
        restoreApplyMs = [double]$applyMatch.Groups[1].Value
        restoreTotalMs = [double]$totalMatch.Groups[1].Value
        selectedRestoreSections = [double]$applyMatch.Groups[2].Value
        restoredSections = [double]$applyMatch.Groups[3].Value
        missingSections = [double]$applyMatch.Groups[4].Value
        failedSections = [double]$applyMatch.Groups[5].Value
        strategy = $applyMatch.Groups[6].Value
        startOutput = $startOutput
        perfLog = $perfLog
    }
}

$summary = [ordered]@{
    backupCreateMedianMs = Get-Median @($runs | ForEach-Object { $_.backupCreateMs })
    archiveBytesMedian = Get-Median @($runs | ForEach-Object { $_.archiveBytes })
    restorePrepareMedianMs = Get-Median @($runs | ForEach-Object { $_.restorePrepareMs })
    restoreApplyMedianMs = Get-Median @($runs | ForEach-Object { $_.restoreApplyMs })
    restoreTotalMedianMs = Get-Median @($runs | ForEach-Object { $_.restoreTotalMs })
    selectedBackupSectionsMedian = Get-Median @($runs | ForEach-Object { $_.selectedBackupSections })
    selectedRestoreSectionsMedian = Get-Median @($runs | ForEach-Object { $_.selectedRestoreSections })
    restoredSectionsMedian = Get-Median @($runs | ForEach-Object { $_.restoredSections })
    missingSectionsMedian = Get-Median @($runs | ForEach-Object { $_.missingSections })
    failedSectionsMedian = Get-Median @($runs | ForEach-Object { $_.failedSections })
}

$result = [ordered]@{
    benchmark = "backupRestore"
    measuredAt = (Get-Date).ToString("o")
    packageName = $PackageName
    activityComponent = $ActivityComponent
    iterations = $Iterations
    profile = "settingsKeyboardTheme"
    device = $device
    summary = $summary
    runs = $runs
}

$json = $result | ConvertTo-Json -Depth 8
Set-Content -LiteralPath $OutputPath -Value $json -Encoding UTF8
$json
