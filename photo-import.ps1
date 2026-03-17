param(
    [Parameter(Position=0)]
    [ValidateSet("install", "run", "build", "clean", "test")]
    [string]$Command = "run",
    [string[]]$Args = @()
)

$ErrorActionPreference = "Stop"

$AppName = "Petrie File Importer"
$JarName = "petrie-file-importer.jar"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppDir = Split-Path -Parent $ScriptDir
$LogFile = "$env:USERPROFILE\.petriefi\photo-import.log"

function Test-Java21 {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Write-Host "Java is not installed." -ForegroundColor Red
        Write-Host "Please install Java 21 from: https://adoptium.net/" -ForegroundColor Yellow
        exit 1
    }

    $javaVersion = (java -version 2>&1 | Select-Object -First 1).ToString()
    if ($javaVersion -notmatch "21") {
        Write-Host "Java 21 or higher is required." -ForegroundColor Red
        Write-Host "Current version: $javaVersion" -ForegroundColor Yellow
        Write-Host "Please upgrade from: https://adoptium.net/" -ForegroundColor Yellow
        exit 1
    }
}

function Build-App {
    if (-not (Test-Path "$AppDir\app\build\libs\$JarName")) {
        Write-Host "Building $AppName..." -ForegroundColor Cyan
        Push-Location $AppDir
        try {
            & .\gradlew.bat shadowJar --no-daemon
        } finally {
            Pop-Location
        }
    }
}

function Install-Path {
    $profilePath = $PROFILE
    if (-not (Test-Path $profilePath)) {
        $profilePath = "$env:USERPROFILE\Documents\PowerShell\Microsoft.PowerShell_profile.ps1"
    }

    if (-not (Test-Path $profilePath)) {
        $profilePath = "$env:USERPROFILE\PowerShell\Microsoft.PowerShell_profile.ps1"
    }

    if (Test-Path $profilePath) {
        if (-not (Select-String -Path $profilePath -Pattern "photo-import" -Quiet)) {
            Add-Content -Path $profilePath -Value "`n`$env:PATH += `";$ScriptDir`""
            Write-Host "Added to PATH in $profilePath" -ForegroundColor Green
        }
    }

    $env:PATH += ";$ScriptDir"
    Write-Host "Added to current session PATH" -ForegroundColor Cyan
    Write-Host "Restart PowerShell to persist PATH changes" -ForegroundColor Yellow
}

function Run-App {
    Test-Java21
    Build-App

    $JarPath = "$AppDir\app\build\libs\$JarName"

    if ($Args -contains "--cli") {
        java -jar $JarPath @Args 2>&1 | Tee-Object -FilePath $LogFile
    } else {
        java -jar $JarPath 2>&1 | Tee-Object -FilePath $LogFile
    }
}

switch ($Command) {
    "install" { Install-Path }
    "run" { Run-App }
    "build" {
        Push-Location $AppDir
        try { & .\gradlew.bat shadowJar --no-daemon }
        finally { Pop-Location }
    }
    "clean" {
        Push-Location $AppDir
        try { & .\gradlew.bat clean --no-daemon }
        finally { Pop-Location }
    }
    "test" {
        Push-Location $AppDir
        try { & .\gradlew.bat test --no-daemon }
        finally { Pop-Location }
    }
}
