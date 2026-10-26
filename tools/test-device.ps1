param([string]$SdkRoot = $env:ANDROID_HOME, [string]$Serial = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $SdkRoot) { $SdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $SdkRoot) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$adbArguments = @()
if ($Serial) { $adbArguments += @('-s', $Serial) }
$sampleDirectory = Join-Path $projectRoot '.test-samples'
New-Item -ItemType Directory -Path $sampleDirectory -Force | Out-Null
$samples = @(
    @{ Name = 'dolby-p5'; Source = 'color_pattern_24_dvhe_05_1920x1080.mp4' },
    @{ Name = 'dolby-p81'; Source = 'color_pattern_24_dvhe_081_1920x1080.mp4' },
    @{ Name = 'dolby-p84'; Source = 'color_pattern_24_dvhe_084_1920x1080.mp4' }
)
& $adb @adbArguments shell mkdir -p /data/local/tmp/oneframe-samples
if ($LASTEXITCODE -ne 0) { throw 'Connect an unlocked Android device or start an emulator first.' }
foreach ($sample in $samples) {
    $destination = Join-Path $sampleDirectory ($sample.Name + '.mp4')
    if (-not (Test-Path -LiteralPath $destination)) {
        Invoke-WebRequest -Uri ('https://ott.dolby.com/OnDelKits/DolbyVision_OSS_Media_Kit/clear/' + $sample.Source) -OutFile $destination
    }
    & $adb @adbArguments push $destination ('/data/local/tmp/oneframe-samples/' + $sample.Name + '.mp4')
    if ($LASTEXITCODE -ne 0) { throw 'Could not transfer test sample.' }
}
if ($Serial) { $env:ANDROID_SERIAL = $Serial }
Push-Location -LiteralPath $projectRoot
try {
    & '.\gradlew.bat' :app:connectedDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Device verification failed. Inspect app/build/reports/androidTests/connected/debug/.' }
} finally { Pop-Location }
