@echo off
setlocal
set "JAVA_HOME=C:\Users\as742\dev\review-system\jdk-15.0.2"
set "PATH=C:\Users\as742\dev\review-system\jdk-15.0.2\bin;C:\Users\as742\dev\review-system\apache-maven-3.9.9\bin;%PATH%"
set "CATALINA_HOME=C:\Users\as742\dev\review-system\apache-tomcat-9.0.123"
set "CATALINA_BASE=C:\Users\as742\dev\review-system\tomcat-instance"
set "CATALINA_OPTS=-Dfile.encoding=UTF-8"
start "" "C:\Users\as742\dev\review-system\sts-bundle\sts-3.9.18.RELEASE\STS.exe" -data "C:\Users\as742\dev\review-system\sts-bundle\workspace" -vm "C:\Users\as742\dev\review-system\jdk-15.0.2\bin\javaw.exe"
