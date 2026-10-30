param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$GradleExecutable,
    [switch]$CheckEnvironment
)
$ErrorActionPreference = 'Stop'
$repoDir = Split-Path $PSScriptRoot -Parent
if (-not $SdkRoot -or -not (Test-Path -LiteralPath (Join-Path $SdkRoot 'platforms\android-34\android.jar'))) {
    throw 'Pass -SdkRoot or set ANDROID_HOME to Android SDK with platform34 and build-tools34.0.0.'
}
if (-not $JdkHome -or -not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\javac.exe'))) {
    throw 'Pass -JdkHome or set JAVA_HOME (JDK21 is the tested version).'
}
if (-not (Test-Path -LiteralPath (Join-Path $SdkRoot 'build-tools\34.0.0\apksigner.bat'))) {
    throw 'Android build-tools34.0.0 is required.'
}
if (-not $GradleExecutable) {
    $taskCaches = @((Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.11.1-bin'))
    if ($env:GRADLE_USER_HOME) { $taskCaches += Join-Path $env:GRADLE_USER_HOME 'wrapper\dists\gradle-8.11.1-bin' }
    foreach ($taskCache in $taskCaches) {
        if (Test-Path -LiteralPath $taskCache) {
            $GradleExecutable = Get-ChildItem -LiteralPath $taskCache -Filter gradle.bat -Recurse | Select-Object -First 1 -ExpandProperty FullName
            if ($GradleExecutable) { break }
        }
    }
}
if (-not $GradleExecutable -or -not (Test-Path -LiteralPath $GradleExecutable)) { throw 'Pass a Gradle8.11.1 gradle.bat using -GradleExecutable.' }
$env:JAVA_HOME = $JdkHome
$env:ANDROID_HOME = $SdkRoot
$taskVersion = (& $GradleExecutable --version --console=plain) -join "`n"
if ($LASTEXITCODE -ne 0 -or $taskVersion -notmatch '(?m)^Gradle 8\.11\.1\s*$') { throw 'Use Gradle8.11.1.' }
if ($CheckEnvironment) { Write-Host 'SDK34, build-tools34.0.0, JDK and Gradle8.11.1 verified.'; return }
& $GradleExecutable -p (Join-Path $repoDir 'launcher') --no-daemon testReleaseUnitTest lintRelease assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build or verification failed.' }
Write-Host (Join-Path $repoDir 'launcher\app\build\outputs\apk\release\app-release.apk')
