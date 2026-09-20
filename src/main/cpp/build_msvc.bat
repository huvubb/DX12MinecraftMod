@echo off
echo Compiling DX12 Renderer with MSVC (x64)...

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot

if not exist "..\..\build\native" mkdir "..\..\build\native"

echo Compiling 64-bit DLL...

REM 关键：直接指定目标平台为 x64
cl /EHsc /LD /MD /std:c++17 /D "_WIN64" ^
   /I"%JAVA_HOME%\include" ^
   /I"%JAVA_HOME%\include\win32" ^
   Dx12Renderer.cpp dx12_jni.cpp ^
   /link d3d12.lib dxgi.lib d3dcompiler.lib ^
   /MACHINE:x64 ^
   /OUT:dx12renderer.dll

if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo Compilation successful!
copy dx12renderer.dll ..\..\build\native\
pause