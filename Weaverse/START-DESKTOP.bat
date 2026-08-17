@echo off
setlocal
cd /d "%~dp0"

if exist "Weaverse.exe" (
  start "" "Weaverse.exe"
  exit /b 0
)

if exist "Weaverse.jar" (
  where java >nul 2>nul
  if errorlevel 1 (
    echo Java 17+ is required to run Weaverse.jar
    echo Install a JRE, or use the packaged Weaverse.exe from GitHub Releases.
    pause
    exit /b 1
  )
  start "Weaverse Desktop" java -jar "Weaverse.jar" --data="%~dp0data"
  exit /b 0
)

echo Could not find Weaverse.exe or Weaverse.jar in this folder.
echo Copy the desktop release into S:\AI\Novel\Weaverse and try again.
pause
exit /b 1
