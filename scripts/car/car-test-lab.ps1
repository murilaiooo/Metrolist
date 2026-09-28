param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("start", "set-viewport", "install", "measure-startup", "screenshot")]
    [string]$Action,

    [string]$Apk,

    [ValidateSet("800x480", "1024x600", "1280x720")]
    [string]$Viewport = "1024x600",

    [string]$Output = (Join-Path (Get-Location) "metrolist-car.png")
)

$ErrorActionPreference = "Stop"

$AvdName = "MetroList_Car_API29"
$SdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$Adb = Join-Path $SdkRoot "platform-tools\adb.exe"
$Emulator = Join-Path $SdkRoot "emulator\emulator.exe"
$Package = "com.metrolist.music.car.debug"
$Activity = "$Package/com.metrolist.music.CarMainActivity"

function Assert-Tool([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Required Android SDK tool not found: $Path"
    }
}

function Assert-Avd {
    $avds = & $Emulator -list-avds
    if ($LASTEXITCODE -ne 0 -or $AvdName -notin $avds) {
        throw "Required AVD '$AvdName' is not installed."
    }
}

function Get-ConnectedEmulator {
    $line = (& $Adb devices) | Where-Object { $_ -match "^emulator-\d+\s+device$" } | Select-Object -First 1
    if ($line) { return ($line -split "\s+")[0] }
    return $null
}

function Wait-ForBoot {
    & $Adb wait-for-device | Out-Null
    $deadline = (Get-Date).AddMinutes(2)
    do {
        if ((& $Adb shell getprop sys.boot_completed 2>$null).Trim() -eq "1") {
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "AVD did not finish booting within two minutes."
}

function Require-Device {
    if (-not (Get-ConnectedEmulator)) {
        throw "MetroList Car emulator is not running. Run: .\scripts\car\car-test-lab.ps1 start"
    }
}

function Read-StartTotalTime([string[]]$Lines) {
    $match = $Lines | Where-Object { $_ -match "^TotalTime:\s+(\d+)$" } | Select-Object -Last 1
    if (-not $match) {
        throw "Could not read TotalTime from am start output."
    }
    return [int](($match -split ":")[1].Trim())
}

function Median([int[]]$Values) {
    $sorted = $Values | Sort-Object
    return $sorted[[math]::Floor($sorted.Count / 2)]
}

Assert-Tool $Adb
Assert-Tool $Emulator
Assert-Avd

switch ($Action) {
    "start" {
        if (-not (Get-ConnectedEmulator)) {
            Start-Process -FilePath $Emulator -ArgumentList @(
                "-avd", $AvdName,
                "-no-snapshot",
                "-gpu", "auto"
            ) | Out-Null
        }
        Wait-ForBoot
        & $Adb shell wm size
        & $Adb shell wm density
    }

    "set-viewport" {
        Require-Device
        & $Adb shell wm size $Viewport
        & $Adb shell wm density 160
        & $Adb shell wm size
        & $Adb shell wm density
    }

    "install" {
        Require-Device
        if ([string]::IsNullOrWhiteSpace($Apk)) {
            throw "-Apk is required for the install action."
        }
        $resolved = (Resolve-Path -LiteralPath $Apk).Path
        & $Adb install -r $resolved
        if ($LASTEXITCODE -ne 0) {
            throw "ADB install failed."
        }
    }

    "measure-startup" {
        Require-Device
        $cold = @()
        $hot = @()

        1..3 | ForEach-Object {
            & $Adb shell am force-stop $Package | Out-Null
            $result = @(& $Adb shell am start -W -n $Activity)
            $cold += Read-StartTotalTime $result
        }

        1..3 | ForEach-Object {
            & $Adb shell input keyevent 3 | Out-Null
            Start-Sleep -Milliseconds 500
            $result = @(& $Adb shell am start -W -n $Activity)
            $hot += Read-StartTotalTime $result
        }

        "Cold ms: $($cold -join ', ')"
        "Cold median ms: $(Median $cold)"
        "Hot ms: $($hot -join ', ')"
        "Hot median ms: $(Median $hot)"
    }

    "screenshot" {
        Require-Device
        $target = [System.IO.Path]::GetFullPath($Output)
        $parent = Split-Path -Parent $target
        if ($parent -and -not (Test-Path -LiteralPath $parent)) {
            New-Item -ItemType Directory -Force -Path $parent | Out-Null
        }
        $remote = "/sdcard/metrolist-car-test-lab.png"
        & $Adb shell screencap -p $remote | Out-Null
        & $Adb pull $remote $target | Out-Null
        & $Adb shell rm -f $remote | Out-Null
        if (-not (Test-Path -LiteralPath $target)) {
            throw "Screenshot was not created: $target"
        }
        $target
    }
}
