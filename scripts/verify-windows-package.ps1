param(
    [string] $PackageRoot = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')) 'desktop-app\build\compose\binaries\main\app\MetroListWindows')
)

$ErrorActionPreference = 'Stop'

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

Write-Output 'WINDOWS_PACKAGE_VERIFY_OK'
Write-Output ('PACKAGE_ROOT=' + $PackageRoot)
Write-Output ('MPV_SHA256=' + (Get-FileHash $Mpv -Algorithm SHA256).Hash.ToLowerInvariant())
