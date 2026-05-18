param(
    [int]$Iterations = 5,
    [string]$Serial = "",
    [string]$ApkPath = "app/build/outputs/apk/benchmark/app-benchmark.apk",
    [string]$OutputPath = "docs/benchmark-results/baseline-$(Get-Date -Format yyyy-MM-dd)-ime-dictionary-load.json",
    [string]$Text = "zzzxqq"
)

$ErrorActionPreference = "Stop"

$PackageName = "dev.patrickgold.florisboard.bench"
$ActivityComponent = "$PackageName/dev.patrickgold.florisboard.benchmark.BenchmarkDictionaryActivity"
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

function Last-Match([System.Text.RegularExpressions.MatchCollection]$Matches) {
    if ($Matches.Count -eq 0) {
        return $null
    }
    return $Matches[$Matches.Count - 1]
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
    Start-Sleep -Seconds 3
    $perfLog = Invoke-AdbText logcat -d -s SwiftFlorisPerf:I

    $loadMatch = Last-Match ([regex]::Matches(
        $perfLog,
        "swiftfloris\.dict\.loadMs=([0-9.]+)\s+language=([a-z]+)\s+wordCount=(\d+)"
    ))
    $preloadMatch = Last-Match ([regex]::Matches(
        $perfLog,
        "swiftfloris\.dict\.preloadMs=([0-9.]+)\s+language=([a-z]+)"
    ))
    $postSuggestionMatch = Last-Match ([regex]::Matches(
        $perfLog,
        "swiftfloris\.dict\.postPreloadSuggestionMs=([0-9.]+)\s+currentWordLength=(\d+)\s+candidateCount=(\d+)"
    ))
    $postSpellMatch = Last-Match ([regex]::Matches(
        $perfLog,
        "swiftfloris\.dict\.postPreloadSpellMs=([0-9.]+)\s+wordLength=(\d+)\s+suggestionCount=(\d+)"
    ))

    $symSpellDistance1Ms = $null
    $symSpellDistance2Ms = $null
    $symSpellWordCount1 = $null
    $symSpellWordCount2 = $null
    $symSpellMatches = [regex]::Matches(
        $perfLog,
        "swiftfloris\.nlp\.symspellBuildMs=([0-9.]+)\s+maxDistance=(\d+)\s+wordCount=(\d+)"
    )
    foreach ($match in $symSpellMatches) {
        $distance = [int]$match.Groups[2].Value
        if ($distance -eq 1) {
            $symSpellDistance1Ms = [double]$match.Groups[1].Value
            $symSpellWordCount1 = [int]$match.Groups[3].Value
        } elseif ($distance -eq 2) {
            $symSpellDistance2Ms = [double]$match.Groups[1].Value
            $symSpellWordCount2 = [int]$match.Groups[3].Value
        }
    }

    $runs += [ordered]@{
        iteration = $i
        inputText = $Text
        dictionaryLoadMs = if ($loadMatch) { [double]$loadMatch.Groups[1].Value } else { $null }
        dictionaryLanguage = if ($loadMatch) { $loadMatch.Groups[2].Value } else { $null }
        dictionaryWordCount = if ($loadMatch) { [int]$loadMatch.Groups[3].Value } else { $null }
        dictionaryPreloadMs = if ($preloadMatch) { [double]$preloadMatch.Groups[1].Value } else { $null }
        symSpellDistance1BuildMs = $symSpellDistance1Ms
        symSpellDistance1WordCount = $symSpellWordCount1
        symSpellDistance2BuildMs = $symSpellDistance2Ms
        symSpellDistance2WordCount = $symSpellWordCount2
        postPreloadSpellMs = if ($postSpellMatch) { [double]$postSpellMatch.Groups[1].Value } else { $null }
        spellingSuggestionCount = if ($postSpellMatch) { [int]$postSpellMatch.Groups[3].Value } else { $null }
        postPreloadSuggestionMs = if ($postSuggestionMatch) { [double]$postSuggestionMatch.Groups[1].Value } else { $null }
        candidateCount = if ($postSuggestionMatch) { [int]$postSuggestionMatch.Groups[3].Value } else { $null }
        startOutput = $startOutput
        perfLog = $perfLog
    }
}

$summary = [ordered]@{
    dictionaryLoadMedianMs = Get-Median @($runs | ForEach-Object { $_.dictionaryLoadMs } | Where-Object { $_ -ne $null })
    dictionaryPreloadMedianMs = Get-Median @($runs | ForEach-Object { $_.dictionaryPreloadMs } | Where-Object { $_ -ne $null })
    symSpellDistance1BuildMedianMs = Get-Median @($runs | ForEach-Object { $_.symSpellDistance1BuildMs } | Where-Object { $_ -ne $null })
    symSpellDistance2BuildMedianMs = Get-Median @($runs | ForEach-Object { $_.symSpellDistance2BuildMs } | Where-Object { $_ -ne $null })
    postPreloadSpellMedianMs = Get-Median @($runs | ForEach-Object { $_.postPreloadSpellMs } | Where-Object { $_ -ne $null })
    postPreloadSuggestionMedianMs = Get-Median @($runs | ForEach-Object { $_.postPreloadSuggestionMs } | Where-Object { $_ -ne $null })
    spellingSuggestionCountMedian = Get-Median @($runs | ForEach-Object { $_.spellingSuggestionCount } | Where-Object { $_ -ne $null })
    candidateCountMedian = Get-Median @($runs | ForEach-Object { $_.candidateCount } | Where-Object { $_ -ne $null })
}

$result = [ordered]@{
    benchmark = "dictionaryLoadAndPreload"
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
