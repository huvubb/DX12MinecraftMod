package com.yourmod;

import java.io.File;
import java.nio.ByteBuffer;

public class Dx12Bridge {
    private static boolean loaded = false;
    private static boolean initialized = false;
    private static int frameCount = 0;

    static {
        loadLibrary();
    }

    private static void loadLibrary() {
        if (loaded) return;
        System.out.println("[DX12] Attempting to load dx12renderer.dll...");
        try {
            System.loadLibrary("dx12renderer");
            loaded = true;
            System.out.println("[DX12] Library loaded from java.library.path");
            return;
        } catch (UnsatisfiedLinkError e) {
            System.out.println("[DX12] Failed to load from java.library.path: " + e.getMessage());
        }
        try {
            String userDir = System.getProperty("user.dir");
            File dllFile = new File(userDir, "dx12renderer.dll");
            if (dllFile.exists()) {
                System.load(dllFile.getAbsolutePath());
                loaded = true;
                System.out.println("[DX12] Library loaded from: " + dllFile.getAbsolutePath());
                return;
            }
        } catch (UnsatisfiedLinkError e) {
            System.out.println("[DX12] Failed to load from user.dir: " + e.getMessage());
        }
        System.err.println("[DX12] Failed to load DLL!");
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

    public static boolean init(long hwnd, int width, int height) {
        if (!loaded) {
            System.err.println("[DX12] Library not loaded!");
            return false;
        }
        if (initialized) return true;
        initialized = nativeInit(hwnd, width, height);
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
        if (initialized && loaded) {
            nativeRender();
        }
    }

    public static void updateMesh(ByteBuffer vertexData, int vertexCount,
                                  ByteBuffer indexData, int indexCount) {
        if (initialized && loaded) {
            System.out.println("[DX12] updateMesh: " + vertexCount + " vertices, " + indexCount + " indices");
            nativeUpdateMesh(vertexData, vertexCount, indexData, indexCount);
        }
    }

    public static void resize(int width, int height) {
        if (initialized && loaded) nativeResize(width, height);
    }

    public static void destroy() {
        if (initialized && loaded) {
            nativeDestroy();
            initialized = false;
        }
    }

    public static boolean isInitialized() {
        return initialized && loaded;
    }
}
