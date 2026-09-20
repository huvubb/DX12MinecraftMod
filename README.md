# DX12MinecraftMod

一个适用于 Fabric MC 1.19+ 的 Minecraft OpenGL 转 DX12 Mod，目前还在开发中。

## ✨ 特性

- 将 Minecraft 的 OpenGL 渲染调用转换为 DirectX 12
- 基于 Fabric Loader / Fabric API
- 使用 JNI 桥接 Java 与 C++ DX12 渲染器

## 🔧 构建

### 环境要求

- JDK 17+
- Visual Studio 2022（含 C++ 桌面开发工作负载）
- Windows 10/11 SDK

### 编译

```bash
# 编译 Java 部分
./gradlew build

# 编译 C++ 部分（DX12 渲染器）
cd src/main/cpp
build_msvc.bat
