#requires -Version 7.0
param(
    [string]$GraalVmHome = 'C:\Tools\graalvm-jdk-25.0.4+7.1'
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$nativeImage = Join-Path $GraalVmHome 'bin\native-image.cmd'
if (-not (Test-Path -LiteralPath $nativeImage -PathType Leaf)) {
    throw "GraalVM Native Image was not found at $nativeImage. Pass -GraalVmHome with a GraalVM JDK 25 installation."
}
$javaHomeBefore = $env:JAVA_HOME
$graalVmHomeBefore = $env:GRAALVM_HOME
try {
    $env:JAVA_HOME = $GraalVmHome
    $env:GRAALVM_HOME = $GraalVmHome
    & (Join-Path $repository 'gradlew.bat') -p $repository --no-daemon clean test nativeSingle
    if ($LASTEXITCODE -ne 0) { throw "Native build failed with exit code $LASTEXITCODE." }
    Write-Host "Portable executable directory: $(Join-Path $repository 'build\distributions')"
} finally {
    $env:JAVA_HOME = $javaHomeBefore
    $env:GRAALVM_HOME = $graalVmHomeBefore
}