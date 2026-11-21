<#
.SYNOPSIS
    Starts NOTTA-POS on this Windows machine.

.DESCRIPTION
    WHAT: Checks that everything the app needs is in place, says which address
          to open from a phone, and starts the app. Close the window, or press
          Ctrl+C in it, to stop the app.

    WHY:  "java -jar notta-pos.jar" is all that starting the app really is. When
          something is missing (Java, the settings file, the database), Java
          answers with a page of error text. This script checks the usual
          causes first and says what to do about each in plain words.

    The easiest way to run it is to double-click start-notta-pos.cmd, which sits
    beside this file. To run it by hand, in PowerShell, in this folder:

        powershell -ExecutionPolicy Bypass -File .\start-notta-pos.ps1
        powershell -ExecutionPolicy Bypass -File .\start-notta-pos.ps1 -Port 8090

    The full setup, step by step: test-host.md (in this folder).

.PARAMETER Port
    The port the app answers on. 8080 unless something else on this machine
    already uses it (some media servers do).

.NOTES
    Written for Windows PowerShell 5.1, the version that comes with Windows 10.
    This file is plain ASCII on purpose: PowerShell 5.1 misreads other characters
    in a script that has no byte-order mark.
#>
param(
    [int]$Port = 8080
)

# Stop at the first thing that goes wrong instead of carrying on with half a setup
$ErrorActionPreference = 'Stop'

# Work from the folder this script is in, wherever it was started from. The app looks
# for application-local.properties in the folder it is started from.
Set-Location -Path $PSScriptRoot

# Say what is wrong, wait for a key (so a double-clicked window does not vanish), and stop
function Stop-WithMessage([string]$Message) {
    Write-Host ""
    Write-Host "Could not start NOTTA-POS:" -ForegroundColor Red
    Write-Host "  $Message"
    Write-Host ""
    Read-Host "Press Enter to close" | Out-Null
    exit 1
}

# True if something on this machine is listening on the given port.
# (It tries to connect, and gives up after a second and a half.)
function Test-PortInUse([int]$PortNumber) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $attempt = $client.ConnectAsync('127.0.0.1', $PortNumber)
        return ($attempt.Wait(1500) -and $client.Connected)
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

Write-Host ""
Write-Host "NOTTA-POS" -ForegroundColor Green
if (Test-Path -Path 'VERSION.txt') {
    Get-Content -Path 'VERSION.txt' | Select-Object -Skip 1 | ForEach-Object { Write-Host "  $_" }
}
Write-Host ""

# ----------------------------------------------------------------------------
# 1. Java 21 or newer
# ----------------------------------------------------------------------------
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Stop-WithMessage "Java is not installed, or this window was opened before it was installed. Install Java 21 (test-host.md, step 1), then open a NEW window and try again."
}

# "java -version" prints something like:  openjdk version "21.0.5" 2024-10-15 LTS
# It prints to the error stream; "cmd /c ... 2>&1" joins that to the ordinary output
# so it can be read here.
$versionLine = (cmd /c "java -version 2>&1" | Select-Object -First 1)
$javaMajor = 0
if ("$versionLine" -match '"(\d+)') {
    $javaMajor = [int]$Matches[1]
}
if ($javaMajor -lt 21) {
    Stop-WithMessage "Java 21 or newer is needed, and this machine's Java says: $versionLine. Install Java 21 (test-host.md, step 1)."
}

# ----------------------------------------------------------------------------
# 2. The app itself and its settings file
# ----------------------------------------------------------------------------
if (-not (Test-Path -Path 'notta-pos.jar')) {
    Stop-WithMessage "notta-pos.jar is not in this folder ($PSScriptRoot). Copy the whole notta-pos folder, not only this script."
}

if (-not (Test-Path -Path 'application-local.properties')) {
    Stop-WithMessage "application-local.properties is missing. Copy application-local.properties.example to application-local.properties (same folder) and put the database password in it (test-host.md, step 4)."
}

if (Select-String -Path 'application-local.properties' -Pattern 'put-your-database-password-here' -SimpleMatch -Quiet) {
    Stop-WithMessage "application-local.properties still holds the placeholder text. Open it and replace put-your-database-password-here with the database password (test-host.md, step 4)."
}

# ----------------------------------------------------------------------------
# 3. The database, and a free port for the app
# ----------------------------------------------------------------------------
if (-not (Test-PortInUse 5432)) {
    Stop-WithMessage "PostgreSQL is not answering on port 5432. Start it: press Win+R, type services.msc, find the service whose name starts with postgresql, and press Start (test-host.md, step 2)."
}

if (Test-PortInUse $Port) {
    Stop-WithMessage "Something is already using port $Port. If NOTTA-POS is already running in another window, use that one. If it is another program, start on a different port:  start-notta-pos.cmd -Port 8090"
}

# ----------------------------------------------------------------------------
# 4. Where to find it
# ----------------------------------------------------------------------------
# This machine's addresses on the network, leaving out the two kinds that are no use
# to another device: 127.x (this machine talking to itself) and 169.254.x (an adapter
# that was given no address).
$addresses = @()
try {
    $addresses = @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
        Select-Object -ExpandProperty IPAddress)
} catch {
    # Not being able to list addresses is no reason not to start
}

Write-Host "Starting. It takes about fifteen seconds. When the log below says 'Started NotaPosApplication', open:"
Write-Host "  On this machine:           http://localhost:$Port" -ForegroundColor Cyan
foreach ($address in $addresses) {
    Write-Host "  From a phone or tablet:    http://${address}:$Port" -ForegroundColor Cyan
}
if ($addresses.Count -eq 0) {
    Write-Host "  (Could not work out this machine's network address. Run ipconfig and look for 'IPv4 Address'.)"
}
if ($addresses.Count -gt 1) {
    Write-Host "  (More than one address: use the one that starts like your phone's own Wi-Fi address, usually 192.168.)"
}

# A reminder if the firewall rule from the setup guide is not there. Without it this
# machine can open the app and nothing else can.
try {
    if (-not (Get-NetFirewallRule -DisplayName 'NOTTA-POS' -ErrorAction SilentlyContinue)) {
        Write-Host ""
        Write-Host "Note: there is no Windows Firewall rule named NOTTA-POS yet, so other devices will not get through." -ForegroundColor Yellow
        Write-Host "      Add it once (test-host.md, step 7)."
    }
} catch {
    # Could not read the firewall rules: say nothing
}

Write-Host ""
Write-Host "To stop the app: press Ctrl+C in this window, or close it."
Write-Host "Everything below is also written to logs\notta-pos.log"
Write-Host ""

# ----------------------------------------------------------------------------
# 5. Start
# ----------------------------------------------------------------------------
New-Item -ItemType Directory -Force -Path 'logs' | Out-Null

# The two settings after the jar's name are handed to the app:
#   --server.port          the port to answer on
#   --logging.file.name    also write the log to a file, so there is something to read
#                          after the window is closed
& java -jar notta-pos.jar "--server.port=$Port" "--logging.file.name=logs/notta-pos.log"
$exitCode = $LASTEXITCODE

# The app only comes back here when it has stopped
if ($exitCode -ne 0) {
    Write-Host ""
    Write-Host "NOTTA-POS stopped (exit code $exitCode)." -ForegroundColor Yellow
    Write-Host "If that was not you pressing Ctrl+C, the reason is in the lines above and in logs\notta-pos.log."
    Write-Host "The most common one on a first start: the database password in application-local.properties is wrong."
    Write-Host ""
    Read-Host "Press Enter to close" | Out-Null
}
exit $exitCode
