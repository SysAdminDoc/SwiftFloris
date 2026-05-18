param(
    [int]$Iterations = 5,
    [string]$Serial = "",
    [string]$ApkPath = "app/build/outputs/apk/benchmark/app-benchmark.apk",
    [string]$OutputPath = "docs/benchmark-results/baseline-$(Get-Date -Format yyyy-MM-dd)-ime-theme-switch.json"
)

$ErrorActionPreference = "Stop"

$PackageName = "dev.patrickgold.florisboard.bench"
$ImeComponent = "$PackageName/dev.patrickgold.florisboard.FlorisImeService"
$ActivityComponent = "$PackageName/dev.patrickgold.florisboard.benchmark.BenchmarkThemeSwitchActivity"
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
    Start-Sleep -Seconds 6
    $perfLog = Invoke-AdbText logcat -d -s SwiftFlorisPerf:I

    $switchMatches = [regex]::Matches(
        $perfLog,
        "swiftfloris\.theme\.switchMs=([0-9.]+)\s+theme=([^\s]+)\s+source=direct\s+loadFailed=(true|false)\s+cachedThemeCount=(\d+)"
    )
    if ($switchMatches.Count -eq 0) {
        throw "No direct theme switch markers found in iteration $i"
    }
    $switchMs = @()
    $switchThemes = @()
    $loadFailures = @()
    $cachedThemeCounts = @()
    foreach ($match in $switchMatches) {
        $switchMs += [double]$match.Groups[1].Value
        $switchThemes += $match.Groups[2].Value
        $loadFailures += [bool]::Parse($match.Groups[3].Value)
        $cachedThemeCounts += [int]$match.Groups[4].Value
    }

    $stepMatches = [regex]::Matches(
        $perfLog,
        "swiftfloris\.theme\.benchmarkStepMs=([0-9.]+)\s+step=(\d+)\s+theme=([^\s]+)\s+expectedCacheHit=(true|false)"
    )
    if ($stepMatches.Count -eq 0) {
        throw "No theme benchmark step markers found in iteration $i"
    }
    $stepMs = @()
    $coldStepMs = @()
    $warmStepMs = @()
    foreach ($match in $stepMatches) {
        $duration = [double]$match.Groups[1].Value
        $expectedCacheHit = [bool]::Parse($match.Groups[4].Value)
        $stepMs += $duration
        if ($expectedCacheHit) {
            $warmStepMs += $duration
        } else {
            $coldStepMs += $duration
        }
    }

    $runs += [ordered]@{
        iteration = $i
        switchCount = $switchMs.Count
        switchMedianMs = Get-Median @($switchMs)
        switchMaxMs = Get-MaxOrNull @($switchMs)
        switchTotalMs = Get-SumOrNull @($switchMs)
        benchmarkStepMedianMs = Get-Median @($stepMs)
        benchmarkStepMaxMs = Get-MaxOrNull @($stepMs)
        benchmarkColdStepMedianMs = Get-Median @($coldStepMs)
        benchmarkWarmStepMedianMs = Get-Median @($warmStepMs)
        loadFailureCount = @($loadFailures | Where-Object { $_ }).Count
        maxCachedThemeCount = Get-MaxOrNull @($cachedThemeCounts)
        themes = @($switchThemes | Select-Object -Unique)
        startOutput = $startOutput
        perfLog = $perfLog
    }
}

$summary = [ordered]@{
    switchCountMedian = Get-Median @($runs | ForEach-Object { $_.switchCount } | Where-Object { $_ -ne $null })
    themeSwitchMedianOfRunMediansMs = Get-Median @($runs | ForEach-Object { $_.switchMedianMs } | Where-Object { $_ -ne $null })
    themeSwitchMaxMedianMs = Get-Median @($runs | ForEach-Object { $_.switchMaxMs } | Where-Object { $_ -ne $null })
    themeSwitchTotalMedianMs = Get-Median @($runs | ForEach-Object { $_.switchTotalMs } | Where-Object { $_ -ne $null })
    benchmarkStepMedianOfRunMediansMs = Get-Median @($runs | ForEach-Object { $_.benchmarkStepMedianMs } | Where-Object { $_ -ne $null })
    benchmarkStepMaxMedianMs = Get-Median @($runs | ForEach-Object { $_.benchmarkStepMaxMs } | Where-Object { $_ -ne $null })
    benchmarkColdStepMedianMs = Get-Median @($runs | ForEach-Object { $_.benchmarkColdStepMedianMs } | Where-Object { $_ -ne $null })
    benchmarkWarmStepMedianMs = Get-Median @($runs | ForEach-Object { $_.benchmarkWarmStepMedianMs } | Where-Object { $_ -ne $null })
    loadFailureCountMedian = Get-Median @($runs | ForEach-Object { $_.loadFailureCount } | Where-Object { $_ -ne $null })
    maxCachedThemeCountMedian = Get-Median @($runs | ForEach-Object { $_.maxCachedThemeCount } | Where-Object { $_ -ne $null })
}

$result = [ordered]@{
    benchmark = "themeSwitch"
    measuredAt = (Get-Date).ToString("o")
    packageName = $PackageName
    imeComponent = $ImeComponent
    activityComponent = $ActivityComponent
    iterations = $Iterations
    targetThemes = @(
        "org.florisboard.themes:swiftkey_pure_light",
        "org.florisboard.themes:m3e_nord_dark",
        "org.florisboard.themes:m3e_swiftkey_pure_dark",
        "org.florisboard.themes:m3e_nord_dark",
        "org.florisboard.themes:swiftkey_pure_light"
    )
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
