param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$KeyboardApk = "app/build/outputs/apk/debug/app-debug.apk",
    [string]$EditorApk = "app/build/outputs/apk/benchmark/app-benchmark.apk",
    [int]$LimitPercent = 1,
    [int]$KillTimeoutSeconds = 240,
    [switch]$EnableLimiter
)

# Checks that the keyboard comes back whole after Android 17's memory limiter kills it.
#
# 1. Types the same word pair three times, hides the keyboard, and expects the learned pair on disk
#    (learning is otherwise saved every 20 words, and a process killed off screen never gets
#    another chance).
# 2. Shows the keyboard again and sets a limit on its process with `am memory-limiter manual`.
# 3. Expects the platform to record that pid's end as a memory-limiter kill, the restarted keyboard
#    to classify it the same way (SwiftFlorisExit log and its exit history file), the keyboard to
#    show again in a new process with no crash, typing to reach the editor, and the Smartbar to
#    predict the pair's second word from the learning the new process loaded from disk.
#
# The words are ordinary dictionary words, so autocorrect leaves them alone. The emulator exposes a
# hardware keyboard, so `input text` goes through the physical keyboard path and the keyboard shows
# just the Smartbar, which is enough for the prediction check.
#
# The keyboard is the debug build (debuggable, so run-as can read its files) and the editor is the
# benchmark build's input screen, which runs in its own process. Needs an Android 17 (API 37)
# device. The emulator image ships the limiter without a config, so it never kills anything there;
# -EnableLimiter turns it on for a userdebug image by bind-mounting a config over /vendor/etc and
# restarting the framework. That mount and permissive SELinux last until the emulator reboots.
# The debug build's data is cleared first.

$ErrorActionPreference = "Stop"

$KeyboardPackage = "io.github.sysadmindoc.swiftfloris.debug"
$ImeComponent = "$KeyboardPackage/dev.patrickgold.florisboard.FlorisImeService"
$EditorPackage = "io.github.sysadmindoc.swiftfloris.bench"
$EditorActivity = "$EditorPackage/dev.patrickgold.florisboard.benchmark.BenchmarkInputActivity"
$FirstWord = "purple"
$SecondWord = "window"
$LearnedPair = "$FirstWord`t$SecondWord"
$UiDump = "/data/local/tmp/swiftfloris-limiter-ui.xml"
$AdbSerialArgs = @("-s", $Serial)
$LimiterConfig = @"
<?xml version="1.0" encoding="utf-8"?>
<MemoryLimiterConfig>
    <version>1</version>
    <configList>
        <limitSet>
            <minimumRequiredMemTotal>0</minimumRequiredMemTotal>
            <memVisible>4096</memVisible>
            <memNotVisible>4096</memNotVisible>
            <swapVisible>4096</swapVisible>
            <swapNotVisible>4096</swapNotVisible>
        </limitSet>
    </configList>
</MemoryLimiterConfig>
"@

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

# Exit codes are ignored: pidof, grep and run-as on a missing file fail on purpose here.
function Invoke-AdbQuiet {
    $output = & adb @script:AdbSerialArgs @args 2>$null
    return ($output -join "`n")
}

function Wait-Until([scriptblock]$Condition, [int]$TimeoutSeconds, [string]$What) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (& $Condition) {
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out after $TimeoutSeconds s waiting for $What"
}

function Get-KeyboardPid {
    return (Invoke-AdbQuiet shell pidof $KeyboardPackage).Trim()
}

function Test-KeyboardShown {
    return (Invoke-AdbQuiet shell dumpsys input_method) -match "mInputShown=true"
}

function Test-LimiterEnabled {
    return (Invoke-AdbQuiet shell am memory-limiter status) -match "killing=true"
}

function Read-LearnedPairs {
    return Invoke-AdbQuiet shell run-as $KeyboardPackage sh -c "'cat files/personal_bigrams_*.tsv'"
}

# Every window's text, the keyboard's Smartbar included. The old dump is deleted first, so a failed
# dump can't pass off the previous screen as this one.
function Get-ScreenText {
    Invoke-AdbQuiet shell rm -f $UiDump | Out-Null
    Invoke-AdbQuiet shell uiautomator dump --windows $UiDump | Out-Null
    return Invoke-AdbQuiet shell cat $UiDump
}

# One word, then its space, with pauses. In a single `input text` burst the physical-keyboard space
# can arrive before the editor reports the word, and the keyboard then corrects a stale prefix
# (a ROADMAP P2), which would learn the wrong pair here.
function Send-Words([string[]]$Words) {
    foreach ($word in $Words) {
        Invoke-Adb shell input text $word
        Start-Sleep -Milliseconds 400
        Invoke-Adb shell input keyevent KEYCODE_SPACE
        Start-Sleep -Milliseconds 400
    }
}

function Show-Editor {
    Invoke-Adb shell am start -S -W -n $EditorActivity | Out-Null
    Wait-Until { Test-KeyboardShown } 30 "the keyboard to show in the editor"
}

function Enable-Limiter {
    $debuggable = (Invoke-AdbText shell getprop ro.debuggable).Trim()
    if ($debuggable -ne "1") {
        throw "The limiter has no config and this image isn't userdebug, so it can't be turned on"
    }
    Invoke-Adb root | Out-Null
    Invoke-Adb wait-for-device
    $stage = "/data/local/tmp/swiftfloris-vendor-etc"
    $configFile = [IO.Path]::Combine([IO.Path]::GetTempPath(), "memory-limiter-config.xml")
    [IO.File]::WriteAllText($configFile, $LimiterConfig)
    try {
        Invoke-Adb shell setenforce 0
        Invoke-Adb shell "rm -rf $stage && mkdir -p $stage && cp -a /vendor/etc/. $stage/"
        Invoke-Adb push $configFile "$stage/memory-limiter-config.xml" | Out-Null
        Invoke-Adb shell chmod 644 "$stage/memory-limiter-config.xml"
        Invoke-Adb shell mount --bind $stage /vendor/etc
    } finally {
        [IO.File]::Delete($configFile)
    }
    Invoke-Adb shell "stop; start"
    Start-Sleep -Seconds 10
    Wait-Until { Test-LimiterEnabled } 180 "the framework to restart with the limiter on"
    Wait-Until { (Invoke-AdbQuiet shell service check input_method) -match ": found" } 60 "the input method service"
}

function Write-Pass([string]$Message) {
    Write-Output "PASS $Message"
}

$sdk = [int](Invoke-AdbText shell getprop ro.build.version.sdk).Trim()
if ($sdk -lt 37) {
    throw "$Serial runs API $sdk; the memory limiter needs Android 17 (API 37)"
}
if (-not (Test-LimiterEnabled)) {
    if (-not $EnableLimiter) {
        throw "The memory limiter is off on $Serial (no config). Rerun with -EnableLimiter on a userdebug image."
    }
    Enable-Limiter
}
Write-Pass "memory limiter is on: $((Invoke-AdbText shell am memory-limiter status).Trim())"

$previousIme = (Invoke-AdbQuiet shell settings get secure default_input_method).Trim()
try {
    Invoke-Adb install -r (Resolve-Path -LiteralPath $KeyboardApk) | Out-Null
    Invoke-Adb install -r (Resolve-Path -LiteralPath $EditorApk) | Out-Null
    Invoke-Adb shell pm clear $KeyboardPackage | Out-Null
    # pm clear kills the keyboard, and the system doesn't bind it again until the focused window
    # changes, so the editor's first show request finds no keyboard ("not served"). `ime set` alone
    # is a no-op when the keyboard is already selected; disabling it first forces a fresh bind.
    Invoke-AdbQuiet shell ime disable $ImeComponent | Out-Null
    Invoke-Adb shell ime enable $ImeComponent | Out-Null
    Invoke-Adb shell ime set $ImeComponent | Out-Null
    Wait-Until { Get-KeyboardPid } 30 "the keyboard to be bound again after its data was cleared"
    Invoke-Adb logcat -c

    # 1. Learning under the 20-word batch reaches disk when the keyboard hides.
    Show-Editor
    for ($i = 0; $i -lt 3; $i++) {
        Send-Words @($FirstWord, $SecondWord)
    }
    Invoke-Adb shell input keyevent KEYCODE_BACK
    Wait-Until { -not (Test-KeyboardShown) } 15 "the keyboard to hide"
    Wait-Until { (Read-LearnedPairs).Contains($LearnedPair) } 15 "the learned pair to reach disk after the keyboard hid"
    Write-Pass "learned pair saved when the keyboard hid"

    # 2. A limiter kill while the keyboard is on screen.
    Show-Editor
    $oldPid = Get-KeyboardPid
    if (-not $oldPid) {
        throw "The keyboard is showing but $KeyboardPackage has no process"
    }
    # Cleared here, so the SwiftFlorisExit line checked below can only come from this kill.
    Invoke-Adb logcat -c
    $limitOutput = (Invoke-AdbText shell am memory-limiter manual $oldPid $LimitPercent).Trim()
    Write-Output "Limit set on pid ${oldPid}: $limitOutput"
    Wait-Until { (Get-KeyboardPid) -ne $oldPid } $KillTimeoutSeconds "the limiter to kill pid $oldPid"
    # The record for this pid, up to the next one, must name the limiter in its description
    # ("reason=13 (OTHER KILLS BY SYSTEM) ... description=MemoryLimiter:AnonSwap"). It can land
    # a moment after the process is gone, so it's read again until it shows up.
    $limiterRecord = "(?s)pid=$oldPid\b(?:(?!ApplicationExitInfo #).)*?description=MemoryLimiter"
    $exitInfoDeadline = (Get-Date).AddSeconds(15)
    do {
        $exitInfo = Invoke-AdbText shell dumpsys activity exit-info $KeyboardPackage
        if ($exitInfo -match $limiterRecord) {
            break
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $exitInfoDeadline)
    if ($exitInfo -notmatch $limiterRecord) {
        throw "pid $oldPid ended, but exit-info had no MemoryLimiter record for it after 15 s:`n$exitInfo"
    }
    Write-Pass "platform recorded pid $oldPid as a memory-limiter kill"

    # 3. The keyboard comes back whole.
    if (-not (Test-KeyboardShown) -or -not (Get-KeyboardPid)) {
        Show-Editor
    }
    $newPid = Get-KeyboardPid
    if (-not $newPid -or $newPid -eq $oldPid) {
        throw "The keyboard didn't come back in a new process (pid now '$newPid')"
    }
    Write-Pass "keyboard showing again in pid $newPid"

    Wait-Until {
        (Invoke-AdbQuiet logcat -d -v raw -s SwiftFlorisExit:I) -match "memory limiter kill"
    } 30 "the restarted keyboard to log the kill"
    $history = Invoke-AdbQuiet shell run-as $KeyboardPackage cat no_backup/process-exits.txt
    $newest = ($history -split "`n" | Select-Object -First 1)
    if ($newest -notmatch "^\d+ $oldPid MemoryLimiter ") {
        throw "The exit history's newest record isn't a limiter kill: '$newest'"
    }
    Write-Pass "keyboard classified the kill as a memory-limiter kill"

    if (-not (Read-LearnedPairs).Contains($LearnedPair)) {
        throw "The learned pair is gone after the restart"
    }
    Write-Pass "learned pair still on disk after the restart"

    Send-Words @($FirstWord)
    $screen = ""
    Wait-Until {
        $script:screen = Get-ScreenText
        ($script:screen -match "(?i)text=`"$FirstWord ") -and ($script:screen -match "(?i)text=`"$SecondWord`"")
    } 20 "'$FirstWord ' in the editor and '$SecondWord' predicted in the Smartbar"
    Write-Pass "typing reaches the editor and the Smartbar predicts '$SecondWord' after '$FirstWord'"

    $crashes = Invoke-AdbQuiet logcat -d -b crash
    if ($crashes -match [regex]::Escape($KeyboardPackage)) {
        throw "The keyboard crashed during the check:`n$crashes"
    }
    Write-Pass "no keyboard crash in the crash buffer"
    Write-Output "ALL CHECKS PASSED"
} finally {
    if ($previousIme -and $previousIme -ne "null" -and $previousIme -ne $ImeComponent) {
        & adb @AdbSerialArgs shell ime set $previousIme | Out-Null
    }
    & adb @AdbSerialArgs shell am force-stop $EditorPackage | Out-Null
}
