# DX12MinecraftMod

[![Build](https://github.com/huvubb/DX12MinecraftMod/actions/workflows/build.yml/badge.svg)](https://github.com/huvubb/DX12MinecraftMod/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Minecraft 1.19.2 / Fabric / Windows x64 的实验性 DirectX 12 渲染后端模组。**

通过 [Mesa D3D12](https://docs.mesa3d.org/drivers/d3d12.html)，将游戏原有 OpenGL 调用转换为 Direct3D 12 GPU 绘制。默认启用，驱动内置于 JAR，启动时自动解压、校验并加载。游戏仍保留 OpenGL API 接口。

当前是 **alpha 实验版**：已经在 Intel UHD Graphics 上进入世界验证，并通过指定版本的 Complementary / Iris 光影短测试；长期稳定性、其他显卡和整合包尚未覆盖，也没有证明性能提升。支持范围仅为 **Minecraft 1.19.2**。

[English](README.en.md) · [下载实验版](https://github.com/huvubb/DX12MinecraftMod/releases) · [反馈问题](https://github.com/huvubb/DX12MinecraftMod/issues/new/choose) · [参与开发](CONTRIBUTING.md)

## 安装与确认

1. 使用 Windows x64、64 位 Java、支持 DirectX 12 的显卡和驱动。本地原版验证使用 Java 21，Iris 检查使用 Java 17，Fabric Loader 0.14.21。
2. 从 Releases 下载普通模组 JAR，放进游戏实例的 `mods` 目录。升级时移走旧版 dx12mod JAR，只保留一份；`-sources.jar` 是源码包。
3. 正常启动，无需参数或独立 DLL。进入世界后按 F3，应看到 `D3D12 (你的显卡)` 和 `Render backend: DirectX 12 (Mesa D3D12)`。

只有核验了实际 D3D12 设备，F3 才会显示已启用。不同模组提前加载系统 OpenGL 驱动、驱动损坏或平台不支持时，会给出启动错误。Windows 版本和显卡驱动也可能影响后端可用性。

紧急排查时可临时使用 JVM 参数 `-Ddx12.disable=true`；正常安装不需要。驱动释放到游戏目录的 `.dx12mod/`，不会修改系统驱动或 Java 安装。

![世界中的真实 D3D12 渲染器与 F3 状态](docs/images/dx12-world.png)

## 验证与限制

本地检查通过了索引网格、着色器、贴图、深度遮挡、透明混合、帧缓冲、前缓冲读取、窗口呈现和缩放。世界检查验证了实际 F3 设备，并正常退出、保存各维度。

Java 25 / Loader 0.19.3 的游戏组合尚未验证。截图使用 4 区块渲染距离，帧率不能当作性能对比。其他显卡、渲染模组、资源包和长时间运行仍需要验证。

[Iris 光影与内存问题](docs/SHADERS.md) · [日志与排查](docs/TROUBLESHOOTING.md) · [架构与来源](docs/ARCHITECTURE.md) · [路线图](docs/ROADMAP.md)

## 构建与验证

安装 64 位 JDK 21（构建）与 JDK 17（世界检查），在项目根目录运行：

```powershell
.\gradlew.bat build
.\gradlew.bat backendCheck backendDisabledCheck
.\gradlew.bat runWorldSmoke
```

JAR 位于 `build/libs/`。GPU 检查只适用于有可用 D3D12 设备的 Windows x64。世界检查会在 `build/world-smoke/` 创建独立存档、截取 F3 并退出；首次运行需要下载 Minecraft 资源。CI 执行构建及禁用路径检查，不代表真实 GPU 检查通过。

当前构建无需 CMake、Visual Studio 或自建 JNI DLL。旧原型保留在 `experimental/legacy-capture/`，不参与发布。固定的 Mesa 26.2.4 二进制、哈希与许可证随源码提供，见 [第三方说明](src/main/resources/natives/windows-x86_64/mesa-26.2.4/THIRD_PARTY_NOTICES.txt)。

欢迎补充 Intel / AMD / NVIDIA 兼容记录、可重复的性能对比和复现报告。如果项目对你有帮助，欢迎 Star 或分享仓库链接。

项目代码使用 [MIT 许可证](LICENSE)，Mesa 及组件遵循各自许可证。本项目与 Mojang、Microsoft 或 Mesa 没有官方关联。
