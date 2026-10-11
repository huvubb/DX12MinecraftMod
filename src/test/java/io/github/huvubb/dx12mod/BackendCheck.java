package io.github.huvubb.dx12mod;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32.*;

/** Exercises the same GLFW/WGL/LWJGL path as Minecraft on the actual D3D12 device. */
public final class BackendCheck {
    public static void main(String[] args) {
        if (Boolean.getBoolean("dx12.disable")) {
            Dx12Bootstrap.install(Path.of("unused"));
            Dx12Bootstrap.verifyRenderer("Intel OpenGL");
            require(Dx12Bootstrap.getStatusLine().contains("disabled"), "Disabled backend must not claim DX12");
            System.out.println("Backend disabled check passed");
            return;
        }
        try {
            Dx12Bootstrap.verifyRenderer("Intel OpenGL");
            throw new AssertionError("Unverified renderer accepted");
        } catch (IllegalStateException expected) {
            // Reject a label-only takeover.
        }
        Dx12Bootstrap.install(Path.of(args[0]));
        Dx12Bootstrap.install(Path.of(args[0]));
        GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err).set();
        long window = 0;
        try {
            require(glfwInit(), "GLFW initialization failed");
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(96, 96, "DX12 backend check", 0, 0);
            require(window != 0, "D3D12 game window creation failed");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            Dx12Bootstrap.verifyRenderer(glGetString(GL_RENDERER));
            require(Dx12Bootstrap.getStatusLine().contains("DirectX 12"), "Actual backend was not confirmed");
            System.out.println("GL compatibility API: " + glGetString(GL_VERSION));
            int program = glCreateProgram();
            int vertex = shader(GL_VERTEX_SHADER, "#version 150\nin vec2 Position; uniform float Z; void main(){gl_Position=vec4(Position,Z,1.0);}");
            int fragment = shader(GL_FRAGMENT_SHADER, "#version 150\nuniform sampler2D Sampler0; out vec4 color; void main(){color=texture(Sampler0,vec2(0.5));}");
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glBindAttribLocation(program, 0, "Position");
            glLinkProgram(program);
            require(glGetProgrami(program, GL_LINK_STATUS) != 0, glGetProgramInfoLog(program));
            glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
            int vao = glGenVertexArrays();
            glBindVertexArray(vao);
            int vbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, new float[]{-1,-1, 1,-1, 1,1, -1,1}, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0);
            glEnableVertexAttribArray(0);
            int indices = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indices);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, new int[]{0,1,2, 2,3,0}, GL_STATIC_DRAW);
            int input = texture(1, 1);
            int output = texture(64, 64);
            int framebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, output, 0);
            int depth = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, depth);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, 64, 64);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
            require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "Incomplete framebuffer");
            glViewport(0, 0, 64, 64);
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glEnable(GL_DEPTH_TEST);
            glBindTexture(GL_TEXTURE_2D, input);
            draw(program, -0.5f, new byte[]{(byte)255,0,0,(byte)255});
            draw(program, 0.5f, new byte[]{0,0,(byte)255,(byte)255});
            expectPixel(255, 0, 0);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            draw(program, -0.75f, new byte[]{0,(byte)255,0,(byte)128});
            expectPixel(127, 128, 0);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
            glBlitFramebuffer(0, 0, 64, 64, 0, 0, 96, 96, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
            expectPixel(127, 128, 0);
            glfwSwapBuffers(window);
            glReadBuffer(GL_FRONT);
            expectPixel(127, 128, 0);
            glReadBuffer(GL_BACK);
            glfwSetWindowSize(window, 128, 96);
            glfwPollEvents();
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
            glBlitFramebuffer(0, 0, 64, 64, 0, 0, 128, 96, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            glfwSwapBuffers(window);
            require(glGetError() == GL_NO_ERROR, "Graphics error after presentation/resize");
            glDeleteProgram(program);
            glDeleteShader(vertex);
            glDeleteShader(fragment);
            glDeleteBuffers(vbo);
            glDeleteBuffers(indices);
            glDeleteVertexArrays(vao);
            glDeleteFramebuffers(framebuffer);
            glDeleteRenderbuffers(depth);
            glDeleteTextures(input);
            glDeleteTextures(output);
            System.out.println("DX12 indexed mesh / shader / texture / depth / alpha / framebuffer / presentation / resize checks passed");
        } finally {
            if (window != 0) glfwDestroyWindow(window);
            glfwTerminate();
            errors.free();
        }
    }

    private static int shader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        require(glGetShaderi(shader, GL_COMPILE_STATUS) != 0, glGetShaderInfoLog(shader));
        return shader;
    }

    private static int texture(int width, int height) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer)null);
        return texture;
    }

    private static void draw(int program, float z, byte[] rgba) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(4).put(rgba).flip();
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glUniform1f(glGetUniformLocation(program, "Z"), z);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    private static void expectPixel(int red, int green, int blue) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        glReadPixels(32, 32, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        int[] expected = {red, green, blue};
        for (int i = 0; i < 3; i++) {
            require(Math.abs((pixel.get(i) & 255) - expected[i]) <= 2, "Incorrect rendered pixel: " + (pixel.get(i) & 255) + " expected " + expected[i]);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
