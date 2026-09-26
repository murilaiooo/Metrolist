$ErrorActionPreference = 'Stop'

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$Resources = Join-Path $ProjectRoot 'runtime-dev\package-resources-current'
$CommonResources = Join-Path $Resources 'common'
$AuthOutput = Join-Path $CommonResources 'auth-helper'
$MpvOutput = Join-Path $CommonResources 'mpv'
$MpvSource = Join-Path $ProjectRoot 'runtime-dev\mpv\20260924\files\libmpv-2.dll'

New-Item -ItemType Directory -Force $AuthOutput | Out-Null
New-Item -ItemType Directory -Force $MpvOutput | Out-Null

if (-not (Test-Path $MpvSource)) {
    throw 'Pinned libmpv runtime is missing.'
}

$MpvHash = (Get-FileHash $MpvSource -Algorithm SHA256).Hash.ToLowerInvariant()
$ExpectedMpvHash = 'd2d081547efea8a1530f4ba0bf58a0d72069b25c40d8b2a7071cea5aa82381c6'

if ($MpvHash -ne $ExpectedMpvHash) {
    throw 'libmpv DLL hash mismatch.'
}

Copy-Item -Force $MpvSource (Join-Path $MpvOutput 'libmpv-2.dll')

$AuthProject = Join-Path $ProjectRoot 'desktop-auth-helper\Metrolist.AuthHelper.csproj'
$PublishArgs = @(
    'publish',
    $AuthProject,
    '-c', 'Release',
    '-r', 'win-x64',
    '--self-contained', 'true',
    '-p:PublishSingleFile=false',
    '-p:DebugType=None',
    '-p:DebugSymbols=false',
    '-o', $AuthOutput
)

& dotnet @PublishArgs

if ($LASTEXITCODE -ne 0) {
    throw 'Auth helper publish failed.'
}

$HelperExe = Join-Path $AuthOutput 'Metrolist.AuthHelper.exe'
if (-not (Test-Path $HelperExe)) {
    throw 'Published auth helper executable is missing.'
}

& $HelperExe --self-test
if ($LASTEXITCODE -ne 0) {
    throw 'Published auth helper self-test failed.'
}

Write-Output ('PACKAGE_RESOURCES=' + $Resources)
Write-Output ('AUTH_HELPER_BYTES=' + (Get-Item $HelperExe).Length)
Write-Output ('MPV_BYTES=' + (Get-Item (Join-Path $MpvOutput 'libmpv-2.dll')).Length)
