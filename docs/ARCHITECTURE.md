# 当前渲染路径

Minecraft OpenGL API → LWJGL / GLFW → Mesa WGL / OpenGL → Mesa D3D12 → Direct3D 12 / DXGI → GPU 与窗口。

`Dx12Bootstrap` 是 Fabric `preLaunch` 入口：检查 Windows x64，解压固定的 Mesa 驱动并验证 SHA-256，拒绝已经加载的不同 OpenGL 驱动，预加载 Mesa 并设置 LWJGL 库路径。GLFW 按库名加载时使用已经加载的同一模块。

`WindowMixin` 在上下文创建后核验实际设备字符串。只有驱动报告 D3D12 才启用 active 状态。`DebugScreenOverlayMixin` 将核验结果显示到 F3，显卡名称来自实际驱动。

区块、实体、界面和着色器继续调用原有接口，转换及 GPU 资源管理由 Mesa 完成。当前模组不再逐帧复制顶点或启用历史 JNI 捕获原型。

`SodiumDrawMixin` 是可选的 Sodium 0.4.4 兼容钩子。D3D12 核验通过后，将批量索引绘制拆成保留索引偏移与基础顶点的单次调用，规避 Iris 检查中出现的原生内存持续增长；不复制缓冲、不改变缓冲位置。没有 Sodium 时不加载该目标，DX12 禁用时使用原调用。这可能增加 CPU 提交开销，不能作为性能优化宣传。

Mesa 26.2.4 的 Windows x64 构建来自 [mmozeiko/build-mesa](https://github.com/mmozeiko/build-mesa/releases/tag/26.2.4)，DLL 未经修改。资源目录保留来源、压缩包和 DLL 哈希、上游源码链接及许可证。

缓存路径包含版本和哈希前缀；每次启动校验已有文件，损坏文件使用临时文件重新释放。失败会给出错误原因。`experimental/legacy-capture/` 保留旧 C++ / JNI 原型，不参与当前构建，不代表完整的自研 Minecraft DX12 渲染器。
