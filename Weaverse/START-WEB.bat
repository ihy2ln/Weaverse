@echo off
setlocal
cd /d "%~dp0"

REM Starts the desktop host (serves the web UI) then opens the browser.
call "%~dp0START-DESKTOP.bat"

timeout /t 2 >nul
start "" "http://127.0.0.1:8787/"
