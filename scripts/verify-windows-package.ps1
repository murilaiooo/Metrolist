param(
    [string] $PackageRoot = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')) 'desktop-app\build\compose\binaries\main\app\MetroListWindows')
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$VersionLine =
    Get-Content (Join-Path $ProjectRoot 'gradle.properties') |
    Where-Object { $_ -match '^metrolistWindowsVersion=' } |
    Select-Object -Last 1
if (-not $VersionLine) {
    throw 'metrolistWindowsVersion is missing from gradle.properties.'
}
$ExpectedVersion = ($VersionLine -split '=', 2)[1].Trim()

$Exe = Join-Path $PackageRoot 'MetroListWindows.exe'
$Resources = Join-Path $PackageRoot 'app\resources'
$Helper = Join-Path $Resources 'auth-helper\Metrolist.AuthHelper.exe'
$Mpv = Join-Path $Resources 'mpv\libmpv-2.dll'

foreach ($Required in @($Exe, $Helper, $Mpv)) {
    if (-not (Test-Path $Required -PathType Leaf)) {
        throw "Required packaged component is missing: $Required"
    }
}

& $Helper --self-test | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Packaged auth helper self-test failed.'
}

& $Helper --runtime-check | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Packaged WebView2 runtime check failed.'
}

$Process = Start-Process -FilePath $Exe -ArgumentList '--startup-check' -PassThru -Wait
if ($Process.ExitCode -ne 0) {
    throw "MetroList Windows startup check failed with exit code $($Process.ExitCode)."
}

$RuntimeProcess = Start-Process -FilePath $Exe -ArgumentList '--runtime-open-check' -PassThru -Wait
if ($RuntimeProcess.ExitCode -ne 0) {
    throw "MetroList Windows runtime-open check failed with exit code $($RuntimeProcess.ExitCode)."
}

$VersionOutput = [IO.Path]::GetTempFileName()
try {
    $VersionProcess = Start-Process -FilePath $Exe -ArgumentList '--version' -RedirectStandardOutput $VersionOutput -PassThru -Wait
    if ($VersionProcess.ExitCode -ne 0) {
        throw "MetroList Windows version check failed with exit code $($VersionProcess.ExitCode)."
    }
    $ActualVersion = (Get-Content $VersionOutput -Raw).Trim()
    if ($ActualVersion -ne $ExpectedVersion) {
        throw "MetroList Windows version mismatch: expected $ExpectedVersion, got $ActualVersion."
    }
} finally {
    Remove-Item $VersionOutput -Force -ErrorAction SilentlyContinue
}

Write-Output 'WINDOWS_PACKAGE_VERIFY_OK'
Write-Output ('APP_VERSION=' + $ActualVersion)
Write-Output ('PACKAGE_ROOT=' + $PackageRoot)
Write-Output ('MPV_SHA256=' + (Get-FileHash $Mpv -Algorithm SHA256).Hash.ToLowerInvariant())
