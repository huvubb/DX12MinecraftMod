package com.yourmod;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicLong;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;

public class Dx12Bridge {
    private static boolean loaded = false;
    private static boolean initialized = false;
    private static volatile boolean captureEnabled = false;
    private static boolean captureReported = false;
    private static String captureError = "";
    private static int frameCount = 0;
    private static final AtomicLong meshIds = new AtomicLong();
    private static final boolean CAPTURE_ENABLED = !Boolean.getBoolean("dx12.disable");

    static {
        if (CAPTURE_ENABLED) loadLibrary();
    }

    private static void loadLibrary() {
        if (loaded) return;
        System.out.println("[DX12] Attempting to load dx12renderer.dll...");
        try {
            System.loadLibrary("dx12renderer");
            verifyLibrary();
            loaded = true;
            captureEnabled = true;
            System.out.println("[DX12] Library loaded from java.library.path");
            return;
        } catch (UnsatisfiedLinkError e) {
            System.out.println("[DX12] Failed to load from java.library.path: " + e.getMessage());
        }
        try {
            String userDir = System.getProperty("user.dir");
            for (String path : new String[] { "dx12renderer.dll", "build/native/dx12renderer.dll",
                    "../build/native/dx12renderer.dll" }) {
                File dllFile = new File(userDir, path);
                if (dllFile.exists()) {
                    System.load(dllFile.getAbsolutePath());
                    verifyLibrary();
                    loaded = true;
                    captureEnabled = true;
                    System.out.println("[DX12] Library loaded from: " + dllFile.getAbsolutePath());
                    return;
                }
            }
        } catch (UnsatisfiedLinkError e) {
            System.out.println("[DX12] Failed to load from user.dir: " + e.getMessage());
        }
        System.err.println("[DX12] No compatible DLL found; capture disabled. Install the DLL matching this JAR.");
    }

    private static native int nativeApiVersion();

    private static void verifyLibrary() {
        if (nativeApiVersion() != 2) throw new UnsatisfiedLinkError("DX12 native API version mismatch");
    }

    public static void log(String msg) {
        System.out.println("[DX12 C++] " + msg);
    }

    public static native boolean nativeInit(long hwnd, int width, int height);
    public static native void nativeResize(int width, int height);
    public static native void nativeRender();
    public static native void nativeUpdateMesh(ByteBuffer vertexData, int vertexCount,
                                               ByteBuffer indexData, int indexCount);
    public static native void nativeDestroy();

    // Raw Minecraft bytes are copied synchronously; no DX12 device is needed to receive them.
    public static native void nativeCaptureMesh(long id, ByteBuffer vertices, int vertexCount, int stride,
            ByteBuffer indices, int indexCount, int indexBytes, int mode,
            boolean indexOnly, boolean sequential, String layout);
    public static native void nativeCaptureDraw(long id, float[] matricesAndOffset, int[] textures, String shader);
    public static native void nativeReleaseMesh(long id);
    public static native void nativeBeginFrame();
    // mesh count, vertex bytes, index bytes, current frame draws, successful uploads, skipped updates
    public static native long[] nativeCaptureStats();

    public static long nextMeshId() { return meshIds.incrementAndGet(); }
    public static boolean isLoaded() { return loaded; }

    public static void captureMesh(long id, BufferBuilder.RenderedBuffer buffer) {
        capture(() -> {
            var state = buffer.drawState();
            StringBuilder layout = new StringBuilder();
            int offset = 0;
            for (VertexFormatElement element : state.format().getElements()) {
                layout.append(offset).append(':').append(element.getUsage()).append(':')
                        .append(element.getIndex()).append(':').append(element.getType()).append(':')
                        .append(element.getCount()).append(';');
                offset += element.getByteSize();
            }
            nativeCaptureMesh(id, state.indexOnly() ? null : buffer.vertexBuffer().slice(),
                    state.vertexCount(), state.format().getVertexSize(),
                    state.sequentialIndex() ? null : buffer.indexBuffer().slice(),
                    state.indexCount(), state.indexType().bytes, state.mode().ordinal(),
                    state.indexOnly(), state.sequentialIndex(), layout.toString());
        });
    }

    public static void captureDraw(long id, ShaderInstance shader) {
        if (shader == null) return;
        capture(() -> {
            float[] state = new float[35];
            if (shader.MODEL_VIEW_MATRIX != null) {
                shader.MODEL_VIEW_MATRIX.getFloatBuffer().duplicate().get(0, state, 0, 16);
            } else {
                RenderSystem.getModelViewMatrix().store(FloatBuffer.wrap(state, 0, 16).slice());
            }
            if (shader.PROJECTION_MATRIX != null) {
                shader.PROJECTION_MATRIX.getFloatBuffer().duplicate().get(0, state, 16, 16);
            } else {
                RenderSystem.getProjectionMatrix().store(FloatBuffer.wrap(state, 16, 16).slice());
            }
            if (shader.CHUNK_OFFSET != null) {
                shader.CHUNK_OFFSET.getFloatBuffer().duplicate().get(0, state, 32, 3);
            }
            int[] textures = new int[12];
            for (int slot = 0; slot < textures.length; slot++) textures[slot] = RenderSystem.getShaderTexture(slot);
            nativeCaptureDraw(id, state, textures, shader.getName());
        });
    }

    private static void capture(Runnable action) {
        if (!captureEnabled) return;
        try { action.run(); } catch (Throwable error) { failCapture(error); }
    }

    private static void failCapture(Throwable error) {
        captureEnabled = false;
        captureError = error.getClass().getSimpleName();
        System.err.println("[DX12] Capture disabled; game rendering continues: " + error);
        try { nativeDestroy(); } catch (Throwable cleanupError) {
            System.err.println("[DX12] Capture cleanup failed: " + cleanupError);
        }
        initialized = false;
    }

    public static void releaseMesh(long id) { capture(() -> nativeReleaseMesh(id)); }
    public static void beginFrame() { capture(() -> nativeBeginFrame()); }

    public static void endFrame() {
        if (captureReported) return;
        capture(() -> {
            long[] stats = nativeCaptureStats();
            if (stats[3] > 0) {
                System.out.println("[DX12] Capture running: uploads=" + stats[4] + ", draws=" + stats[3]
                        + "; DX12 renderer initialized=" + initialized);
                captureReported = true;
            }
        });
    }

    public static String getStatusLine() {
        String renderer = " | DX12 绘制：" + (initialized ? "已初始化" : "未启动");
        if (!CAPTURE_ENABLED) return "DX12 接收：已禁用" + renderer;
        if (!loaded) return "DX12 接收：DLL 未加载" + renderer;
        if (!captureEnabled) return "DX12 接收：已停用 " + captureError + renderer;
        try {
            long[] stats = nativeCaptureStats();
            return "DX12 接收：运行中 | 网格 " + stats[0] + " | 本帧绘制 " + stats[3]
                    + " | 更新 " + stats[4] + " | 跳过 " + stats[5] + renderer;
        } catch (Throwable error) {
            failCapture(error);
            return "DX12 接收：已停用 " + captureError + renderer;
        }
    }

    public static boolean init(long hwnd, int width, int height) {
        if (!CAPTURE_ENABLED || !loaded) {
            System.err.println("[DX12] Library not loaded!");
            return false;
        }
        if (initialized) return true;
        try { initialized = nativeInit(hwnd, width, height); } catch (Throwable error) { failCapture(error); }
        if (initialized) {
            System.out.println("[DX12] Renderer initialized successfully!");
        } else {
            System.err.println("[DX12] Renderer initialization failed!");
        }
        return initialized;
    }

    public static void render() {
        frameCount++;
        if (frameCount % 60 == 0) {
            System.out.println("[DX12] render() called " + frameCount + " times");
        }
        if (initialized && loaded) capture(() -> nativeRender());
    }

    public static void updateMesh(ByteBuffer vertexData, int vertexCount,
                                  ByteBuffer indexData, int indexCount) {
        if (initialized && loaded) {
            System.out.println("[DX12] updateMesh: " + vertexCount + " vertices, " + indexCount + " indices");
            capture(() -> nativeUpdateMesh(vertexData, vertexCount, indexData, indexCount));
        }
    }

    public static void resize(int width, int height) {
        if (initialized && loaded) capture(() -> nativeResize(width, height));
    }

    public static void destroy() {
        if (loaded) {
            capture(() -> nativeDestroy());
            initialized = false;
            captureReported = false;
        }
    }

    public static boolean isInitialized() {
        return initialized && loaded;
    }
}
