@echo off
REM ================================================================
REM  P2P LAN Chat - Web Edition - Compile and Run
REM ================================================================
REM  Make sure mysql-connector-j-9.7.0.jar is in the lib/ folder
REM  Then run this file from Command Prompt
REM ================================================================

set JAR=lib\mysql-connector-j-9.7.0.jar
set OUT=out

REM --- Check JAR ---
if not exist "%JAR%" (
    echo.
    echo  ERROR: %JAR% not found!
    echo  Put mysql-connector-j-9.7.0.jar in the lib/ folder.
    echo.
    pause
    exit /b 1
)

REM --- Create folders ---
if not exist "%OUT%" mkdir "%OUT%"
if not exist "received_files" mkdir "received_files"

REM --- Compile ---
echo  Compiling...
dir /s /b src\*.java > sources.txt
javac -cp "%JAR%" -d "%OUT%" @sources.txt
del sources.txt

if errorlevel 1 (
    echo  COMPILE FAILED.
    pause
    exit /b 1
)

echo  Compile OK!
echo.

REM --- Ask for username ---
set /p USERNAME=Enter your username: 

echo.
echo  Starting P2P LAN Chat...
echo  Opening browser at http://localhost:8080
echo.

java -cp "%OUT%;%JAR%" App %USERNAME%
pause
