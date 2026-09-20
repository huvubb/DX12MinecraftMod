cmake_minimum_required(VERSION 3.15)
project(dx12renderer)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 查找JNI
find_package(JNI REQUIRED)

# 添加DirectX
set(DirectX_INCLUDE_DIR "C:/Program Files (x86)/Microsoft DirectX SDK (June 2010)/Include")
set(DirectX_LIBRARY_DIR "C:/Program Files (x86)/Microsoft DirectX SDK (June 2010)/Lib/x64")

# 创建共享库
add_library(dx12renderer SHARED
    Dx12Renderer.cpp
    dx12_jni.cpp
)

# 包含目录
target_include_directories(dx12renderer PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${DirectX_INCLUDE_DIR}
)

# 链接库
target_link_libraries(dx12renderer
    ${JNI_LIBRARIES}
    d3d12
    dxgi
    d3dcompiler
)

# Windows特定设置
if(WIN32)
    set_target_properties(dx12renderer PROPERTIES
        PREFIX ""
        SUFFIX ".dll"
    )
    target_compile_definitions(dx12renderer PRIVATE _WINDLL)
endif()

# 输出到build/native目录
set_target_properties(dx12renderer PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/../../build/native"
)