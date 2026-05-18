param(
    [int]$Iterations = 5,
    [string]$Serial = "",
    [string]$ApkPath = "app/build/outputs/apk/benchmark/app-benchmark.apk",
    [string]$OutputPath = "docs/benchmark-results/baseline-$(Get-Date -Format yyyy-MM-dd)-ime-candidate-row.json",
    [string]$Text = "hello world this is a test"
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

function Get-MaxOrNull([double[]]$Values) {
    if ($Values.Count -eq 0) {
        return $null
    }
    return ($Values | Measure-Object -Maximum).Maximum
}

function Get-SumOrNull([double[]]$Values) {
    if ($Values.Count -eq 0) {
        return $null
    }
    return ($Values | Measure-Object -Sum).Sum
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

$escapedText = ($Text -replace "\s+", "%s")
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
    Invoke-Adb shell logcat -c
    Invoke-Adb shell input text $escapedText
    Start-Sleep -Seconds 2
    $perfLog = Invoke-AdbText logcat -d -s SwiftFlorisPerf:I

    $recomposeMatches = [regex]::Matches(
        $perfLog,
        "swiftfloris\.smartbar\.candidates\.recomposeMs=([0-9.]+)\s+candidateCount=(\d+)\s+displayMode=([A-Z_]+)"
    )
    $recomposeMs = @()
    $recomposeCandidateCounts = @()
    $displayModes = @()
    foreach ($match in $recomposeMatches) {
        $recomposeMs += [double]$match.Groups[1].Value
        $recomposeCandidateCounts += [int]$match.Groups[2].Value
        $displayModes += $match.Groups[3].Value
    }

    $suggestMatches = [regex]::Matches(
        $perfLog,
        "swiftfloris\.nlp\.suggestMs=([0-9.]+)\s+currentWordLength=(\d+)\s+candidateCount=(\d+)"
    )
    $suggestMs = @()
    $suggestCandidateCounts = @()
    foreach ($match in $suggestMatches) {
        $suggestMs += [double]$match.Groups[1].Value
        $suggestCandidateCounts += [int]$match.Groups[3].Value
    }

    $runs += [ordered]@{
        iteration = $i
        text = $Text
        recomposeCount = $recomposeMs.Count
        recomposeMedianMs = Get-Median @($recomposeMs)
        recomposeMaxMs = Get-MaxOrNull @($recomposeMs)
        recomposeTotalMs = Get-SumOrNull @($recomposeMs)
        recomposeMaxCandidateCount = Get-MaxOrNull @($recomposeCandidateCounts)
        displayModes = @($displayModes | Select-Object -Unique)
        nlpSuggestCount = $suggestMs.Count
        nlpSuggestMedianMs = Get-Median @($suggestMs)
        nlpSuggestMaxMs = Get-MaxOrNull @($suggestMs)
        nlpSuggestMaxCandidateCount = Get-MaxOrNull @($suggestCandidateCounts)
        startOutput = $startOutput
        perfLog = $perfLog
    }
}

$summary = [ordered]@{
    recomposeCountMedian = Get-Median @($runs | ForEach-Object { $_.recomposeCount } | Where-Object { $_ -ne $null })
    recomposeMedianOfRunMediansMs = Get-Median @($runs | ForEach-Object { $_.recomposeMedianMs } | Where-Object { $_ -ne $null })
    recomposeMaxMedianMs = Get-Median @($runs | ForEach-Object { $_.recomposeMaxMs } | Where-Object { $_ -ne $null })
    recomposeTotalMedianMs = Get-Median @($runs | ForEach-Object { $_.recomposeTotalMs } | Where-Object { $_ -ne $null })
    recomposeMaxCandidateCountMedian = Get-Median @($runs | ForEach-Object { $_.recomposeMaxCandidateCount } | Where-Object { $_ -ne $null })
    nlpSuggestCountMedian = Get-Median @($runs | ForEach-Object { $_.nlpSuggestCount } | Where-Object { $_ -ne $null })
    nlpSuggestMedianOfRunMediansMs = Get-Median @($runs | ForEach-Object { $_.nlpSuggestMedianMs } | Where-Object { $_ -ne $null })
    nlpSuggestMaxMedianMs = Get-Median @($runs | ForEach-Object { $_.nlpSuggestMaxMs } | Where-Object { $_ -ne $null })
    nlpSuggestMaxCandidateCountMedian = Get-Median @($runs | ForEach-Object { $_.nlpSuggestMaxCandidateCount } | Where-Object { $_ -ne $null })
}

$result = [ordered]@{
    benchmark = "candidateRowRecomposition"
    measuredAt = (Get-Date).ToString("o")
    packageName = $PackageName
    imeComponent = $ImeComponent
    activityComponent = $ActivityComponent
    iterations = $Iterations
    inputText = $Text
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
