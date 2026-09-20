// Dx12Renderer.h
#pragma once

#include <windows.h>
#include <d3d12.h>
#include <dxgi1_4.h>
#include <wrl.h>
#include <DirectXMath.h>  // 添加这行！
#include <vector>
#include <memory>

using Microsoft::WRL::ComPtr;

// 简单顶点结构，匹配Minecraft的顶点格式（位置+颜色+UV）
struct Vertex {
    DirectX::XMFLOAT3 Position;   // 现在 DirectX 命名空间可用
    DirectX::XMFLOAT4 Color;
    DirectX::XMFLOAT2 TexCoord;
};

class Dx12Renderer {
public:
    Dx12Renderer(HWND hwnd, int width, int height);
    ~Dx12Renderer();

    bool Initialize();
    void Resize(int width, int height);
    void Render();
    void Flush();
    void UpdateMesh(const std::vector<Vertex>& vertices, const std::vector<uint32_t>& indices);

private:
    // --- 核心DX12对象 ---
    ComPtr<ID3D12Device> m_device;
    ComPtr<ID3D12CommandQueue> m_commandQueue;
    ComPtr<IDXGISwapChain3> m_swapChain;
    ComPtr<ID3D12Resource> m_renderTargets[2];
    ComPtr<ID3D12CommandAllocator> m_commandAllocator;
    ComPtr<ID3D12GraphicsCommandList> m_commandList;
    ComPtr<ID3D12PipelineState> m_pipelineState;
    ComPtr<ID3D12RootSignature> m_rootSignature;
    ComPtr<ID3D12Fence> m_fence;

    UINT m_rtvDescriptorSize;
    UINT m_frameIndex;
    HANDLE m_fenceEvent;
    UINT64 m_fenceValue;

    HWND m_hwnd;
    int m_width;
    int m_height;
    ComPtr<ID3D12DescriptorHeap> m_rtvHeap;

    ComPtr<ID3D12Resource> m_vertexBuffer;
    ComPtr<ID3D12Resource> m_indexBuffer;
    D3D12_VERTEX_BUFFER_VIEW m_vertexBufferView;
    D3D12_INDEX_BUFFER_VIEW m_indexBufferView;
    UINT m_indexCount;

    // --- 辅助函数 ---
    void CreateDeviceAndSwapChain();
    void CreateRenderTargets();
    void CreateCommandObjects();
    void CreateRootSignatureAndPipeline();
    void CreateMeshBuffers(const std::vector<Vertex>& vertices, const std::vector<uint32_t>& indices);
    void PopulateCommandList();
    void WaitForPreviousFrame();
};