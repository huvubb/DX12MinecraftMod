package io.github.huvubb.dx12mod;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Loads the complete Mesa D3D12 driver before GLFW creates any OpenGL context. */
public final class Dx12Bootstrap implements PreLaunchEntrypoint {
    private static final String RESOURCE = "/natives/windows-x86_64/mesa-26.2.4/opengl32.dll";
    private static final String SHA256 = "5bae84a373e9b0bd732780b01533bc5e50ce3e0144a39836b8332201b89e9f42";
    private static boolean prepared;
    private static boolean active;
    private static String renderer = "waiting for window";

    private interface Windows extends StdCallLibrary {
        Windows INSTANCE = Native.load("kernel32", Windows.class);
        Pointer GetModuleHandleW(WString name);
        int GetModuleFileNameW(Pointer module, char[] filename, int size);
    }

    @Override
    public void onPreLaunch() {
        install(FabricLoader.getInstance().getGameDir());
    }

    public static synchronized void install(Path gameDirectory) {
        if (prepared || Boolean.getBoolean("dx12.disable")) return;
        if (!System.getProperty("os.name").startsWith("Windows")
                || !System.getProperty("os.arch").equals("amd64")) {
            throw new IllegalStateException("DX12 requires 64-bit Java on Windows.");
        }
        try {
            Path directory = gameDirectory.toAbsolutePath().resolve(".dx12mod/mesa-26.2.4-" + SHA256.substring(0, 12));
            Files.createDirectories(directory);
            Path library = directory.resolve("opengl32.dll");
            if (!Files.isRegularFile(library) || !hash(library).equals(SHA256)) {
                Path temporary = Files.createTempFile(directory, "opengl32-", ".tmp");
                try (InputStream input = Dx12Bootstrap.class.getResourceAsStream(RESOURCE)) {
                    if (input == null) throw new IOException("Bundled Mesa D3D12 driver is missing.");
                    Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                    if (!hash(temporary).equals(SHA256)) throw new IOException("Mesa D3D12 driver checksum mismatch.");
                    Files.move(temporary, library, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            // GLFW loads by basename, so an earlier system driver would keep ownership of its context.
            checkLoadedModule(library);
            System.load(library.toString());
            checkLoadedModule(library);
            System.setProperty("org.lwjgl.opengl.libname", library.toString());
            prepared = true;
            System.out.println("[DX12] Mesa D3D12 driver loaded: " + library);
        } catch (IOException | UnsatisfiedLinkError exception) {
            throw new IllegalStateException("DX12 driver could not start: " + exception.getMessage(), exception);
        }
    }

    private static void checkLoadedModule(Path expected) throws IOException {
        Pointer module = Windows.INSTANCE.GetModuleHandleW(new WString("opengl32.dll"));
        if (module == null) return;
        char[] filename = new char[32768];
        int length = Windows.INSTANCE.GetModuleFileNameW(module, filename, filename.length);
        if (length == 0 || length >= filename.length) throw new IOException("Cannot identify the loaded graphics driver.");
        Path actual = Path.of(new String(filename, 0, length));
        if (!Files.isSameFile(expected, actual)) {
            throw new IOException("Another mod loaded an OpenGL driver before DX12: " + actual);
        }
    }

    private static String hash(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            for (int size; (size = input.read(buffer)) != -1;) digest.update(buffer, 0, size);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void verifyRenderer(String actualRenderer) {
        renderer = actualRenderer == null ? "unknown" : actualRenderer;
        if (Boolean.getBoolean("dx12.disable")) return;
        if (!prepared || !renderer.toLowerCase(Locale.ROOT).contains("d3d12")) {
            throw new IllegalStateException("DX12 did not take over the game context. Actual renderer: " + renderer);
        }
        active = true;
        System.out.println("[DX12] ACTIVE: all game rendering and presentation use " + renderer);
    }

    public static String getStatusLine() {
        if (Boolean.getBoolean("dx12.disable")) return "Render backend: DX12 disabled";
        return active ? "Render backend: DirectX 12 (Mesa D3D12)" : "Render backend: DX12 awaiting context verification";
    }

    public static String getRendererLine() {
        return "DX12 device: " + renderer;
    }
}
