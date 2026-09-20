#include <jni.h>
#include "Dx12Renderer.h"
#include <windows.h>
#include <memory>
#include <vector>
#include <iostream>

static std::unique_ptr<Dx12Renderer> g_renderer;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yourmod_Dx12Bridge_nativeInit(
    JNIEnv* env,
    jclass clazz,
    jlong hwnd,
    jint width,
    jint height) {
    try {
        std::cout << "[DX12 C++] nativeInit called with HWND=" << hwnd << ", " << width << "x" << height << std::endl;
        g_renderer = std::make_unique<Dx12Renderer>((HWND)hwnd, width, height);
        bool result = g_renderer->Initialize();
        std::cout << "[DX12 C++] nativeInit result: " << (result ? "true" : "false") << std::endl;
        return result ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        std::cerr << "[DX12 C++] nativeInit exception: " << e.what() << std::endl;
        return JNI_FALSE;
    } catch (...) {
        std::cerr << "[DX12 C++] nativeInit unknown exception" << std::endl;
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_yourmod_Dx12Bridge_nativeResize(
    JNIEnv* env,
    jclass clazz,
    jint width,
    jint height) {
    if (g_renderer) {
        g_renderer->Resize(width, height);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_yourmod_Dx12Bridge_nativeUpdateMesh(
    JNIEnv* env,
    jclass clazz,
    jobject vertexBuffer,
    jint vertexCount,
    jobject indexBuffer,
    jint indexCount) {
    if (!g_renderer || !vertexBuffer || !indexBuffer) {
        std::cerr << "[DX12 C++] updateMesh: invalid parameters" << std::endl;
        return;
    }

    auto* vertices = (Vertex*)env->GetDirectBufferAddress(vertexBuffer);
    auto* indices = (uint32_t*)env->GetDirectBufferAddress(indexBuffer);

    if (!vertices || !indices) {
        std::cerr << "[DX12 C++] updateMesh: failed to get buffer addresses" << std::endl;
        return;
    }

    std::cout << "[DX12 C++] updateMesh: " << vertexCount << " vertices, " << indexCount << " indices" << std::endl;

    std::vector<Vertex> vertexVec(vertices, vertices + vertexCount);
    std::vector<uint32_t> indexVec(indices, indices + indexCount);

    g_renderer->UpdateMesh(vertexVec, indexVec);
}

extern "C" JNIEXPORT void JNICALL
Java_com_yourmod_Dx12Bridge_nativeRender(
    JNIEnv* env,
    jclass clazz) {
    if (g_renderer) {
        g_renderer->Render();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_yourmod_Dx12Bridge_nativeDestroy(
    JNIEnv* env,
    jclass clazz) {
    g_renderer.reset();
}
