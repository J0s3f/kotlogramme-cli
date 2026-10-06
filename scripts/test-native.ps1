#requires -Version 7.0
param(
    [string]$Executable = (Join-Path (Split-Path -Parent $PSScriptRoot) ('build/native/nativeCompile/kotlogramme' + $(if ($IsWindows) { '.exe' } else { '' })))
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Executable -PathType Leaf)) {
    throw "Native executable is missing: $Executable. Run nativeCompile first."
}
$awtLibraries = Get-ChildItem -LiteralPath (Split-Path -Parent $Executable) -File -Filter '*awt*'
if ($awtLibraries) {
    throw "The image must not need the JDK AWT libraries: $($awtLibraries.Name -join ', ')"
}
$testDirectory = Join-Path ([IO.Path]::GetTempPath()) ('kotlogramme-native-test-' + [guid]::NewGuid())
[IO.Directory]::CreateDirectory($testDirectory) | Out-Null
$isolatedExecutable = Join-Path $testDirectory ([IO.Path]::GetFileName($Executable))
Copy-Item -LiteralPath $Executable -Destination $isolatedExecutable
$nativeDirectory = Split-Path -Parent $Executable
Get-ChildItem -LiteralPath $nativeDirectory -File |
    Where-Object { $_.Extension -in @('.dll', '.so', '.dylib') } |
    ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $testDirectory }
function Invoke-NativeCheck([string[]]$Arguments, [string]$Expected, [string]$InputText = '', [int]$ExpectedExitCode = 0) {
    $start = [Diagnostics.ProcessStartInfo]::new($isolatedExecutable)
    $start.WorkingDirectory = $testDirectory
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.RedirectStandardInput = $true
    $start.StandardOutputEncoding = [Text.Encoding]::UTF8
    $start.StandardErrorEncoding = [Text.Encoding]::UTF8
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    foreach ($name in @('JAVA_HOME', 'GRAALVM_HOME', 'JDK_HOME', 'CLASSPATH', 'TG_API_ID', 'TG_API_HASH')) {
        $start.Environment.Remove($name) | Out-Null
    }
    $start.Environment['PATH'] = if ($IsWindows) { Join-Path $env:SystemRoot 'System32' } else { '' }
    $process = [Diagnostics.Process]::Start($start)
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $process.StandardInput.Write($InputText)
    $process.StandardInput.Close()
    if (-not $process.WaitForExit(30000)) {
        $process.Kill()
        throw "Native check timed out: $Arguments"
    }
    $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
    if ($process.ExitCode -ne $ExpectedExitCode -or $output -notmatch $Expected) {
        throw "Native check failed ($($process.ExitCode)): $Arguments`n$output"
    }
    Write-Host "PASS: $Arguments"
    $process.Dispose()
}
try {
    $configDirectory = Join-Path $testDirectory 'config-ü'
    Invoke-NativeCheck @('--help') 'Usage:'
    Invoke-NativeCheck @('--not-a-real-option') 'no such option' -ExpectedExitCode 1
    Invoke-NativeCheck @('--version') 'kotlogramme.*\d+\.\d+'
    Invoke-NativeCheck @('--config-dir', $configDirectory, 'config', 'set', '--api-id', '12345', '--api-hash', 'offline-smoke-test') 'saved|updated|Saved|Updated'
    Invoke-NativeCheck @('--config-dir', $configDirectory, 'config') '12345'
    # Doctor needs no credentials: it loads the native library, the Telegram schema and the terminal
    # libraries, and skips the checks that would talk to Telegram. A failed check exits non-zero.
    $bareDirectory = Join-Path $testDirectory 'config-bare'
    Invoke-NativeCheck @('--config-dir', $bareDirectory, 'doctor') '(?s)Native library[^
]*loaded.*Telegram schema[^
]*decoded a sample update'
    Invoke-NativeCheck @('--config-dir', $configDirectory, 'shell') 'list conversations' "help`nexit`n"
    Write-Host 'Native smoke checks passed with the portable native files and no Java environment or Java on PATH.'
} finally {
    Remove-Item -LiteralPath $testDirectory -Recurse -Force
}