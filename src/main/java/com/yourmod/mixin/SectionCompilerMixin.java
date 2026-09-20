package com.yourmod.mixin;

import com.yourmod.Dx12Bridge;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Mixin(ChunkRenderDispatcher.class)
public class SectionCompilerMixin {
    private static int chunkCount = 0;
    private static boolean debugPrinted = false;

    @Inject(
        method = "compileChunk",
        at = @At("RETURN"),
        remap = true
    )
    private void onCompileChunk(Object task, CallbackInfoReturnable<?> cir) {
        if (task == null) return;

        try {
            if (!debugPrinted) {
                System.out.println("[DX12] SectionCompilerMixin loaded!");
                System.out.println("[DX12] Task class: " + task.getClass().getName());
                debugPrinted = true;
            }

            // 遍历所有字段，查找 BufferBuilder
            Object builder = null;
            for (Field f : task.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(task);
                if (val != null) {
                    String className = val.getClass().getName();
                    // BufferBuilder 在 1.19.2 中可能在 com.mojang.blaze3d.vertex 包
                    if (className.contains("BufferBuilder") || className.contains("VertexBuffer")) {
                        builder = val;
                        System.out.println("[DX12] Found builder: " + f.getName() + " -> " + className);
                        break;
                    }
                }
            }
            if (builder == null) {
                System.out.println("[DX12] No BufferBuilder found in task");
                return;
            }

            // 获取顶点数量
            int vertexCount = 0;
            for (Field f : builder.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == int.class) {
                    int val = f.getInt(builder);
                    if (val > 0 && val < 1000000) {
                        vertexCount = val;
                        System.out.println("[DX12] Found vertexCount: " + f.getName() + " = " + val);
                        break;
                    }
                }
            }
            if (vertexCount == 0) {
                System.out.println("[DX12] vertexCount is 0");
                return;
            }

            // 获取顶点数据
            ByteBuffer vertexBuffer = null;
            for (Field f : builder.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == ByteBuffer.class) {
                    ByteBuffer buf = (ByteBuffer) f.get(builder);
                    if (buf != null && buf.remaining() > 0) {
                        vertexBuffer = buf;
                        System.out.println("[DX12] Found vertexBuffer: " + f.getName() + " size=" + buf.remaining());
                        break;
                    }
                }
            }
            if (vertexBuffer == null || vertexBuffer.remaining() == 0) {
                System.out.println("[DX12] vertexBuffer is null or empty");
                return;
            }

            // 计算顶点大小
            int vertexSize = vertexBuffer.remaining() / vertexCount;
            System.out.println("[DX12] vertexSize: " + vertexSize);

            // 复制顶点数据
            ByteBuffer vertexData = ByteBuffer.allocateDirect(vertexCount * vertexSize);
            vertexData.order(ByteOrder.nativeOrder());
            vertexBuffer.rewind();
            for (int i = 0; i < vertexCount * vertexSize; i++) {
                vertexData.put(vertexBuffer.get());
            }
            vertexData.rewind();

            // 生成索引数据
            int indexCount = vertexCount;
            ByteBuffer indexData = ByteBuffer.allocateDirect(indexCount * 4);
            indexData.order(ByteOrder.nativeOrder());
            for (int i = 0; i < vertexCount; i++) {
                indexData.putInt(i);
            }
            indexData.rewind();

            Dx12Bridge.updateMesh(vertexData, vertexCount, indexData, indexCount);

            chunkCount++;
            if (chunkCount % 5 == 0) {
                System.out.println("[DX12] Chunk compiled: " + chunkCount + ", vertices: " + vertexCount);
            }

        } catch (Exception e) {
            System.err.println("[DX12] SectionCompiler error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}