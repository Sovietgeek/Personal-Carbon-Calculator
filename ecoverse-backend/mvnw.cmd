@REM Maven Wrapper script for Windows
@echo off
set MAVEN_PROJECTBASEDIR=%~dp0
set MAVEN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6\bin\mvn.cmd
if exist "%MAVEN_CMD%" goto execMvn
echo Downloading Maven...
powershell -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip' -OutFile '%TEMP%\maven.zip'"
powershell -Command "Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists' -Force"
del "%TEMP%\maven.zip"
:execMvn
"%MAVEN_CMD%" %*
