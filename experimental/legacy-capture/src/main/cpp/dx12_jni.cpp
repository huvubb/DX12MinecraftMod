#include <jni.h>
#include "Dx12Renderer.h"
#include <windows.h>
#include <memory>
#include <vector>
#include <iostream>
#include <mutex>
#include "RenderCapture.h"

static std::unique_ptr<Dx12Renderer> g_renderer;
static RenderCapture g_capture;
// ponytail: one lock for CPU capture; split by mesh only if capture contention is measured.
static std::mutex g_captureMutex;

extern "C" JNIEXPORT jint JNICALL
Java_com_yourmod_Dx12Bridge_nativeApiVersion(JNIEnv*, jclass) { return 2; }

template <typename F> static void captureCall(JNIEnv* env, F action) {
    try {
        std::lock_guard<std::mutex> lock(g_captureMutex);
        action();
    } catch (const std::invalid_argument& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), error.what());
    } catch (const std::exception& error) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), error.what());
    }
}

static std::vector<uint8_t> copyBuffer(JNIEnv* env, jobject buffer, int count, int stride) {
    if (count < 0 || stride <= 0) throw std::invalid_argument("Negative count or invalid stride");
    const int64_t size = int64_t(count) * stride;
    if (!size) return {};
    if (!buffer || env->GetDirectBufferCapacity(buffer) < size) {
        throw std::invalid_argument("Missing, non-direct or truncated buffer");
    }
    auto data = static_cast<const uint8_t*>(env->GetDirectBufferAddress(buffer));
    if (!data) throw std::invalid_argument("Buffer has no direct address");
    return {data, data + size};
}

static std::string copyString(JNIEnv* env, jstring value) {
    if (!value) throw std::invalid_argument("Missing capture metadata");
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) throw std::runtime_error("Cannot read capture metadata");
    std::string result;
    try { result = chars; } catch (...) { env->ReleaseStringUTFChars(value, chars); throw; }
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yourmod_Dx12Bridge_nativeCaptureMesh(JNIEnv* env, jclass, jlong id,
        jobject vertices, jint vertexCount, jint stride, jobject indices, jint indexCount,
        jint indexBytes, jint mode, jboolean indexOnly, jboolean sequential, jstring layout) {
    captureCall(env, [&] {
        CapturedMesh mesh;
        mesh.vertexCount = vertexCount; mesh.stride = stride;
        mesh.indexCount = indexCount; mesh.indexBytes = indexBytes;
        mesh.mode = mode; mesh.sequential = sequential;
        mesh.layout = copyString(env, layout);
        if (!indexOnly) mesh.vertices = std::make_shared<const std::vector<uint8_t>>(
                copyBuffer(env, vertices, vertexCount, stride));
        if (!sequential) mesh.indices = copyBuffer(env, indices, indexCount, indexBytes);
        g_capture.upload(id, std::move(mesh), indexOnly);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_yourmod_Dx12Bridge_nativeCaptureDraw(JNIEnv* env, jclass, jlong id,
        jfloatArray matrices, jintArray textures, jstring shader) {
    captureCall(env, [&] {
        if (!matrices || env->GetArrayLength(matrices) != 35 ||
                !textures || env->GetArrayLength(textures) != 12) {
            throw std::invalid_argument("Expected two 4x4 matrices, chunk offset and 12 texture slots");
        }
        CapturedDraw draw;
        draw.meshId = id;
        draw.shader = copyString(env, shader);
        env->GetFloatArrayRegion(matrices, 0, 35, draw.matricesAndOffset.data());
        if (!env->ExceptionCheck()) env->GetIntArrayRegion(textures, 0, 12, draw.textures.data());
        if (!env->ExceptionCheck()) g_capture.draw(std::move(draw));
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_yourmod_Dx12Bridge_nativeReleaseMesh(JNIEnv* env, jclass, jlong id) {
    captureCall(env, [&] { g_capture.release(id); });
}

extern "C" JNIEXPORT void JNICALL
Java_com_yourmod_Dx12Bridge_nativeBeginFrame(JNIEnv* env, jclass) {
    captureCall(env, [&] { g_capture.draws.clear(); });
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_yourmod_Dx12Bridge_nativeCaptureStats(JNIEnv* env, jclass) {
    jlongArray result = nullptr;
    captureCall(env, [&] {
        auto stats = g_capture.stats();
        jlong values[6] = {stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]};
        result = env->NewLongArray(6);
        if (result) env->SetLongArrayRegion(result, 0, 6, values);
    });
    return result;
}

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
    captureCall(env, [&] { g_capture = RenderCapture{}; });
}
