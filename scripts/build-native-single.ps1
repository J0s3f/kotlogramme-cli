#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$NativeDirectory,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$Platform,
    [Parameter(Mandatory)][string]$Version,
    [string]$Wrappe
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'native-packaging.ps1')
$payloadFiles = @(Get-NativePayloadFiles $NativeDirectory)
$nativeName = if ($Platform.StartsWith('windows-')) { 'kotlogramme.exe' } else { 'kotlogramme' }
if (-not ($payloadFiles | Where-Object Name -EQ $nativeName)) {
    throw "Native executable is missing from $NativeDirectory. Run nativeCompile first."
}
$extension = if ($Platform.StartsWith('windows-')) { '.exe' } else { '' }
$outputPath = Join-Path $OutputDirectory "kotlogramme-$Version-$Platform$extension"
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$runner = switch ($Platform) {
    'windows-x86_64' { 'x86_64-pc-windows-gnu' }
    'macos-x86_64' { 'x86_64-apple-darwin' }
    'macos-aarch64' { 'aarch64-apple-darwin' }
    'linux-x86_64' { 'x86_64-unknown-linux-musl' }
    'linux-aarch64' { 'aarch64-unknown-linux-gnu' }
    default { throw "Unsupported portable executable platform: $Platform" }
}
if (-not $Wrappe) {
    if ($Platform -eq 'linux-aarch64') {
        throw 'Pass -Wrappe with a source-built Wrappe 1.0.6 that includes the Linux ARM64 runner.'
    }
    $toolDirectory = Join-Path $repository 'build/tools'
    [IO.Directory]::CreateDirectory($toolDirectory) | Out-Null
    $toolAsset = if ($IsWindows) { 'wrappe.exe' } elseif ($IsMacOS) { 'wrappe-macos' } else { 'wrappe-linux' }
    $expectedHash = switch ($toolAsset) {
        'wrappe.exe' { '3fdf78a356414c6e010ef1e02b027e1aa6a066641a0251972be812686ca4cb5e' }
        'wrappe-macos' { '42eae5d4535c2c2f79d2115d58918e81944ec9e65fd09d1c0103a7c6e9e4bd6e' }
        'wrappe-linux' { 'aa4f845f1e2cc91b3fb27b9bf4727463217a750866cc5f30f27b59b3dc88025e' }
    }
    $Wrappe = Join-Path $toolDirectory $toolAsset
    if (-not (Test-Path -LiteralPath $Wrappe)) {
        Invoke-WebRequest -Uri "https://github.com/Systemcluster/wrappe/releases/download/v1.0.6/$toolAsset" -OutFile $Wrappe
    }
    if ((Get-FileHash -LiteralPath $Wrappe -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedHash) {
        throw "Wrappe 1.0.6 checksum mismatch: $Wrappe"
    }
    if (-not $IsWindows) { & chmod +x $Wrappe }
}
$payloadDirectory = Join-Path $repository "build/portable/$Platform/payload"
$resolvedPayload = [IO.Path]::GetFullPath($payloadDirectory)
$buildRoot = [IO.Path]::GetFullPath((Join-Path $repository 'build/portable')) + [IO.Path]::DirectorySeparatorChar
if (-not $resolvedPayload.StartsWith($buildRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Portable payload directory is outside the generated build tree.'
}
if (Test-Path -LiteralPath $resolvedPayload) { Remove-Item -LiteralPath $resolvedPayload -Recurse -Force }
[IO.Directory]::CreateDirectory($resolvedPayload) | Out-Null
foreach ($file in $payloadFiles) { Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $payloadDirectory $file.Name) }
if ($Platform -eq 'windows-x86_64') {
    foreach ($file in Get-WindowsRuntimeFiles) {
        Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $payloadDirectory $file.Name)
    }
}
foreach ($name in @('LICENSE', 'NOTICE')) {
    Copy-Item -LiteralPath (Join-Path $repository $name) -Destination (Join-Path $payloadDirectory $name)
}
$licensePath = Join-Path $payloadDirectory 'WRAPPE-LICENSE.txt'
Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/Systemcluster/wrappe/v1.0.6/LICENSE' -OutFile $licensePath
$nativeName = if ($Platform.StartsWith('windows-')) { 'kotlogramme.exe' } else { 'kotlogramme' }
& $Wrappe --runner $runner --show-information none --console always --current-dir inherit --verification checksum `
    --unpack-directory "kotlogramme-$Platform" $payloadDirectory (Join-Path $payloadDirectory $nativeName) $outputPath
if ($LASTEXITCODE -ne 0) { throw 'Wrappe packaging failed.' }
& (Join-Path $PSScriptRoot 'test-native.ps1') -Executable $outputPath
Write-Host "Single native executable: $outputPath"