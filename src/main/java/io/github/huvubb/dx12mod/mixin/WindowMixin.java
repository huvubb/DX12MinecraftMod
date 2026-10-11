package io.github.huvubb.dx12mod.mixin;

import io.github.huvubb.dx12mod.Dx12Bootstrap;
import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void dx12$verify(CallbackInfo ci) { Dx12Bootstrap.verifyRenderer(GlUtil.getRenderer()); }
}
