package io.github.huvubb.dx12mod.mixin;

import io.github.huvubb.dx12mod.Dx12Bootstrap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void dx12$status(CallbackInfoReturnable<List<String>> ci) {
        List<String> lines = new ArrayList<>(ci.getReturnValue());
        lines.add(Dx12Bootstrap.getStatusLine());
        ci.setReturnValue(lines);
    }

    @Inject(method = "getSystemInformation", at = @At("RETURN"), cancellable = true)
    private void dx12$renderer(CallbackInfoReturnable<List<String>> ci) {
        List<String> lines = new ArrayList<>(ci.getReturnValue());
        lines.add(Dx12Bootstrap.getStatusLine());
        lines.add(Dx12Bootstrap.getRendererLine());
        ci.setReturnValue(lines);
    }
}
