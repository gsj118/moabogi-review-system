@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0.setup\start_mysql.ps1"
exit /b %ERRORLEVEL%
