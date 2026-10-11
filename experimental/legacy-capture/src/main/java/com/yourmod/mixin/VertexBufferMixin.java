package com.yourmod.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yourmod.Dx12Bridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
public class VertexBufferMixin {
    @Unique private final long dx12$meshId = Dx12Bridge.nextMeshId();

    // upload() releases the game's buffer; copy before the original method runs.
    @Inject(method = "upload", at = @At("HEAD"))
    private void dx12$upload(BufferBuilder.RenderedBuffer buffer, CallbackInfo ci) {
        if (!((VertexBuffer) (Object) this).isInvalid()) Dx12Bridge.captureMesh(dx12$meshId, buffer);
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void dx12$draw(CallbackInfo ci) {
        if (!((VertexBuffer) (Object) this).isInvalid()) {
            Dx12Bridge.captureDraw(dx12$meshId, RenderSystem.getShader());
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void dx12$close(CallbackInfo ci) { Dx12Bridge.releaseMesh(dx12$meshId); }
}
