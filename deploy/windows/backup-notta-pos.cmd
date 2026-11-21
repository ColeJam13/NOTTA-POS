@echo off
rem Backs up the NOTTA-POS database. Double-click this file.
rem It runs backup-notta-pos.ps1, which sits beside it (see start-notta-pos.cmd for why
rem it is started this way).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0backup-notta-pos.ps1" %*
