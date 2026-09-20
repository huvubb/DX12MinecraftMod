package com.yourmod.mixin;

import com.yourmod.Dx12Bridge;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    private int frameCount = 0;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        frameCount++;
        if (frameCount % 60 == 0) {
            System.out.println("[DX12] Frame " + frameCount + ", renderLevel=" + renderLevel + ", initialized=" + Dx12Bridge.isInitialized());
        }

        if (renderLevel && Dx12Bridge.isInitialized()) {
            Dx12Bridge.render();
            ci.cancel();
        }
    }

    @Inject(method = "resize", at = @At("HEAD"))
    private void onResize(int width, int height, CallbackInfo ci) {
        if (Dx12Bridge.isInitialized()) {
            Dx12Bridge.resize(width, height);
        }
    }
}
