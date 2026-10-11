# 实验版推广计划

对外定位：Windows x64 / Minecraft 1.19.2 的 Mesa D3D12 后端实验。传播材料包括 JAR、中英文安装说明、真实 F3 截图、路线图和反馈模板。当前不宣称性能提升、永不崩溃或全套自研渲染器。

## 推广顺序

1. GitHub 预发布提供 JAR、SHA-256 和限制说明，使用 Minecraft、Fabric、DirectX 12 相关主题帮助检索。
2. 准备 Minecraft 社区、MC 百科或 B 站的安装与 F3 演示，按平台规则投稿，邀请具体显卡型号的测试者。
3. 收集可复现反馈，公开兼容记录和修复情况后扩大推广。记录下载与有效反馈，避免用 Star 数代替稳定性证据。

## 中文公告草稿

DX12MinecraftMod 实验版现已开源：让 Minecraft 1.19.2（Fabric / Windows x64）的 GPU 绘制通过 Mesa D3D12 后端执行。驱动随 JAR 提供，默认开启，无需额外参数；F3 显示实际 D3D12 设备。

目前已在 Intel UHD Graphics 上验证进入世界和正常保存，正在招募 Intel / AMD / NVIDIA 用户提供兼容反馈。长期稳定性和性能提升尚未确认，游戏仍保留 OpenGL API 接口。

源码与下载：https://github.com/huvubb/DX12MinecraftMod

## English announcement draft

DX12MinecraftMod is open source: an experimental Mesa D3D12 backend for Minecraft 1.19.2 on Fabric and Windows x64. The driver is bundled and enabled automatically, with the actual adapter verified in F3.

World rendering and clean saving have been checked locally on Intel UHD Graphics. We welcome reproducible Intel / AMD / NVIDIA compatibility reports. Long-term stability and performance gains are not established; Minecraft retains its OpenGL API interface.

Source and downloads: https://github.com/huvubb/DX12MinecraftMod
