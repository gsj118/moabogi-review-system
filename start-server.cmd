@echo off
setlocal
set "JAVA_HOME=%USERPROFILE%\dev\review-system\jdk-15.0.2"
set "PATH=%USERPROFILE%\dev\review-system\jdk-15.0.2\bin;%USERPROFILE%\dev\review-system\apache-maven-3.9.9\bin;%PATH%"
set "CATALINA_HOME=%USERPROFILE%\dev\review-system\apache-tomcat-9.0.123"
set "CATALINA_BASE=%USERPROFILE%\dev\review-system\tomcat-instance"
set "CATALINA_OPTS=-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Seoul"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0.setup\check_server_port.ps1"
if errorlevel 1 exit /b %ERRORLEVEL%
call "%~dp0start-db.cmd"
if errorlevel 1 exit /b %ERRORLEVEL%
call "%~dp0build.cmd"
if errorlevel 1 exit /b %ERRORLEVEL%
copy /y "%~dp0review-system\target\ROOT.war" "%CATALINA_BASE%\webapps\ROOT.war" >nul
if errorlevel 1 exit /b %ERRORLEVEL%
call "%CATALINA_HOME%\bin\catalina.bat" run
