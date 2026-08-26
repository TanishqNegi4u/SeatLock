@REM Maven Wrapper bootstrap script for Windows
@REM Downloads maven-wrapper.jar if not present, then runs Maven via the wrapper
@echo off

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot
set "MAVEN_WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar"
set "MAVEN_WRAPPER_PROPERTIES=%~dp0.mvn\wrapper\maven-wrapper.properties"

if not exist "%MAVEN_WRAPPER_JAR%" (
    echo Downloading Maven Wrapper...
    mkdir "%~dp0.mvn\wrapper" 2>nul
    powershell -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar' -OutFile '%MAVEN_WRAPPER_JAR%'"
)

"%JAVA_HOME%\bin\java" %MAVEN_OPTS% ^
  -jar "%MAVEN_WRAPPER_JAR" %*
