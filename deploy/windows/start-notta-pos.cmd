@echo off
rem Starts NOTTA-POS. Double-click this file.
rem It runs start-notta-pos.ps1, which sits beside it, and tells Windows to allow that one
rem script to run (Windows refuses to run PowerShell scripts by default).
rem Anything typed after the name is passed on, for example:  start-notta-pos.cmd -Port 8090
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-notta-pos.ps1" %*
