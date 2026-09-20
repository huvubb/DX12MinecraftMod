package com.yourmod.mixin;

import com.yourmod.Dx12Bridge;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class WindowMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onMinecraftInit(GameConfig gameConfig, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() == null) {
                System.out.println("[DX12] Window not ready yet, waiting...");
                return;
            }

            var window = mc.getWindow();

            // 获取窗口尺寸
            int width = window.getWidth();
            int height = window.getHeight();
            System.out.println("[DX12] Window size: " + width + "x" + height);

            // 使用 JNA 获取 HWND
            long hwnd = getHWNDViaJNA();
            System.out.println("[DX12] HWND (JNA): " + hwnd);

            if (hwnd == 0) {
                System.err.println("[DX12] Failed to get HWND!");
                return;
            }

            boolean success = Dx12Bridge.init(hwnd, width, height);
            System.out.println("[DX12] Renderer init result: " + success);

        } catch (Exception e) {
            System.err.println("[DX12] Initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private long getHWNDViaJNA() {
        try {
            // 方法1：通过窗口标题查找
            WinDef.HWND hwnd = User32.INSTANCE.FindWindow(null, "Minecraft 1.19.2");
            if (hwnd != null) {
                long ptr = Pointer.nativeValue(hwnd.getPointer());
                System.out.println("[DX12] FindWindow: " + ptr);
                if (ptr != 0) return ptr;
            }
        } catch (Exception e) {
            System.out.println("[DX12] FindWindow failed: " + e.getMessage());
        }

        try {
            // 方法2：获取活动窗口
            WinDef.HWND hwnd = User32.INSTANCE.GetActiveWindow();
            if (hwnd != null) {
                long ptr = Pointer.nativeValue(hwnd.getPointer());
                System.out.println("[DX12] GetActiveWindow: " + ptr);
                if (ptr != 0) return ptr;
            }
        } catch (Exception e) {
            System.out.println("[DX12] GetActiveWindow failed: " + e.getMessage());
        }

        try {
            // 方法3：获取桌面窗口（测试用）
            WinDef.HWND hwnd = User32.INSTANCE.GetDesktopWindow();
            if (hwnd != null) {
                long ptr = Pointer.nativeValue(hwnd.getPointer());
                System.out.println("[DX12] GetDesktopWindow: " + ptr);
                if (ptr != 0) return ptr;
            }
        } catch (Exception e) {
            System.out.println("[DX12] GetDesktopWindow failed: " + e.getMessage());
        }

        return 0;
    }
}
