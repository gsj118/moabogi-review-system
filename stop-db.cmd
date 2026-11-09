@echo off
"%USERPROFILE%\dev\review-system\mysql-8.4.11-winx64\bin\mysqladmin.exe" --defaults-extra-file="%USERPROFILE%\dev\review-system\config\mysql-admin.cnf" shutdown
