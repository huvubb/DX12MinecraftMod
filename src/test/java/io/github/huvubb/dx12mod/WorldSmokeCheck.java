package io.github.huvubb.dx12mod;

import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/** Only in the development test mod; creates an isolated world and exits after rendering it. */
public final class WorldSmokeCheck implements ClientModInitializer {
    private int worldFrames;
    private boolean entering;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("dx12.worldSmoke")) return;
        Thread worker = new Thread(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            try {
                long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(4);
                while (System.nanoTime() < deadline) {
                    CompletableFuture<Boolean> step = new CompletableFuture<>();
                    minecraft.execute(() -> {
                        try { step.complete(check(minecraft)); }
                        catch (Exception | AssertionError exception) { step.completeExceptionally(exception); }
                    });
                    if (step.get(60, TimeUnit.SECONDS)) return;
                    Thread.sleep(250);
                }
                throw new AssertionError("World smoke check timed out");
            } catch (Exception | AssertionError exception) {
                exception.printStackTrace();
                System.err.println("[DX12-SMOKE] FAILED");
                minecraft.execute(minecraft::stop);
            }
        }, "DX12 world smoke check");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean check(Minecraft minecraft) throws Exception {
        if (minecraft.getOverlay() != null) return false;
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.renderDistance().set(4);
        if (minecraft.level != null && minecraft.player != null) {
            minecraft.options.renderDebug = true;
            if (++worldFrames < 80) return false;
            if (Boolean.getBoolean("dx12.irisSmoke") && worldFrames < 220) {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object iris = api.getMethod("getInstance").invoke(null);
                Class<?> configApi = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
                Object config = api.getMethod("getConfig").invoke(iris);
                if (worldFrames == 80) {
                    verifyIris();
                    configApi.getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, false);
                } else if (worldFrames == 100) {
                    if (Boolean.TRUE.equals(api.getMethod("isShaderPackInUse").invoke(iris))) {
                        throw new AssertionError("Iris shaders did not disable");
                    }
                    configApi.getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, true);
                } else if (worldFrames == 160) {
                    verifyIris();
                    Class.forName("net.coderbot.iris.Iris").getMethod("reload").invoke(null);
                    System.out.println("[DX12-SMOKE] Iris disable/enable/reload completed");
                }
                return false;
            }
            Field overlay = Gui.class.getDeclaredField("debugScreen");
            overlay.setAccessible(true);
            DebugScreenOverlay debug = (DebugScreenOverlay)overlay.get(minecraft.gui);
            Method systemInfo = DebugScreenOverlay.class.getDeclaredMethod("getSystemInformation");
            systemInfo.setAccessible(true);
            List<?> lines = (List<?>)systemInfo.invoke(debug);
            if (!lines.contains("Render backend: DirectX 12 (Mesa D3D12)")) throw new AssertionError("F3 backend status missing");
            if (Boolean.getBoolean("dx12.irisSmoke")) {
                verifyIris();
            }
            System.out.println("[DX12-SMOKE] F3: " + lines);
            Path image = FabricLoader.getInstance().getGameDir().resolve("dx12-world.png");
            try (NativeImage pixels = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                pixels.writeToFile(image);
            }
            System.out.println("[DX12-SMOKE] PASS: world rendered through " + Dx12Bootstrap.getRendererLine() + "; screenshot=" + image);
            Files.writeString(image.resolveSibling("dx12-smoke.ok"), lines.toString());
            minecraft.stop();
            return true;
        }
        if (entering || minecraft.screen == null) return false;
        if (minecraft.screen instanceof TitleScreen
                && Files.isDirectory(FabricLoader.getInstance().getGameDir().resolve("saves/New World"))) {
            entering = true;
            minecraft.createWorldOpenFlows().loadLevel(minecraft.screen, "New World");
            return false;
        }
        String action = minecraft.screen instanceof TitleScreen ? "Singleplayer"
                : minecraft.screen instanceof SelectWorldScreen ? "Create New World"
                : minecraft.screen instanceof CreateWorldScreen ? "Create New World" : null;
        if (action != null) {
            for (var child : minecraft.screen.children()) {
                if (child instanceof Button button && button.active && button.getMessage().getString().equals(action)) {
                    if (minecraft.screen instanceof CreateWorldScreen) entering = true;
                    button.onPress();
                    break;
                }
            }
        }
        return false;
    }

    private static void verifyIris() throws ReflectiveOperationException {
        Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
        Object iris = api.getMethod("getInstance").invoke(null);
        if (!Boolean.TRUE.equals(api.getMethod("isShaderPackInUse").invoke(iris))) {
            throw new AssertionError("Iris shader pack is not active");
        }
        Class<?> implementation = Class.forName("net.coderbot.iris.Iris");
        if (Boolean.TRUE.equals(implementation.getMethod("isFallback").invoke(null))) {
            throw new AssertionError("Iris fell back after shader compilation failed");
        }
        System.out.println("[DX12-SMOKE] Iris pack active: "
                + implementation.getMethod("getCurrentPackName").invoke(null));
    }
}
