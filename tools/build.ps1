param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $SdkRoot) { $SdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $SdkRoot) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path -LiteralPath (Join-Path $SdkRoot 'platforms\android-36'))) {
    throw 'Install Android SDK Platform 36, then pass -SdkRoot.'
}
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
$sdkProperty = $SdkRoot.Replace('\', '/').Replace(':', '\:')
[System.IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "sdk.dir=$sdkProperty`n")
Push-Location -LiteralPath $projectRoot
try {
    & '.\gradlew.bat' :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build or verification failed.' }
    $outputPath = Join-Path $projectRoot 'artifacts'
    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
    $metadata = Get-Content -LiteralPath 'app\build\outputs\apk\debug\output-metadata.json' -Raw | ConvertFrom-Json
    $apkName = 'OneFrame-' + $metadata.elements[0].versionName + '.apk'
    Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination (Join-Path $outputPath $apkName) -Force
    Write-Output (Join-Path $outputPath $apkName)
} finally { Pop-Location }
