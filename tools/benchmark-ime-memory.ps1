param(
    [int]$Iterations = 3,
    [string]$Serial = "",
    [string]$ApkPath = "app/build/outputs/apk/benchmark/app-benchmark.apk",
    [string]$Scenarios = "dictionary,glide,emojiSearch,clipboard,sticker,contextModel,firstRender",
    [string]$OutputPath = "docs/benchmark-results/baseline-$(Get-Date -Format yyyy-MM-dd)-ime-memory.json"
)

# Memory baseline for the keyboard's heavy workloads, one fresh process per run. The in-app
# scenarios log samples from BenchmarkMemoryActivity. firstRender opens BenchmarkInputActivity
# with the benchmark keyboard selected and reads dumpsys meminfo once the keyboard has drawn.

$ErrorActionPreference = "Stop"

$PackageName = "io.github.sysadmindoc.swiftfloris.bench"
$ImeComponent = "$PackageName/dev.patrickgold.florisboard.FlorisImeService"
$InputActivity = "$PackageName/dev.patrickgold.florisboard.benchmark.BenchmarkInputActivity"
$MemoryActivity = "$PackageName/dev.patrickgold.florisboard.benchmark.BenchmarkMemoryActivity"
$ScenarioExtra = "dev.patrickgold.florisboard.benchmark.MEMORY_SCENARIO"
# codeKb is mapped files: the APK's code and resources, and the context model mapped out of it.
$Fields = @("pssKb", "javaHeapKb", "nativeHeapKb", "graphicsKb", "codeKb")
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

function Start-FreshProcess {
    Invoke-Adb shell logcat -c
    Invoke-Adb shell am force-stop $PackageName
    Invoke-Adb shell input keyevent KEYCODE_HOME
    Start-Sleep -Milliseconds 500
}

function Invoke-MemoryScenario([string]$ScenarioName, [int]$Iteration) {
    Start-FreshProcess
    Invoke-Adb shell am start -W -n $MemoryActivity --es $ScenarioExtra $ScenarioName | Out-Null
    $deadline = (Get-Date).AddSeconds(120)
    $perfLog = ""
    while ((Get-Date) -lt $deadline) {
        $perfLog = Invoke-AdbText logcat -d -v raw -s SwiftFlorisPerf:*
        if ($perfLog -match "swiftfloris\.mem\.(done|benchmarkFailed)") {
            break
        }
        Start-Sleep -Seconds 1
    }
    if ($perfLog -match "swiftfloris\.mem\.benchmarkFailed") {
        throw "Scenario $ScenarioName failed on iteration ${Iteration}:`n$perfLog"
    }
    if ($perfLog -notmatch "swiftfloris\.mem\.done scenario=$ScenarioName") {
        throw "Scenario $ScenarioName did not finish within 120 seconds on iteration $Iteration"
    }
    $phases = [ordered]@{}
    foreach ($line in ($perfLog -split "`n")) {
        $phaseMatch = [regex]::Match($line, "swiftfloris\.mem\.result scenario=$ScenarioName phase=(\w+) (.*)$")
        if (-not $phaseMatch.Success) {
            continue
        }
        $values = [ordered]@{}
        foreach ($pair in [regex]::Matches($phaseMatch.Groups[2].Value, "(\w+)=([-0-9.]+)")) {
            $values[$pair.Groups[1].Value] = [double]$pair.Groups[2].Value
        }
        $phases[$phaseMatch.Groups[1].Value] = $values
    }
    if ($phases.Count -eq 0) {
        throw "Scenario $ScenarioName logged no samples on iteration $Iteration"
    }
    return [ordered]@{ scenario = $ScenarioName; iteration = $Iteration; phases = $phases }
}

function Read-MemInfo {
    $memInfo = Invoke-AdbText shell dumpsys meminfo $PackageName
    $read = {
        param([string]$Pattern)
        $found = [regex]::Match($memInfo, $Pattern)
        if ($found.Success) { [double]$found.Groups[1].Value } else { $null }
    }
    $pss = & $read "TOTAL PSS:\s+(\d+)"
    if ($null -eq $pss) {
        $pss = & $read "(?m)^\s*TOTAL\s+(\d+)"
    }
    return [ordered]@{
        pssKb = $pss
        javaHeapKb = & $read "Java Heap:\s+(\d+)"
        nativeHeapKb = & $read "Native Heap:\s+(\d+)"
        graphicsKb = & $read "Graphics:\s+(\d+)"
        codeKb = & $read "Code:\s+(\d+)"
    }
}

function Invoke-FirstRender([int]$Iteration) {
    Start-FreshProcess
    Invoke-Adb shell ime enable $ImeComponent | Out-Null
    Invoke-Adb shell ime set $ImeComponent | Out-Null
    $selectedIme = (Invoke-AdbText shell settings get secure default_input_method).Trim()
    if ($selectedIme -ne $ImeComponent) {
        throw "Expected $ImeComponent to be selected for iteration $Iteration, got $selectedIme"
    }
    Invoke-Adb shell am start -W -n $InputActivity | Out-Null
    Start-Sleep -Seconds 3
    $perfLog = Invoke-AdbText logcat -d -s SwiftFlorisPerf:I
    if ($perfLog -notmatch "swiftfloris\.ime\.firstRenderMs=") {
        throw "The keyboard didn't log a first render on iteration $Iteration"
    }
    $phases = [ordered]@{ rendered = Read-MemInfo }
    return [ordered]@{ scenario = "firstRender"; iteration = $Iteration; phases = $phases }
}

$scenarioList = @($Scenarios.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ })
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
$device = Get-BenchmarkDeviceMetadata

# The in-app scenarios run first, under the keyboard that was already selected, so the benchmark
# keyboard's own service isn't sharing their process. firstRender switches keyboards last.
$runs = @()
foreach ($scenarioName in ($scenarioList | Where-Object { $_ -ne "firstRender" })) {
    for ($i = 1; $i -le $Iterations; $i++) {
        $runs += Invoke-MemoryScenario $scenarioName $i
    }
}
if ($scenarioList -contains "firstRender") {
    for ($i = 1; $i -le $Iterations; $i++) {
        $runs += Invoke-FirstRender $i
    }
}
Invoke-Adb shell am force-stop $PackageName
if ($previousIme -and $previousIme -ne $ImeComponent) {
    Invoke-Adb shell ime set $previousIme
}

$summary = [ordered]@{}
foreach ($scenarioName in $scenarioList) {
    $scenarioRuns = @($runs | Where-Object { $_.scenario -eq $scenarioName })
    $phaseNames = @($scenarioRuns | ForEach-Object { $_.phases.Keys } | Select-Object -Unique)
    $phaseSummary = [ordered]@{}
    foreach ($phaseName in $phaseNames) {
        $medians = [ordered]@{}
        foreach ($field in $Fields) {
            $values = @($scenarioRuns | ForEach-Object { $_.phases[$phaseName][$field] } | Where-Object { $null -ne $_ -and $_ -ge 0 })
            $medians["${field}Median"] = Get-Median $values
        }
        $phaseSummary[$phaseName] = $medians
    }
    $summary[$scenarioName] = $phaseSummary
}

$result = [ordered]@{
    benchmark = "imeMemory"
    measuredAt = (Get-Date).ToString("o")
    packageName = $PackageName
    iterations = $Iterations
    keyboardDuringScenarios = $previousIme
    device = $device
    summary = $summary
    runs = $runs
}

$json = ConvertTo-Json -InputObject $result -Depth 8
Set-Content -LiteralPath $OutputPath -Value $json -Encoding UTF8
$json
