# Deploy Python Backend to Jetson Nano
# Run this from PowerShell on your Windows PC

param(
    [Parameter(Mandatory=$true)]
    [string]$JetsonIP,
    
    [Parameter(Mandatory=$false)]
    [string]$Username = "jetson",
    
    [Parameter(Mandatory=$false)]
    [int]$Port = 22
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Deploying to Jetson Nano at $JetsonIP" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if Python folder exists
$pythonDir = Join-Path $PSScriptRoot ""
if (-not (Test-Path $pythonDir)) {
    Write-Host "ERROR: Python directory not found at: $pythonDir" -ForegroundColor Red
    exit 1
}

# Test connection
Write-Host "[1/4] Testing connection to Jetson..." -ForegroundColor Yellow
try {
    $result = Test-Connection -ComputerName $JetsonIP -Count 1 -Quiet
    if (-not $result) {
        Write-Host "ERROR: Cannot reach $JetsonIP" -ForegroundColor Red
        Write-Host "Make sure Jetson is powered on and connected to network" -ForegroundColor Red
        exit 1
    }
    Write-Host "Connection OK" -ForegroundColor Green
} catch {
    Write-Host "WARNING: Could not test connection, continuing anyway..." -ForegroundColor Yellow
}

# Check for SCP/SSH tools
Write-Host "[2/4] Checking for SSH tools..." -ForegroundColor Yellow
$scpPath = (Get-Command scp -ErrorAction SilentlyContinue).Source
$sshPath = (Get-Command ssh -ErrorAction SilentlyContinue).Source

if (-not $scpPath) {
    Write-Host "ERROR: SCP not found. Install OpenSSH client:" -ForegroundColor Red
    Write-Host "  Settings > Apps > Optional Features > Add OpenSSH Client" -ForegroundColor Yellow
    exit 1
}
Write-Host "SSH tools found" -ForegroundColor Green

# Copy Python files
Write-Host "[3/4] Copying Python files to Jetson..." -ForegroundColor Yellow
$targetPath = "${Username}@${JetsonIP}:~/ZealGame-Deploy"

# Create temp directory and copy files
$tempDir = Join-Path $env:TEMP "ZealGame-Deploy"
if (Test-Path $tempDir) {
    Remove-Item $tempDir -Recurse -Force
}
Copy-Item -Path $pythonDir -Destination $tempDir -Recurse

try {
    & scp -r "$tempDir" "$targetPath"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Failed to copy files. Check credentials and try again." -ForegroundColor Red
        exit 1
    }
    Write-Host "Files copied successfully" -ForegroundColor Green
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    Remove-Item $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}

# Run setup script on Jetson
Write-Host "[4/4] Running setup script on Jetson..." -ForegroundColor Yellow
Write-Host "You may be prompted for password again..." -ForegroundColor Yellow

$setupCommands = @"
cd ~/ZealGame-Deploy
chmod +x setup_jetson.sh
./setup_jetson.sh
"@

try {
    & ssh "${Username}@${JetsonIP}" $setupCommands
    if ($LASTEXITCODE -ne 0) {
        Write-Host "WARNING: Setup script encountered issues. Check output above." -ForegroundColor Yellow
    }
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Deployment Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Green
Write-Host "1. SSH to Jetson: ssh ${Username}@${JetsonIP}" -ForegroundColor White
Write-Host "2. Start server: sudo systemctl start zealgame-bridge" -ForegroundColor White
Write-Host "3. Check status: sudo systemctl status zealgame-bridge" -ForegroundColor White
Write-Host "4. View logs: sudo journalctl -u zealgame-bridge -f" -ForegroundColor White
Write-Host ""
Write-Host "Update your GameTwoScreen.java with:" -ForegroundColor Yellow
Write-Host "pythonClient = new PythonBridgeClient(`"$JetsonIP`", 9009);" -ForegroundColor Cyan
Write-Host ""

# Offer to update Java files
$updateJava = Read-Host "Update Java client IP automatically? (y/n)"
if ($updateJava -eq 'y' -or $updateJava -eq 'Y') {
    $javaFiles = @(
        "core\src\main\java\io\github\ZaneStyx\Screen\GameTwoScreen.java",
        "core\src\main\java\io\github\ZaneStyx\Screen\GameOneScreen.java"
    )
    
    foreach ($file in $javaFiles) {
        $fullPath = Join-Path (Split-Path $PSScriptRoot -Parent) $file
        if (Test-Path $fullPath) {
            Write-Host "Updating $file..." -ForegroundColor Yellow
            $content = Get-Content $fullPath -Raw
            $content = $content -replace 'new PythonBridgeClient\("127\.0\.0\.1"', "new PythonBridgeClient(`"$JetsonIP`""
            Set-Content $fullPath -Value $content -NoNewline
            Write-Host "Updated!" -ForegroundColor Green
        }
    }
    Write-Host ""
    Write-Host "Java files updated. Rebuild your project." -ForegroundColor Green
}
