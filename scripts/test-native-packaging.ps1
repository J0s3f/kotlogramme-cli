#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('kotlogramme-package-test-' + [guid]::NewGuid())
[IO.Directory]::CreateDirectory($fixture) | Out-Null
try {
    $nativeDirectory = Join-Path $fixture 'native'
    [IO.Directory]::CreateDirectory($nativeDirectory) | Out-Null
    [IO.File]::WriteAllText((Join-Path $nativeDirectory 'kotlogramme'), 'native program')
    [IO.File]::WriteAllText((Join-Path $nativeDirectory 'LICENSE'), 'license')
    . (Join-Path $PSScriptRoot 'native-packaging.ps1')
    $single = @(Get-NativePayloadFiles $nativeDirectory)
    if ($single.Count -ne 1) { throw 'License files must not make a single native executable need an installer.' }
    [IO.File]::WriteAllText((Join-Path $nativeDirectory 'libawt.so'), 'support library')
    [IO.File]::WriteAllText((Join-Path $nativeDirectory 'libfreetype.so.6'), 'versioned support library')
    $payload = @(Get-NativePayloadFiles $nativeDirectory)
    if ($payload.Count -ne 3) { throw 'Every runtime support library must be included in the portable payload.' }
    Write-Host 'Native packaging tests passed: single-file selection and all runtime support libraries.'
} finally {
    Remove-Item -LiteralPath $fixture -Recurse -Force
}