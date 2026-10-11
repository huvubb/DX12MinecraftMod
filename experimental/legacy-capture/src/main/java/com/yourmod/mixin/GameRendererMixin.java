package com.yourmod.mixin;

import com.yourmod.Dx12Bridge;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void dx12$beginFrame(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        Dx12Bridge.beginFrame();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void dx12$endFrame(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        Dx12Bridge.endFrame();
    }
}
