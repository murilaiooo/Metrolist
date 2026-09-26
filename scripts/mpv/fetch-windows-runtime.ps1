param(
    [string] $Destination = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')) 'runtime-dev\mpv\20260924')
)

$ErrorActionPreference = 'Stop'

$Release = '20260924'
$Asset = 'mpv-dev-x86_64-20260924-git-2a4eb8067c.7z'
$ExpectedSha256 = '50caf10ddb6644da4c205ce67dfe37e32fc3715b33c82e933d6e1962ff61eac9'
$Url = "https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/$Release/$Asset"

New-Item -ItemType Directory -Force $Destination | Out-Null

$Archive = Join-Path $Destination $Asset
if (-not (Test-Path $Archive)) {
    & curl.exe -fL $Url -o $Archive
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to download pinned libmpv runtime"
    }
}

$ActualSha256 = (Get-FileHash $Archive -Algorithm SHA256).Hash.ToLowerInvariant()
if ($ActualSha256 -ne $ExpectedSha256) {
    throw "Pinned libmpv runtime SHA-256 mismatch"
}

$Extracted = Join-Path $Destination 'files'
$Dll = Get-ChildItem $Extracted -Recurse -Filter 'libmpv-2.dll' -ErrorAction SilentlyContinue |
    Select-Object -First 1

if ($null -eq $Dll) {
    New-Item -ItemType Directory -Force $Extracted | Out-Null
    & 7z x -y "-o$Extracted" $Archive | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to extract pinned libmpv runtime"
    }

    $Dll = Get-ChildItem $Extracted -Recurse -Filter 'libmpv-2.dll' |
        Select-Object -First 1
}

if ($null -eq $Dll) {
    throw "libmpv-2.dll was not found in the pinned runtime"
}

Write-Output "MPV_DLL=$($Dll.FullName)"
Write-Output "MPV_RUNTIME_SHA256=$ActualSha256"
