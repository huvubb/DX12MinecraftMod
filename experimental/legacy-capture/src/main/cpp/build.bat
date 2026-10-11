@echo off
echo Compiling DX12 Renderer Engine...

set SCRIPT_DIR=%~dp0
set SCRIPT_DIR=%SCRIPT_DIR:~0,-1%

cd /d "%SCRIPT_DIR%"

REM ³¹µ×É¾³ý build Ä¿Â¼£¨°üÀ¨ËùÓÐ»º´æ£©
if exist build (
    echo Removing old build directory and cache...
    rmdir /s /q build
)

echo Creating fresh build directory...
mkdir build
cd build

echo Running CMake configuration with Ninja...
cmake .. -G "Ninja"
if errorlevel 1 (
    echo Ninja failed, trying Visual Studio...
    cd ..
    rmdir /s /q build
    mkdir build
    cd build
    
    cmake .. -G "Visual Studio 18 2026" -A x64
    if errorlevel 1 (
        echo VS 2026 failed, trying VS 2022...
        cd ..
        rmdir /s /q build
        mkdir build
        cd build
        
        cmake .. -G "Visual Studio 17 2022" -A x64
        if errorlevel 1 (
            echo All generators failed!
            pause
            exit /b 1
        )
    )
)

echo Starting compilation...
cmake --build . --config Release
if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo Compilation successful!

if not exist "..\..\build\native" mkdir "..\..\build\native"

if exist "Release\dx12renderer.dll" (
    copy "Release\dx12renderer.dll" "..\..\build\native\"
    echo DLL copied to build/native/
) else if exist "dx12renderer.dll" (
    copy "dx12renderer.dll" "..\..\build\native\"
    echo DLL copied to build/native/
) else (
    echo Warning: dx12renderer.dll not found
    dir /s *.dll
)

cd ..
echo.
echo ========================================
echo   Compilation Complete!
echo ========================================
pause