#requires -Version 7.0

function Get-NativePayloadFiles([string]$NativeDirectory) {
    Get-ChildItem -LiteralPath $NativeDirectory -File |
        Where-Object { $_.Name -in @('kotlogramme', 'kotlogramme.exe') -or $_.Name -match '\.(dll|dylib|so(?:\.[0-9]+)*)$' } |
        Sort-Object Name
}

function Get-WindowsRuntimeFiles {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
    $installation = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if (-not $installation) { throw 'MSVC redistributable runtime not found. Install the C++ build tools.' }
    $redistRoot = Join-Path $installation 'VC/Redist/MSVC'
    $latest = Get-ChildItem -LiteralPath $redistRoot -Directory |
        Where-Object Name -Match '^\d+\.\d+\.\d+$' |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
    $architectureDirectory = Join-Path $latest.FullName 'x64'
    $crtDirectory = Get-ChildItem -LiteralPath $architectureDirectory -Directory -Filter 'Microsoft.VC*.CRT' |
        Select-Object -First 1
    if (-not $crtDirectory) { throw "MSVC x64 runtime is missing under $architectureDirectory." }
    Get-ChildItem -LiteralPath $crtDirectory.FullName -File -Filter '*.dll'
}