@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if exist .venv\Scripts\python.exe goto ready
py -3 -m venv .venv
if errorlevel 1 goto fail
:ready
.venv\Scripts\python.exe -c "import minecraft_launcher_lib; import PIL; import PIL.ImageTk" >nul 2>&1
if not errorlevel 1 goto launch
.venv\Scripts\python.exe -m pip install -r requirements.txt
if errorlevel 1 goto fail
:launch
.venv\Scripts\python.exe launcher.py
if errorlevel 1 goto fail
exit /b 0
:fail
echo Установи Python 3.12 или 3.13 с python.org, проверь интернет и повтори.
pause
exit /b 1
