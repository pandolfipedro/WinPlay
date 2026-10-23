param(
    [Parameter(Mandatory=$true)][string]$Jdk,
    [Parameter(Mandatory=$true)][string]$Kotlin,
    [switch]$Package
)
$ErrorActionPreference = 'Stop'
$Jdk = (Resolve-Path -LiteralPath $Jdk).Path
$Kotlin = (Resolve-Path -LiteralPath $Kotlin).Path
$root = $PSScriptRoot
$build = Join-Path $root 'build'
New-Item -ItemType Directory -Force "$build/app","$build/tests","$build/native" | Out-Null
$lib = (Get-ChildItem "$root/lib/*.jar").FullName -join ';'
$runtimeLib = (Get-ChildItem "$root/lib/*.jar" | Where-Object { $_.Name -notmatch 'junit|hamcrest' }).FullName
$sources = (Get-ChildItem "$root/src/upstream","$root/src/adapted","$root/src/main" -Recurse -Filter '*.kt').FullName
$compiler = "$Kotlin/lib/*"
$javaSources = @((Get-ChildItem "$root/src/native" -Filter '*.java').FullName)
& "$Jdk/bin/javac.exe" -encoding UTF-8 --release 17 -classpath $lib -d "$build/native" @javaSources
if ($LASTEXITCODE -ne 0) { throw 'Native codec boundary compilation failed' }
& "$Jdk/bin/java.exe" -cp $compiler org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -jvm-target 17 -classpath "$lib;$build/native" -include-runtime -d "$build/app/diplay-desktop.jar" @sources
if ($LASTEXITCODE -ne 0) { throw 'Kotlin compilation failed' }
& "$Jdk/bin/jar.exe" uf "$build/app/diplay-desktop.jar" -C "$build/native" .
if ($LASTEXITCODE -ne 0) { throw 'Native codec classes packaging failed' }
Copy-Item $runtimeLib "$build/app" -Force
$testSources = (Get-ChildItem "$root/src/test" -Filter '*.kt').FullName
& "$Jdk/bin/java.exe" -cp $compiler org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -jvm-target 17 -classpath "$build/app/diplay-desktop.jar;$lib" -Xfriend-paths="$build/app/diplay-desktop.jar" -d "$build/tests/tests.jar" @testSources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
$tests = foreach ($testSource in (Get-ChildItem "$root/src/test" -Filter '*.kt')) {
    $text = [IO.File]::ReadAllText($testSource.FullName)
    $packageName = [regex]::Match($text,'(?m)^package ([\w.]+)').Groups[1].Value
    $className = [regex]::Match($text,'(?m)^class (\w+Test)\b').Groups[1].Value
    if (!$packageName -or !$className) { throw "Cannot discover test class: $($testSource.Name)" }
    "$packageName.$className"
}
& "$Jdk/bin/java.exe" "-Dorg.bytedeco.javacpp.cachedir=$build/native-cache" -cp "$build/tests/tests.jar;$build/app/diplay-desktop.jar;$lib" org.junit.runner.JUnitCore @tests
if ($LASTEXITCODE -ne 0) { throw 'Unit tests failed' }
& "$Jdk/bin/java.exe" -cp "$build/app/*" diplay.desktop.Desktop --self-test
if ($LASTEXITCODE -ne 0) { throw 'Self test failed' }
if ($Package) {
    & "$Jdk/bin/java.exe" -cp "$build/app/*" diplay.desktop.Desktop --write-icon "$root/assets/WinPlay.ico"
    if ($LASTEXITCODE -ne 0) { throw 'Application icon generation failed' }
    $destination = Join-Path $build ('package-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    & "$Jdk/bin/jpackage.exe" --type app-image --name WinPlay --app-version 0.3.1 --vendor 'WinPlay' --icon "$root/assets/WinPlay.ico" --input "$build/app" --main-jar diplay-desktop.jar --main-class diplay.desktop.Desktop --dest $destination --add-modules java.desktop,java.logging,java.naming,java.management,jdk.crypto.ec,jdk.unsupported
    if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }
    Copy-Item "$root/licenses" "$destination/WinPlay/licenses" -Recurse
    Copy-Item "$root/README.txt","$root/UPSTREAM.txt","$root/TOOLCHAIN.json","$root/DEPENDENCIES.sha256","$root/UPSTREAM-SHA256.csv" "$destination/WinPlay"
    Write-Output "PACKAGE=$destination/WinPlay"
}
