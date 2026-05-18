param(
    [int]$Iterations = 5,
    [string]$Serial = "",
    [string]$ApkPath = "app/build/outputs/apk/benchmark/app-benchmark.apk",
    [string]$OutputPath = "docs/benchmark-results/baseline-$(Get-Date -Format yyyy-MM-dd)-ime-first-render.json"
)

$ErrorActionPreference = "Stop"

$PackageName = "dev.patrickgold.florisboard.bench"
$ImeComponent = "$PackageName/dev.patrickgold.florisboard.FlorisImeService"
$ActivityComponent = "$PackageName/dev.patrickgold.florisboard.benchmark.BenchmarkInputActivity"
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
$previousIme = $null
try {
    $previousIme = (Invoke-AdbText shell settings get secure default_input_method).Trim()
} catch {
    $previousIme = $null
}

trap {
    if ($previousIme -and $previousIme -ne $ImeComponent) {
        & adb @AdbSerialArgs shell ime set $previousIme | Out-Null
    }
    throw
}

Invoke-Adb install -r $resolvedApk
Invoke-Adb shell ime enable $ImeComponent
Invoke-Adb shell ime set $ImeComponent
$selectedIme = (Invoke-AdbText shell settings get secure default_input_method).Trim()
if ($selectedIme -ne $ImeComponent) {
    throw "Expected $ImeComponent to be selected, got $selectedIme"
}

$device = [ordered]@{
    serial = (Invoke-AdbText get-serialno).Trim()
    manufacturer = (Invoke-AdbText shell getprop ro.product.manufacturer).Trim()
    model = (Invoke-AdbText shell getprop ro.product.model).Trim()
    device = (Invoke-AdbText shell getprop ro.product.device).Trim()
    androidRelease = (Invoke-AdbText shell getprop ro.build.version.release).Trim()
    sdk = (Invoke-AdbText shell getprop ro.build.version.sdk).Trim()
}

$runs = @()
for ($i = 1; $i -le $Iterations; $i++) {
    Invoke-Adb shell logcat -c
    Invoke-Adb shell am force-stop $PackageName
    Invoke-Adb shell ime enable $ImeComponent
    Invoke-Adb shell ime set $ImeComponent
    $selectedIme = (Invoke-AdbText shell settings get secure default_input_method).Trim()
    if ($selectedIme -ne $ImeComponent) {
        throw "Expected $ImeComponent to be selected for iteration $i, got $selectedIme"
    }
    Invoke-Adb shell input keyevent KEYCODE_HOME
    Start-Sleep -Milliseconds 500

    $startOutput = Invoke-AdbText shell am start -W -n $ActivityComponent
    Start-Sleep -Seconds 2
    $perfLog = Invoke-AdbText logcat -d -s SwiftFlorisPerf:I

    $totalTime = [regex]::Match($startOutput, "TotalTime:\s*(\d+)")
    $waitTime = [regex]::Match($startOutput, "WaitTime:\s*(\d+)")
    $firstRenderMatches = [regex]::Matches($perfLog, "swiftfloris\.ime\.firstRenderMs=([0-9.]+)")
    $firstRenderMs = $null
    if ($firstRenderMatches.Count -gt 0) {
        $firstRenderMs = [double]$firstRenderMatches[$firstRenderMatches.Count - 1].Groups[1].Value
    }

    $runs += [ordered]@{
        iteration = $i
        activityTotalTimeMs = if ($totalTime.Success) { [int]$totalTime.Groups[1].Value } else { $null }
        activityWaitTimeMs = if ($waitTime.Success) { [int]$waitTime.Groups[1].Value } else { $null }
        imeFirstRenderMs = $firstRenderMs
        startOutput = $startOutput
    }
}

$summary = [ordered]@{
    activityTotalTimeMedianMs = Get-Median @($runs | ForEach-Object { $_.activityTotalTimeMs } | Where-Object { $_ -ne $null })
    activityWaitTimeMedianMs = Get-Median @($runs | ForEach-Object { $_.activityWaitTimeMs } | Where-Object { $_ -ne $null })
    imeFirstRenderMedianMs = Get-Median @($runs | ForEach-Object { $_.imeFirstRenderMs } | Where-Object { $_ -ne $null })
}

$result = [ordered]@{
    benchmark = "imeFirstRender"
    measuredAt = (Get-Date).ToString("o")
    packageName = $PackageName
    imeComponent = $ImeComponent
    activityComponent = $ActivityComponent
    iterations = $Iterations
    device = $device
    summary = $summary
    runs = $runs
}

$json = $result | ConvertTo-Json -Depth 8
Set-Content -LiteralPath $OutputPath -Value $json -Encoding UTF8
if ($previousIme -and $previousIme -ne $ImeComponent) {
    & adb @AdbSerialArgs shell ime set $previousIme | Out-Null
}
$json
