cmake_minimum_required(VERSION 3.15)
project(dx12renderer)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 查找JNI
find_package(JNI REQUIRED)

# 创建共享库
add_library(dx12renderer SHARED
    Dx12Renderer.cpp
    dx12_jni.cpp
)

# 包含目录
target_include_directories(dx12renderer PRIVATE
    ${JNI_INCLUDE_DIRS}
)

# 链接库
target_link_libraries(dx12renderer
    ${JNI_LIBRARIES}
    d3d12
    dxgi
    d3dcompiler
)

if(MINGW)
    # Ship one DLL without requiring the compiler's runtime DLLs on the player's PATH.
    target_link_options(dx12renderer PRIVATE -static -static-libgcc -static-libstdc++)
endif()

# Windows特定设置
if(WIN32)
    set_target_properties(dx12renderer PROPERTIES
        PREFIX ""
        SUFFIX ".dll"
    )
    target_compile_definitions(dx12renderer PRIVATE _WINDLL)
endif()

# 输出到项目根目录下的 build/native
set_target_properties(dx12renderer PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/../../../build/native"
    RUNTIME_OUTPUT_DIRECTORY_RELEASE "${CMAKE_SOURCE_DIR}/../../../build/native"
)

include(CTest)
if(BUILD_TESTING)
    add_executable(capture_check ../../test/cpp/CaptureCheck.cpp)
    target_include_directories(capture_check PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
    # Keep assertions enabled even in Release builds.
    if(MSVC)
        target_compile_options(capture_check PRIVATE /UNDEBUG)
    else()
        target_compile_options(capture_check PRIVATE -UNDEBUG)
    endif()
    add_test(NAME capture_check COMMAND capture_check)
endif()
