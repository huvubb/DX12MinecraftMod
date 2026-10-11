package io.github.huvubb.dx12mod.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import io.github.huvubb.dx12mod.Dx12Bootstrap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Redirect(method = "resizeDisplay", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;resize(IIZ)V"))
    private void dx12$resize(RenderTarget target, int width, int height, boolean clearError) {
        // GUI scale changes need no GPU allocation; match createBuffers' texture and framebuffer state.
        if (Dx12Bootstrap.isActive() && target.frameBufferId >= 0
                && target.width == width && target.height == height
                && target.viewWidth == width && target.viewHeight == height) {
            target.unbindRead();
            target.bindWrite(false);
            return;
        }
        target.resize(width, height, clearError);
    }
}
