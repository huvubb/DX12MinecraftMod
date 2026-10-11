package com.yourmod;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

// Run with gradlew captureCheck (also builds and checks the native DLL).
public class CaptureCheck {
    private static final String LAYOUT = "0:POSITION:0:FLOAT:3;12:COLOR:0:UBYTE:4;";

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Capture check failed");
    }

    private static void invalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid native input was accepted");
    }

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--expect-unavailable")) {
            check(!Dx12Bridge.isLoaded());
            Dx12Bridge.beginFrame();
            Dx12Bridge.destroy();
            check(Dx12Bridge.getStatusLine().contains("已禁用"));
            System.out.println("Incompatible DLL safely disabled");
            return;
        }
        check(Dx12Bridge.isLoaded());
        Dx12Bridge.destroy();
        ByteBuffer vertices = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
        ByteBuffer indices = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder());
        for (int index : new int[] {0, 1, 2, 2, 3, 0}) indices.putShort((short) index);
        indices.flip();
        Dx12Bridge.nativeCaptureMesh(1, vertices, 4, 16, indices, 6, 2, 7, false, false, LAYOUT);
        Dx12Bridge.nativeCaptureMesh(2, vertices, 4, 16, null, 6, 2, 7, false, true, LAYOUT);
        check(Arrays.equals(Dx12Bridge.nativeCaptureStats(), new long[] {2, 128, 12, 0, 2, 0}));

        // Transparent sorting updates indices without discarding cached vertices.
        Dx12Bridge.nativeCaptureMesh(1, null, 4, 16, indices, 6, 2, 7, true, false, LAYOUT);
        float[] matrices = new float[35];
        int[] textures = new int[12];
        for (int offset : new int[] {0, 16}) for (int i = 0; i < 4; i++) matrices[offset + i * 5] = 1;
        Dx12Bridge.nativeCaptureDraw(1, matrices, textures, "rendertype_solid");
        check(Arrays.equals(Dx12Bridge.nativeCaptureStats(), new long[] {2, 128, 12, 1, 3, 0}));
        invalid(() -> Dx12Bridge.nativeCaptureMesh(3, ByteBuffer.allocate(64), 4, 16,
                indices, 6, 2, 7, false, false, LAYOUT));
        invalid(() -> Dx12Bridge.nativeCaptureMesh(3, ByteBuffer.allocateDirect(8), 4, 16,
                indices, 6, 2, 7, false, false, LAYOUT));
        Dx12Bridge.nativeCaptureMesh(3, null, 4, 16, indices, 6, 2, 7, true, false, LAYOUT);
        invalid(() -> Dx12Bridge.nativeCaptureDraw(1, new float[16], textures, "bad"));
        indices.putShort(0, (short) 4);
        invalid(() -> Dx12Bridge.nativeCaptureMesh(1, vertices, 4, 16,
                indices, 6, 2, 7, false, false, LAYOUT));
        check(Arrays.equals(Dx12Bridge.nativeCaptureStats(), new long[] {2, 128, 12, 1, 3, 1}));
        Dx12Bridge.releaseMesh(1);
        check(Arrays.equals(Dx12Bridge.nativeCaptureStats(), new long[] {1, 64, 0, 1, 3, 1}));
        Dx12Bridge.nativeCaptureDraw(2, matrices, textures, "rendertype_translucent");
        Dx12Bridge.beginFrame();
        check(Dx12Bridge.nativeCaptureStats()[3] == 0);
        Dx12Bridge.destroy();
        check(Arrays.equals(Dx12Bridge.nativeCaptureStats(), new long[6]));

        // A sort task's vertex count can differ from the retained VBO after rebuilding.
        indices.putShort(0, (short) 0);
        Dx12Bridge.nativeCaptureMesh(1, vertices, 4, 16, indices, 6, 2, 7, false, false, LAYOUT);
        Dx12Bridge.nativeCaptureMesh(1, null, 3, 16, indices, 6, 2, 7, true, false, LAYOUT);
        check(Arrays.equals(Dx12Bridge.nativeCaptureStats(), new long[] {1, 64, 12, 0, 2, 0}));
        check(Dx12Bridge.getStatusLine().contains("更新 2"));
        check(Dx12Bridge.getStatusLine().contains("未启动"));
        // An out-of-range stale sort is counted and excluded from future captured draws.
        indices.putShort(0, (short) 4);
        Dx12Bridge.nativeCaptureMesh(1, null, 8, 16, indices, 6, 2, 7, true, false, LAYOUT);
        check(Arrays.equals(Dx12Bridge.nativeCaptureStats(), new long[] {0, 0, 0, 0, 2, 1}));
        // Unexpected capture errors stop the optional receiver, not the game renderer.
        Dx12Bridge.captureMesh(1, null);
        check(Dx12Bridge.getStatusLine().contains("已停用"));
        Dx12Bridge.beginFrame();
        check(Arrays.equals(Dx12Bridge.nativeCaptureStats(), new long[6]));
        System.out.println("Capture JNI check passed");
    }
}
