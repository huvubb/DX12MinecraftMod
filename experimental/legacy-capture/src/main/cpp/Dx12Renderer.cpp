#include "Dx12Renderer.h"
#include <d3dcompiler.h>
#include <DirectXMath.h>
#include <iostream>
#include <vector>
#include <windows.h>

#pragma comment(lib, "d3d12.lib")
#pragma comment(lib, "dxgi.lib")
#pragma comment(lib, "d3dcompiler.lib")

// ============================================================
// CD3DX12_VIEWPORT
// ============================================================
struct CD3DX12_VIEWPORT : public D3D12_VIEWPORT {
    CD3DX12_VIEWPORT() = default;
    CD3DX12_VIEWPORT(FLOAT topLeftX, FLOAT topLeftY, FLOAT width, FLOAT height,
                     FLOAT minDepth = 0.0f, FLOAT maxDepth = 1.0f) {
        TopLeftX = topLeftX; TopLeftY = topLeftY; Width = width; Height = height;
        MinDepth = minDepth; MaxDepth = maxDepth;
    }
};

// ============================================================
// CD3DX12_RECT
// ============================================================
struct CD3DX12_RECT : public RECT {
    CD3DX12_RECT() = default;
    CD3DX12_RECT(LONG left, LONG top, LONG right, LONG bottom) {
        left = left; top = top; right = right; bottom = bottom;
    }
};

// ============================================================
// CD3DX12_ROOT_SIGNATURE_DESC
// ============================================================
struct CD3DX12_ROOT_SIGNATURE_DESC {
    D3D12_ROOT_SIGNATURE_DESC Desc;
    CD3DX12_ROOT_SIGNATURE_DESC() : Desc() {}
    CD3DX12_ROOT_SIGNATURE_DESC(UINT numParameters, const D3D12_ROOT_PARAMETER* _pParameters,
                                UINT numStaticSamplers,
                                const D3D12_STATIC_SAMPLER_DESC* _pStaticSamplers,
                                D3D12_ROOT_SIGNATURE_FLAGS flags) {
        Init(numParameters, _pParameters, numStaticSamplers, _pStaticSamplers, flags);
    }
    void Init(UINT numParameters, const D3D12_ROOT_PARAMETER* _pParameters,
              UINT numStaticSamplers,
              const D3D12_STATIC_SAMPLER_DESC* _pStaticSamplers,
              D3D12_ROOT_SIGNATURE_FLAGS flags) {
        Desc.NumParameters = numParameters;
        Desc.pParameters = _pParameters;
        Desc.NumStaticSamplers = numStaticSamplers;
        Desc.pStaticSamplers = _pStaticSamplers;
        Desc.Flags = flags;
    }
    operator const D3D12_ROOT_SIGNATURE_DESC&() const { return Desc; }
};

// ============================================================
// 构造函数 / 析构函数
// ============================================================
Dx12Renderer::Dx12Renderer(HWND hwnd, int width, int height)
    : m_hwnd(hwnd), m_width(width), m_height(height), m_frameIndex(0), m_fenceValue(0) {
    OutputDebugStringA("[DX12] Constructor\n");
}

Dx12Renderer::~Dx12Renderer() {
    Flush();
}

// ============================================================
// Initialize
// ============================================================
bool Dx12Renderer::Initialize() {
    OutputDebugStringA("[DX12] Initialize\n");
    HRESULT hr = D3D12CreateDevice(nullptr, D3D_FEATURE_LEVEL_11_0, IID_PPV_ARGS(&m_device));
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to create device\n"); return false; }
    
    D3D12_COMMAND_QUEUE_DESC queueDesc = {};
    queueDesc.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
    hr = m_device->CreateCommandQueue(&queueDesc, IID_PPV_ARGS(&m_commandQueue));
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to create command queue\n"); return false; }
    
    ComPtr<IDXGIFactory4> factory;
    hr = CreateDXGIFactory1(IID_PPV_ARGS(&factory));
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to create factory\n"); return false; }
    
    DXGI_SWAP_CHAIN_DESC1 swapChainDesc = {};
    swapChainDesc.Width = m_width;
    swapChainDesc.Height = m_height;
    swapChainDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    swapChainDesc.Stereo = FALSE;
    swapChainDesc.SampleDesc.Count = 1;
    swapChainDesc.SampleDesc.Quality = 0;
    swapChainDesc.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    swapChainDesc.BufferCount = 2;
    swapChainDesc.Scaling = DXGI_SCALING_STRETCH;
    swapChainDesc.SwapEffect = DXGI_SWAP_EFFECT_FLIP_DISCARD;
    swapChainDesc.AlphaMode = DXGI_ALPHA_MODE_UNSPECIFIED;
    swapChainDesc.Flags = 0;
    
    ComPtr<IDXGISwapChain1> swapChain1;
    hr = factory->CreateSwapChainForHwnd(
        m_commandQueue.Get(),
        m_hwnd,
        &swapChainDesc,
        nullptr,
        nullptr,
        &swapChain1
    );
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to create swap chain\n"); return false; }
    
    hr = swapChain1.As(&m_swapChain);
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to get swap chain 3\n"); return false; }
    
    m_frameIndex = m_swapChain->GetCurrentBackBufferIndex();
    
    D3D12_DESCRIPTOR_HEAP_DESC rtvHeapDesc = {};
    rtvHeapDesc.NumDescriptors = 2;
    rtvHeapDesc.Type = D3D12_DESCRIPTOR_HEAP_TYPE_RTV;
    hr = m_device->CreateDescriptorHeap(&rtvHeapDesc, IID_PPV_ARGS(&m_rtvHeap));
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to create RTV heap\n"); return false; }
    m_rtvDescriptorSize = m_device->GetDescriptorHandleIncrementSize(D3D12_DESCRIPTOR_HEAP_TYPE_RTV);
    
    D3D12_CPU_DESCRIPTOR_HANDLE rtvHandle = m_rtvHeap->GetCPUDescriptorHandleForHeapStart();
    for (UINT i = 0; i < 2; i++) {
        hr = m_swapChain->GetBuffer(i, IID_PPV_ARGS(&m_renderTargets[i]));
        if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to get back buffer\n"); return false; }
        m_device->CreateRenderTargetView(m_renderTargets[i].Get(), nullptr, rtvHandle);
        rtvHandle.ptr += m_rtvDescriptorSize;
    }
    
    hr = m_device->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&m_commandAllocator));
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to create command allocator\n"); return false; }
    
    hr = m_device->CreateCommandList(0, D3D12_COMMAND_LIST_TYPE_DIRECT, m_commandAllocator.Get(),
                                     nullptr, IID_PPV_ARGS(&m_commandList));
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to create command list\n"); return false; }
    m_commandList->Close();
    
    m_fenceEvent = CreateEvent(nullptr, FALSE, FALSE, nullptr);
    if (!m_fenceEvent) { OutputDebugStringA("[DX12] Failed to create fence event\n"); return false; }
    hr = m_device->CreateFence(0, D3D12_FENCE_FLAG_NONE, IID_PPV_ARGS(&m_fence));
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to create fence\n"); return false; }
    
    // ============================================================
    // 根签名
    // ============================================================
    CD3DX12_ROOT_SIGNATURE_DESC rootDesc;
    rootDesc.Init(0, nullptr, 0, nullptr,
                  D3D12_ROOT_SIGNATURE_FLAG_ALLOW_INPUT_ASSEMBLER_INPUT_LAYOUT);
    ComPtr<ID3DBlob> sig, err;
    hr = D3D12SerializeRootSignature(&rootDesc.Desc, D3D_ROOT_SIGNATURE_VERSION_1, &sig, &err);
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to serialize root signature\n"); return false; }
    hr = m_device->CreateRootSignature(0, sig->GetBufferPointer(), sig->GetBufferSize(),
                                       IID_PPV_ARGS(&m_rootSignature));
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to create root signature\n"); return false; }
    
    // ============================================================
    // 着色器
    // ============================================================
    const char* vs = "struct VSInput { float3 pos : POSITION; float4 color : COLOR; };"
                     "struct VSOutput { float4 pos : SV_POSITION; float4 color : COLOR; };"
                     "VSOutput main(VSInput input) { VSOutput output; "
                     "output.pos = float4(input.pos, 1.0f); output.color = input.color; return output; }";
    ComPtr<ID3DBlob> vsBlob, psBlob, errBlob;
    hr = D3DCompile(vs, strlen(vs), "vs", nullptr, nullptr, "main", "vs_5_0", 0, 0, &vsBlob, &errBlob);
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to compile vertex shader\n"); return false; }
    
    const char* ps = "struct PSInput { float4 pos : SV_POSITION; float4 color : COLOR; };"
                     "float4 main(PSInput input) : SV_TARGET { return input.color; }";
    hr = D3DCompile(ps, strlen(ps), "ps", nullptr, nullptr, "main", "ps_5_0", 0, 0, &psBlob, &errBlob);
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to compile pixel shader\n"); return false; }
    
    // ============================================================
    // 输入布局 & PSO
    // ============================================================
    D3D12_INPUT_ELEMENT_DESC inputLayout[] = {
        { "POSITION", 0, DXGI_FORMAT_R32G32B32_FLOAT, 0, 0, D3D12_INPUT_CLASSIFICATION_PER_VERTEX_DATA, 0 },
        { "COLOR", 0, DXGI_FORMAT_R32G32B32A32_FLOAT, 0, 12, D3D12_INPUT_CLASSIFICATION_PER_VERTEX_DATA, 0 }
    };
    
    D3D12_GRAPHICS_PIPELINE_STATE_DESC psoDesc = {};
    psoDesc.InputLayout = { inputLayout, 2 };
    psoDesc.pRootSignature = m_rootSignature.Get();
    psoDesc.VS = { vsBlob->GetBufferPointer(), vsBlob->GetBufferSize() };
    psoDesc.PS = { psBlob->GetBufferPointer(), psBlob->GetBufferSize() };
    psoDesc.RasterizerState.FillMode = D3D12_FILL_MODE_SOLID;
    psoDesc.RasterizerState.CullMode = D3D12_CULL_MODE_NONE;
    psoDesc.BlendState.RenderTarget[0].RenderTargetWriteMask = D3D12_COLOR_WRITE_ENABLE_ALL;
    psoDesc.DepthStencilState.DepthEnable = FALSE;
    psoDesc.SampleMask = UINT_MAX;
    psoDesc.PrimitiveTopologyType = D3D12_PRIMITIVE_TOPOLOGY_TYPE_TRIANGLE;
    psoDesc.NumRenderTargets = 1;
    psoDesc.RTVFormats[0] = DXGI_FORMAT_R8G8B8A8_UNORM;
    psoDesc.SampleDesc.Count = 1;
    hr = m_device->CreateGraphicsPipelineState(&psoDesc, IID_PPV_ARGS(&m_pipelineState));
    if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to create pipeline state\n"); return false; }
    
    // ============================================================
    // 创建测试三角形
    // ============================================================
    Vertex verts[] = {
        { { -0.5f, -0.5f, 0.0f }, { 1.0f, 0.0f, 0.0f, 1.0f }, { 0.0f, 0.0f } },
        { {  0.5f, -0.5f, 0.0f }, { 0.0f, 1.0f, 0.0f, 1.0f }, { 0.0f, 0.0f } },
        { {  0.0f,  0.5f, 0.0f }, { 0.0f, 0.0f, 1.0f, 1.0f }, { 0.0f, 0.0f } }
    };
    uint32_t idx[] = { 0, 1, 2 };
    std::vector<Vertex> v(verts, verts + 3);
    std::vector<uint32_t> i(idx, idx + 3);
    CreateMeshBuffers(v, i);
    m_indexCount = 3;
    OutputDebugStringA("[DX12] Triangle created\n");
    
    OutputDebugStringA("[DX12] Initialize success\n");
    return true;
}

// ============================================================
// UpdateMesh
// ============================================================
void Dx12Renderer::UpdateMesh(const std::vector<Vertex>& vertices, const std::vector<uint32_t>& indices) {
    CreateMeshBuffers(vertices, indices);
    m_indexCount = (UINT)indices.size();
}

// ============================================================
// CreateMeshBuffers
// ============================================================
void Dx12Renderer::CreateMeshBuffers(const std::vector<Vertex>& vertices, const std::vector<uint32_t>& indices) {
    HRESULT hr;
    UINT vbSize = (UINT)vertices.size() * sizeof(Vertex);
    UINT ibSize = (UINT)indices.size() * sizeof(uint32_t);
    m_vertexBuffer.Reset(); m_indexBuffer.Reset();
    
    if (vbSize > 0) {
        D3D12_HEAP_PROPERTIES hp = {};
        hp.Type = D3D12_HEAP_TYPE_UPLOAD;
        D3D12_RESOURCE_DESC rd = {};
        rd.Dimension = D3D12_RESOURCE_DIMENSION_BUFFER;
        rd.Width = vbSize; rd.Height = 1; rd.DepthOrArraySize = 1;
        rd.MipLevels = 1; rd.SampleDesc.Count = 1; rd.Layout = D3D12_TEXTURE_LAYOUT_ROW_MAJOR;
        hr = m_device->CreateCommittedResource(&hp, D3D12_HEAP_FLAG_NONE, &rd,
                                               D3D12_RESOURCE_STATE_GENERIC_READ, nullptr,
                                               IID_PPV_ARGS(&m_vertexBuffer));
        if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to create vertex buffer\n"); return; }
        void* p; m_vertexBuffer->Map(0, nullptr, &p);
        memcpy(p, vertices.data(), vbSize);
        m_vertexBuffer->Unmap(0, nullptr);
        m_vertexBufferView.BufferLocation = m_vertexBuffer->GetGPUVirtualAddress();
        m_vertexBufferView.StrideInBytes = sizeof(Vertex);
        m_vertexBufferView.SizeInBytes = vbSize;
    }
    if (ibSize > 0) {
        D3D12_HEAP_PROPERTIES hp = {}; hp.Type = D3D12_HEAP_TYPE_UPLOAD;
        D3D12_RESOURCE_DESC rd = {};
        rd.Dimension = D3D12_RESOURCE_DIMENSION_BUFFER;
        rd.Width = ibSize; rd.Height = 1; rd.DepthOrArraySize = 1;
        rd.MipLevels = 1; rd.SampleDesc.Count = 1; rd.Layout = D3D12_TEXTURE_LAYOUT_ROW_MAJOR;
        hr = m_device->CreateCommittedResource(&hp, D3D12_HEAP_FLAG_NONE, &rd,
                                               D3D12_RESOURCE_STATE_GENERIC_READ, nullptr,
                                               IID_PPV_ARGS(&m_indexBuffer));
        if (FAILED(hr)) { OutputDebugStringA("[DX12] Failed to create index buffer\n"); return; }
        void* p; m_indexBuffer->Map(0, nullptr, &p);
        memcpy(p, indices.data(), ibSize);
        m_indexBuffer->Unmap(0, nullptr);
        m_indexBufferView.BufferLocation = m_indexBuffer->GetGPUVirtualAddress();
        m_indexBufferView.Format = DXGI_FORMAT_R32_UINT;
        m_indexBufferView.SizeInBytes = ibSize;
    }
}

// ============================================================
// Render
// ============================================================
void Dx12Renderer::Render() {
    static int frameCount = 0;
    frameCount++;
    
    m_commandAllocator->Reset();
    m_commandList->Reset(m_commandAllocator.Get(), m_pipelineState.Get());
    
    auto backBuffer = m_renderTargets[m_frameIndex].Get();
    
    D3D12_RESOURCE_BARRIER barrier1 = {};
    barrier1.Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
    barrier1.Flags = D3D12_RESOURCE_BARRIER_FLAG_NONE;
    barrier1.Transition.pResource = backBuffer;
    barrier1.Transition.StateBefore = D3D12_RESOURCE_STATE_PRESENT;
    barrier1.Transition.StateAfter = D3D12_RESOURCE_STATE_RENDER_TARGET;
    barrier1.Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
    m_commandList->ResourceBarrier(1, &barrier1);
    
    CD3DX12_VIEWPORT viewport(0.0f, 0.0f, (float)m_width, (float)m_height);
    CD3DX12_RECT scissorRect(0, 0, (LONG)m_width, (LONG)m_height);
    m_commandList->RSSetViewports(1, &viewport);
    m_commandList->RSSetScissorRects(1, &scissorRect);
    
    D3D12_CPU_DESCRIPTOR_HANDLE rtvHandle = m_rtvHeap->GetCPUDescriptorHandleForHeapStart();
    rtvHandle.ptr += m_frameIndex * m_rtvDescriptorSize;
    m_commandList->OMSetRenderTargets(1, &rtvHandle, FALSE, nullptr);
    
    float clearColor[] = { 0.1f, 0.2f, 0.5f, 1.0f };
    m_commandList->ClearRenderTargetView(rtvHandle, clearColor, 0, nullptr);
    
    if (m_vertexBuffer && m_indexCount > 0) {
        m_commandList->SetGraphicsRootSignature(m_rootSignature.Get());
        m_commandList->SetPipelineState(m_pipelineState.Get());
        m_commandList->IASetPrimitiveTopology(D3D_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
        m_commandList->IASetVertexBuffers(0, 1, &m_vertexBufferView);
        m_commandList->IASetIndexBuffer(&m_indexBufferView);
        m_commandList->DrawIndexedInstanced(m_indexCount, 1, 0, 0, 0);
    }
    
    D3D12_RESOURCE_BARRIER barrier2 = {};
    barrier2.Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
    barrier2.Flags = D3D12_RESOURCE_BARRIER_FLAG_NONE;
    barrier2.Transition.pResource = backBuffer;
    barrier2.Transition.StateBefore = D3D12_RESOURCE_STATE_RENDER_TARGET;
    barrier2.Transition.StateAfter = D3D12_RESOURCE_STATE_PRESENT;
    barrier2.Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
    m_commandList->ResourceBarrier(1, &barrier2);
    
    m_commandList->Close();
    ID3D12CommandList* ppCommandLists[] = { m_commandList.Get() };
    m_commandQueue->ExecuteCommandLists(_countof(ppCommandLists), ppCommandLists);
    
    m_swapChain->Present(1, 0);
    m_frameIndex = m_swapChain->GetCurrentBackBufferIndex();
    WaitForPreviousFrame();
}

// ============================================================
// Resize
// ============================================================
void Dx12Renderer::Resize(int width, int height) {
    if (m_width == width && m_height == height) return;
    WaitForPreviousFrame();
    m_width = width; m_height = height;
    for (int i = 0; i < 2; i++) m_renderTargets[i].Reset();
    m_swapChain->ResizeBuffers(2, width, height, DXGI_FORMAT_R8G8B8A8_UNORM, 0);
    m_frameIndex = m_swapChain->GetCurrentBackBufferIndex();
    D3D12_CPU_DESCRIPTOR_HANDLE rtvHandle = m_rtvHeap->GetCPUDescriptorHandleForHeapStart();
    for (UINT i = 0; i < 2; i++) {
        m_swapChain->GetBuffer(i, IID_PPV_ARGS(&m_renderTargets[i]));
        m_device->CreateRenderTargetView(m_renderTargets[i].Get(), nullptr, rtvHandle);
        rtvHandle.ptr += m_rtvDescriptorSize;
    }
}

// ============================================================
// WaitForPreviousFrame / Flush
// ============================================================
void Dx12Renderer::WaitForPreviousFrame() {
    const UINT64 fence = m_fenceValue;
    m_commandQueue->Signal(m_fence.Get(), fence);
    m_fenceValue++;
    
    if (m_fence->GetCompletedValue() < fence) {
        m_fence->SetEventOnCompletion(fence, m_fenceEvent);
        WaitForSingleObject(m_fenceEvent, INFINITE);
    }
}

void Dx12Renderer::Flush() {
    WaitForPreviousFrame();
}