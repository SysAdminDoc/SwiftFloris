param(
    [int]$Iterations = 5,
    [string]$Serial = "",
    [string]$ApkPath = "app/build/outputs/apk/benchmark/app-benchmark.apk",
    [string]$OutputPath = "docs/benchmark-results/baseline-$(Get-Date -Format yyyy-MM-dd)-ime-suggestion-latency.json",
    [string]$Text = "teh"
)

$ErrorActionPreference = "Stop"

$PackageName = "dev.patrickgold.florisboard.bench"
$ActivityComponent = "$PackageName/dev.patrickgold.florisboard.benchmark.BenchmarkSuggestionActivity"
$InputExtra = "dev.patrickgold.florisboard.benchmark.INPUT_TEXT"
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

Invoke-Adb install -r $resolvedApk

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
    Invoke-Adb shell input keyevent KEYCODE_HOME
    Start-Sleep -Milliseconds 500

    $startOutput = Invoke-AdbText shell am start -W -n $ActivityComponent --es $InputExtra $Text
    Start-Sleep -Seconds 2
    $perfLog = Invoke-AdbText logcat -d -s SwiftFlorisPerf:I

    $suggestMatches = [regex]::Matches(
        $perfLog,
        "swiftfloris\.nlp\.firstSuggestionMs=([0-9.]+)\s+currentWordLength=(\d+)\s+candidateCount=(\d+)"
    )
    $selectedMatch = $null
    foreach ($match in $suggestMatches) {
        $wordLength = [int]$match.Groups[2].Value
        if ($wordLength -ge $Text.Length) {
            $selectedMatch = $match
        }
    }
    if (-not $selectedMatch -and $suggestMatches.Count -gt 0) {
        $selectedMatch = $suggestMatches[$suggestMatches.Count - 1]
    }

    $runs += [ordered]@{
        iteration = $i
        inputText = $Text
        suggestionLatencyMs = if ($selectedMatch) { [double]$selectedMatch.Groups[1].Value } else { $null }
        currentWordLength = if ($selectedMatch) { [int]$selectedMatch.Groups[2].Value } else { $null }
        candidateCount = if ($selectedMatch) { [int]$selectedMatch.Groups[3].Value } else { $null }
        suggestionLogCount = $suggestMatches.Count
        startOutput = $startOutput
        perfLog = $perfLog
    }
}

$summary = [ordered]@{
    suggestionLatencyMedianMs = Get-Median @($runs | ForEach-Object { $_.suggestionLatencyMs } | Where-Object { $_ -ne $null })
    candidateCountMedian = Get-Median @($runs | ForEach-Object { $_.candidateCount } | Where-Object { $_ -ne $null })
}

$result = [ordered]@{
    benchmark = "firstSuggestionLatency"
    measuredAt = (Get-Date).ToString("o")
    packageName = $PackageName
    activityComponent = $ActivityComponent
    iterations = $Iterations
    inputText = $Text
    device = $device
    summary = $summary
    runs = $runs
}

$json = $result | ConvertTo-Json -Depth 8
Set-Content -LiteralPath $OutputPath -Value $json -Encoding UTF8
$json
