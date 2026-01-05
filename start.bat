@echo off
title Minecraft Protector
cls
echo Minecraft LAN Protector v4.0
echo ============================
echo.
echo Features:
echo - Blocks fake worlds
echo - Scans suspicious ports
echo - Colorful alerts
echo.
echo Checking Java...
java -version >nul || (
    echo Java not found!
    pause
    exit
)

echo Compiling...
javac LanProtectorV4.java || (
    echo Compile error!
    pause
    exit
)

echo Starting protection...
echo.
java LanProtectorV4

echo.
echo Program finished.
pause