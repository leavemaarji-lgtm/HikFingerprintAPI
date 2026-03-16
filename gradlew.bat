@echo off
set CLASSPATH=%~dp0gradle\wrapper\gradle-wrapper.jar
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
"%JAVA_EXE%" -Xmx64m -Xms64m -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
