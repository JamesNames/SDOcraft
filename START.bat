@echo off
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
echo Install Python 3.12 or 3.13 from python.org, check your Internet connection and try again.
pause
exit /b 1
