# Iris 光影

DX12MinecraftMod 保留 Minecraft 的 OpenGL API，由 Mesa D3D12 执行 GPU 绘制，因此可以接入 Iris 的 GLSL 光影管线。光影加载器使用 Iris；本项目不重复实现它。

## 安装（Minecraft 1.19.2）

在同一个游戏实例的 `mods` 中放入以下普通 JAR，只保留每个模组的一份：

- DX12MinecraftMod 1.1.0-alpha.2。
- [Iris 1.6.11 + 1.19.2](https://modrinth.com/mod/iris/version/LgOJIpIS)。
- [Sodium 0.4.4 + 1.19.2](https://modrinth.com/mod/sodium/version/rAfhHfow)。
- [Fabric API 0.77.0 + 1.19.2](https://modrinth.com/mod/fabric-api/version/6g95K303)。

把光影 ZIP 原样放入实例的 `shaderpacks`，在 Iris 的光影菜单选择并应用。无需 JVM 参数，DX12 默认启用。不要同时安装 OptiFine / OptiFabric 或另一套光影加载器。

旧版 alpha.1 同时使用 Iris 与 Sodium 0.4.4 时，在本机 Intel UHD / Mesa 26.2.4 上出现过 Java 堆外内存持续增长，曾耗尽约 32GB 内存并在驱动中崩溃。降低 Java 最大堆不能修复这个问题。alpha.2 在实际 D3D12 上自动将 Sodium 的批量索引绘制拆成逐次绘制；无需改变 Sodium 缓冲设置或 JVM 参数。日志应出现 `Sodium draws submitted individually`。

## 实测记录

环境：Windows 11 / Intel UHD Graphics / Mesa 26.2.4 / Java 17 / Fabric Loader 0.14.21 / Iris 1.6.11 / Sodium 0.4.4。854×480，4 区块，Sodium 默认 ASYNC 分配和高级 staging buffer 开启。进程私有内存包含 Java 堆及原生分配，不等于 F3 的堆内存数字。

| 光影包 | 检查 | 进程峰值 |
| --- | --- | --- |
| Complementary Reimagined r5.9.3 | 渲染、关闭/开启/重载、保存退出通过 | 4.28GiB |
| BSL v10.1.8 | 发布版测试提前退出，未完成；退出前内存约 3.52GiB | 未完成 |
| Sildur’s Enhanced Default v1.20 Fancy | 同一逐次绘制方案的测试原型通过；最终 JAR 未复测 | 3.34GiB（原型） |

Sildur’s 与 BSL 在不安装 Sodium 时也通过了上述检查，峰值分别约 3.94GiB 和 3.7GiB，可作为排查替代组合。测试不是长期压力测试。截图帧率很低，不能当作流畅运行或性能提升的证据；没有逐项核验阴影、反射及每种光影选项。

Complementary 报告未知新版本生物群系、End 闪光相关 uniform 及未初始化变量警告；BSL 报告部分 OpenGL 扩展不支持警告。当前测试使用 Iris 实际状态和未发生编译失败回退作为通过条件，不代表包的所有效果都正确。

进入世界后检查 F3 的 `D3D12 (显卡名称)`、`Render backend: DirectX 12 (Mesa D3D12)` 和 Iris 的光影包状态。只有菜单中有包名不能证明实际光影管线工作；遇到编译失败应检查 Iris 日志与实际画面。

## 开发复现

将需要测试的光影 ZIP 放到 `build/world-smoke/shaderpacks/`。在 `build/world-smoke/config/iris.properties` 设置：

```properties
enableShaders=true
shaderPack=实际光影文件名.zip
```

执行 `./tools/Test-Iris.ps1 -ShaderPack '光影文件名.zip' -MaxPrivateGB 6`。测试验证 D3D12、Iris 实际启用状态、未发生光影失败回退、关闭/重新开启/重载、F3 截图、兼容绘制路径实际被调用和正常保存退出。脚本记录进程内存，超限或系统可用内存不足 8GiB 时停止独立测试实例。只作用于开发实例；Iris 和 Sodium 不打包进 DX12 模组。添加 `-NoSodium` 可验证不安装 Sodium 的组合。

首次运行应下载游戏资源。离线跳过资源下载的测试不覆盖音效完整性。开发环境使用 Loom 重映射第三方模组，需要单独补齐它们在发行 JAR 中内嵌的运行库；用户直接安装原始 Iris/Sodium JAR 无需这些开发配置。

本地短测试不代表长期稳定、其他显卡或每种光影选项兼容。需要计算着色器或 SSBO 的光影应按实际 OpenGL 扩展能力检查；不能仅凭 DirectX 12 显卡支持就保证它们可用。
