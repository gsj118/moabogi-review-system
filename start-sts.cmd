@echo off
setlocal
set "JAVA_HOME=%USERPROFILE%\dev\review-system\jdk-15.0.2"
set "PATH=%USERPROFILE%\dev\review-system\jdk-15.0.2\bin;%USERPROFILE%\dev\review-system\apache-maven-3.9.9\bin;%PATH%"
set "CATALINA_HOME=%USERPROFILE%\dev\review-system\apache-tomcat-9.0.123"
set "CATALINA_BASE=%USERPROFILE%\dev\review-system\tomcat-instance"
set "CATALINA_OPTS=-Dfile.encoding=UTF-8"
start "" "%USERPROFILE%\dev\review-system\sts-bundle\sts-3.9.18.RELEASE\STS.exe" -data "%USERPROFILE%\dev\review-system\sts-bundle\workspace" -vm "%USERPROFILE%\dev\review-system\jdk-15.0.2\bin\javaw.exe"
