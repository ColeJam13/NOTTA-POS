<#
.SYNOPSIS
    Saves a copy of the NOTTA-POS database to a file.

.DESCRIPTION
    WHAT: Writes everything in the database (menu, tables, staff, every order
          and payment) to one file in the "backups" folder beside this script,
          named with the date and time: backups\notta_pos-20261121-183000.dump

    WHEN: Before every update of the app, without exception. An update can
          change the database's tables the first time the new version starts,
          and that cannot be undone except by putting a backup back. It is also
          worth running at the end of any day whose data you would miss.

    The easiest way to run it is to double-click backup-notta-pos.cmd. The app
    can stay running while it works.

    HOW TO PUT A BACKUP BACK is printed at the end, and is in test-host.md.

    It uses pg_dump, the backup tool that is installed with PostgreSQL, and
    reads the database password from application-local.properties so it does
    not have to be typed.

.NOTES
    Written for Windows PowerShell 5.1. Plain ASCII on purpose (see
    start-notta-pos.ps1).
#>
param(
    [string]$Database = 'notta_pos',
    [string]$User = 'nottauser',
    [string]$DbHost = 'localhost',
    [int]$DbPort = 5432
)

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

function Stop-WithMessage([string]$Message) {
    Write-Host ""
    Write-Host "The backup was NOT made:" -ForegroundColor Red
    Write-Host "  $Message"
    Write-Host ""
    Read-Host "Press Enter to close" | Out-Null
    exit 1
}

# ----------------------------------------------------------------------------
# 1. Find pg_dump
# ----------------------------------------------------------------------------
# The PostgreSQL installer does not always add its tools to the PATH (the list of
# folders Windows looks in for a program), so look where it installs them too.
$pgDump = $null
$onPath = Get-Command pg_dump -ErrorAction SilentlyContinue
if ($onPath) {
    $pgDump = $onPath.Source
} else {
    $found = Get-ChildItem -Path (Join-Path $env:ProgramFiles 'PostgreSQL\*\bin\pg_dump.exe') -ErrorAction SilentlyContinue |
        Sort-Object -Property FullName -Descending |
        Select-Object -First 1
    if ($found) {
        $pgDump = $found.FullName
    }
}
if (-not $pgDump) {
    Stop-WithMessage "pg_dump.exe was not found. It is installed with PostgreSQL, normally in C:\Program Files\PostgreSQL\<version>\bin."
}

# ----------------------------------------------------------------------------
# 2. The database password, from the app's own settings file
# ----------------------------------------------------------------------------
if (-not (Test-Path -Path 'application-local.properties')) {
    Stop-WithMessage "application-local.properties is not in this folder, so the database password is not known."
}
$password = $null
foreach ($line in Get-Content -Path 'application-local.properties') {
    if ($line -match '^\s*spring\.datasource\.password\s*=\s*(.*)$') {
        $password = $Matches[1].Trim()
    }
}
if (-not $password) {
    Stop-WithMessage "application-local.properties has no spring.datasource.password line."
}

# ----------------------------------------------------------------------------
# 3. Make the backup
# ----------------------------------------------------------------------------
New-Item -ItemType Directory -Force -Path 'backups' | Out-Null
$file = Join-Path 'backups' ("notta_pos-{0}.dump" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))

Write-Host ""
Write-Host "Backing up database '$Database' to $file ..."

# pg_dump reads the password from the PGPASSWORD setting. It is set for this one
# command and removed straight after, whatever happens ("finally").
#   -F c   the "custom" format: compressed, and the one pg_restore reads
#   -f     the file to write
$env:PGPASSWORD = $password
try {
    & $pgDump -h $DbHost -p $DbPort -U $User -d $Database -F c -f $file
    $exitCode = $LASTEXITCODE
} finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}

if ($exitCode -ne 0) {
    Stop-WithMessage "pg_dump stopped with an error (code $exitCode). Its own message is above. Is PostgreSQL running, and is the password in application-local.properties right?"
}
if (-not (Test-Path -Path $file) -or (Get-Item -Path $file).Length -eq 0) {
    Stop-WithMessage "pg_dump finished but $file is missing or empty."
}

$sizeKb = [math]::Round((Get-Item -Path $file).Length / 1KB)
Write-Host "Done: $file ($sizeKb KB)" -ForegroundColor Green
Write-Host ""
Write-Host "Keep a copy somewhere other than this machine now and then."
Write-Host ""
Write-Host "To put this backup back (it REPLACES what is in the database now):"
Write-Host "  1. Stop NOTTA-POS."
Write-Host "  2. In PowerShell, in this folder:"
Write-Host "       & `"$(Join-Path (Split-Path $pgDump) 'pg_restore.exe')`" -h $DbHost -p $DbPort -U $User -d $Database --clean --if-exists `"$file`""
Write-Host "     and type the database password when asked."
Write-Host "  3. Start NOTTA-POS again."
Write-Host ""
Read-Host "Press Enter to close" | Out-Null
