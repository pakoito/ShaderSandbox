
package com.pacoworks.shadersandbox;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import static javax.microedition.khronos.egl.EGL10.EGL_ALPHA_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_BLUE_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_DEFAULT_DISPLAY;
import static javax.microedition.khronos.egl.EGL10.EGL_DEPTH_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_GREEN_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_HEIGHT;
import static javax.microedition.khronos.egl.EGL10.EGL_NONE;
import static javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT;
import static javax.microedition.khronos.egl.EGL10.EGL_RED_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_STENCIL_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_WIDTH;
import static javax.microedition.khronos.opengles.GL10.GL_RGBA;
import static javax.microedition.khronos.opengles.GL10.GL_UNSIGNED_BYTE;

public class ShaderTool {

    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    private static final int[] ATTRIB_LIST = new int[]{
            EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE
    };

    private enum ScaleType {
        CENTER_INSIDE, CENTER_CROP
    }

    private static final float[] TEXTURE_NO_ROTATION = {
            0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,
    };

    private static final int NO_IMAGE = -1;

    private static final float[] CUBE = {
            -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f,
    };

    private final EGL10 mEGL;

    private final EGLDisplay mEGLDisplay;

    private final EGLConfig mEGLConfig;

    private final EGLContext mEGLContext;

    private final GL10 mGL;

    private final FloatBuffer mGLCubeBuffer;

    private final FloatBuffer mGLTextureBuffer;

    public ShaderTool() {
        int[] version = new int[2];
        // No error checking performed, minimum required code to elucidate logic
        mEGL = (EGL10) EGLContext.getEGL();
        mEGLDisplay = mEGL.eglGetDisplay(EGL_DEFAULT_DISPLAY);
        mEGL.eglInitialize(mEGLDisplay, version);
        int[] attribList = new int[]{
                EGL_DEPTH_SIZE, 0, EGL_STENCIL_SIZE, 0, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
                EGL_ALPHA_SIZE, 8, EGL10.EGL_RENDERABLE_TYPE, 4, EGL_NONE
        };
        // No error checking performed, minimum required code to elucidate logic
        // Expand on this logic to be more selective in choosing a configuration
        int[] numConfig = new int[1];
        mEGL.eglChooseConfig(mEGLDisplay, attribList, null, 0, numConfig);
        int configSize = numConfig[0];
        EGLConfig[] eglConfigs = new EGLConfig[configSize];
        mEGL.eglChooseConfig(mEGLDisplay, attribList, eglConfigs, configSize, numConfig);
        mEGLConfig = eglConfigs[0]; // Choosing a config is a little more complicated. Get first for now
        mEGLContext = mEGL.eglCreateContext(mEGLDisplay, mEGLConfig, EGL_NO_CONTEXT, ATTRIB_LIST);
        mGL = (GL10) mEGLContext.getGL();
        mGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mGLTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_NO_ROTATION.length * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
    }

    private void adjustImageScaling(ScaleType scaleType, float width, float height, float outputWidth,
                                    float outputHeight, FloatBuffer glCubeBuffer, FloatBuffer glTextureBuffer) {
        float ratio1 = outputWidth / width;
        float ratio2 = outputHeight / height;
        float ratioMax = Math.max(ratio1, ratio2);
        int imageWidthNew = Math.round(width * ratioMax);
        int imageHeightNew = Math.round(height * ratioMax);
        float ratioWidth = imageWidthNew / outputWidth;
        float ratioHeight = imageHeightNew / outputHeight;
        float[] cube = CUBE;
        float[] textureCords = TEXTURE_NO_ROTATION;
        if (scaleType == ScaleType.CENTER_CROP) {
            float distHorizontal = (1 - 1 / ratioWidth) / 2;
            float distVertical = (1 - 1 / ratioHeight) / 2;
            textureCords = new float[]{
                    addDistance(textureCords[0], distHorizontal), addDistance(textureCords[1], distVertical),
                    addDistance(textureCords[2], distHorizontal), addDistance(textureCords[3], distVertical),
                    addDistance(textureCords[4], distHorizontal), addDistance(textureCords[5], distVertical),
                    addDistance(textureCords[6], distHorizontal), addDistance(textureCords[7], distVertical),
            };
        } else {
            cube = new float[]{
                    CUBE[0] / ratioHeight, CUBE[1] / ratioWidth, CUBE[2] / ratioHeight, CUBE[3] / ratioWidth,
                    CUBE[4] / ratioHeight, CUBE[5] / ratioWidth, CUBE[6] / ratioHeight, CUBE[7] / ratioWidth,
            };
        }
        glCubeBuffer.clear();
        glCubeBuffer.put(cube).position(0);
        glTextureBuffer.clear();
        glTextureBuffer.put(textureCords).position(0);
    }

    private static float addDistance(float coordinate, float distance) {
        return coordinate == 0.0f ? distance : 1 - distance;
    }

    private int uploadTexture(Bitmap bitmap) {
        int gLTextureId;
        if (bitmap.getWidth() % 2 == 1) {
            Bitmap resizedBitmap = Bitmap.createBitmap(bitmap.getWidth() + 1, bitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas can = new Canvas(resizedBitmap);
            can.drawARGB(0x00, 0x00, 0x00, 0x00);
            can.drawBitmap(bitmap, 0, 0, null);
            gLTextureId = OpenGlUtils.loadTexture(resizedBitmap, NO_IMAGE, false);
            resizedBitmap.recycle();
        } else {
            gLTextureId = OpenGlUtils.loadTexture(bitmap, NO_IMAGE, false);
        }
        return gLTextureId;
    }

    private static Bitmap convertToBitmap(int width, int height, GL10 gl) {
        int[] iat = new int[width * height];
        IntBuffer ib = IntBuffer.allocate(width * height);
        gl.glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, ib);
        int[] ia = ib.array();
        // Stupid !
        // Convert upside down mirror-reversed image to right-side up normal
        // image.
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                iat[(height - i - 1) * width + j] = ia[i * width + j];
            }
        }
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(iat));
        return bitmap;
    }

    public Bitmap processBitmap(GPUImageFilter filter, Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] attribList = new int[]{
                EGL_WIDTH, width, EGL_HEIGHT, height, EGL_NONE
        };
        EGLSurface mEGLSurface = mEGL.eglCreatePbufferSurface(mEGLDisplay, mEGLConfig, attribList);
        mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        adjustImageScaling(ScaleType.CENTER_INSIDE, bitmap.getWidth(), bitmap.getHeight(), bitmap.getWidth(),
                bitmap.getHeight(), mGLCubeBuffer, mGLTextureBuffer);
        int gLTextureId = uploadTexture(bitmap);
        filter.init();
        filter.onOutputSizeChanged(bitmap.getWidth(), bitmap.getHeight());
        filter.onDraw(gLTextureId, mGLCubeBuffer, mGLTextureBuffer);
        filter.destroy();
        final Bitmap outputBitmap = convertToBitmap(width, height, mGL);
        GLES20.glDeleteTextures(1, IntBuffer.wrap(new int[]{gLTextureId}));
        return outputBitmap;
    }
}
