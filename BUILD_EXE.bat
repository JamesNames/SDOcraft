@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if exist .venv\Scripts\python.exe goto ready
py -3.12 -m venv .venv
if errorlevel 1 goto fail
:ready
.venv\Scripts\python.exe -m pip install -r requirements.txt pyinstaller==6.16.0
if errorlevel 1 goto fail
.venv\Scripts\python.exe -m PyInstaller --noconfirm --clean --onefile --windowed --name SDOcraft --icon branding/sdocraft.ico --add-data "branding;branding" --collect-all minecraft_launcher_lib launcher.py
if errorlevel 1 goto fail
echo Готово: dist\SDOcraft.exe
pause
exit /b 0
:fail
echo Ошибка сборки. Установи Python 3.12 и проверь сообщение выше.
pause
exit /b 1
