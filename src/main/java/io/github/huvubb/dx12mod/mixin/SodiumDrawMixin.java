package io.github.huvubb.dx12mod.mixin;

import io.github.huvubb.dx12mod.Dx12Bootstrap;
import io.github.huvubb.dx12mod.Dx12Mod;
import java.nio.IntBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL32C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList", remap = false)
public class SodiumDrawMixin {
    @Unique private boolean dx12$logged;

    @Redirect(method = "multiDrawElementsBaseVertex", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;glMultiDrawElementsBaseVertex(ILjava/nio/IntBuffer;ILorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;)V"))
    private void dx12$draw(int mode, IntBuffer counts, int type, PointerBuffer offsets, IntBuffer bases) {
        if (!Dx12Bootstrap.isActive()) {
            GL32C.glMultiDrawElementsBaseVertex(mode, counts, type, offsets, bases);
            return;
        }
        int draws = offsets.remaining();
        if (counts.remaining() < draws || bases.remaining() < draws) {
            throw new IllegalArgumentException("Incomplete Sodium draw buffers");
        }
        if (!dx12$logged) {
            Dx12Mod.LOGGER.info("[DX12] Sodium draws submitted individually to avoid Mesa D3D12 native memory growth.");
            dx12$logged = true;
        }
        // shortcut: bypass Mesa 26.2.4 multi-draw memory growth; remove after a driver fix passes the Iris checks.
        for (int draw = 0; draw < draws; draw++) {
            GL32C.glDrawElementsBaseVertex(mode, counts.get(counts.position() + draw), type,
                    offsets.get(offsets.position() + draw), bases.get(bases.position() + draw));
        }
    }
}
