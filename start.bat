@echo off
title LAN Protector v3.2
echo ================================
echo    LAN PROTECTOR v3.2 - FIXED
echo ================================
echo.

echo Step 1: Checking Java...
java -version
if errorlevel 1 (
    echo ERROR: Java not found!
    pause
    exit
)
echo.

echo Step 2: Compiling fixed version...
javac LanProtectorV3_2.java
if errorlevel 1 (
    echo COMPILATION ERROR!
    echo Fix the Java file first.
    pause
    exit
)
echo Compilation successful!
echo.

echo Step 3: Starting protection...
echo ================================
echo   PROGRAM OUTPUT:
echo ================================
echo.
java LanProtectorV3_2

echo.
echo ================================
echo   PROGRAM FINISHED
echo ================================
pause
