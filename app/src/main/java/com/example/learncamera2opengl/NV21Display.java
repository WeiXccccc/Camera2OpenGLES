package com.example.learncamera2opengl;


import static javax.microedition.khronos.opengles.GL10.GL_LUMINANCE;
import static javax.microedition.khronos.opengles.GL10.GL_UNSIGNED_BYTE;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class NV21Display {
    private final static String TAG = "Abbott NV21Display";

    public final String vertexShaderCode =
            "attribute vec4 vPosition;" +
                    "attribute vec2 aTexCoord;" +
                    "varying vec2 vTexCoord;" +
                    "uniform mat4 mMat;" +
                    "void main() {" +
                    "   gl_Position = vPosition;" +
                    "   vTexCoord = aTexCoord;" +
                    "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
                    "varying vec2 vTexCoord;" +
                    "uniform sampler2D YTexture;" +
                    "uniform sampler2D UVTexture;" +
                    "void main() {" +
                    "   float y = texture2D(YTexture,vTexCoord).r;" +  // 使用 GL_LUMINANCE 类型, r == g == b = Y, a = 1.0 ,任取r g b 中一个
                    "   float u = texture2D(UVTexture,vTexCoord).a - 0.5;" +     // NV21 V在U前 使用 GL_LUMINANCE 类型, r == g == b = v, a = u ,任取r g b 中一个 和 a
                    "   float v = texture2D(UVTexture,vTexCoord).r - 0.5;" +    // 减 0.5 归一化到 [-0.5,0.5) 区间 为了计算公式的需要
                    "   float r = y + 1.370705*v;" +
                    "   float g = y - 0.337633*u - 0.698001*v;" +       // 看了几个转换公式 参数稍有差异
                    "   float b = y + 1.732446*u;" +
                    "   gl_FragColor = vec4(r,g,b,1.0);" +
                    "}";

    private final FloatBuffer vertexBuffer;
    private final FloatBuffer coordBuffer;
    private final int mProgram;
    private int mPositionHandle;
    private int mTextureHandle;
    private int mYHandle;
    private int mUVHandle;

    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 3;
    static float positionCoords[] = {

            -1.0f, 1.0f, 0.0f,  //左上角
            -1.0f, -1.0f, 0.0f,  //左下角
            1.0f, 1.0f, 0.0f,    //右上角
            1.0f, -1.0f, 0.0f,    //右下角
    };
    static float texCoords[] = {

            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f,

    };


    public NV21Display() {
        vertexBuffer = ByteBuffer.allocateDirect(positionCoords.length * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(positionCoords).position(0);
        coordBuffer = ByteBuffer.allocateDirect(texCoords.length * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        coordBuffer.put(texCoords).position(0);

        // prepare shaders and OpenGL program
        int vertexShader = loadShader(
                GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(
                GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        mProgram = GLES20.glCreateProgram();             // create empty OpenGL Program
        GLES20.glAttachShader(mProgram, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(mProgram);                  // create OpenGL program executables
        checkGLError("glLinkProgram");

        GLES20.glUseProgram(mProgram);
    }


    public void draw(byte[] nv21_data, int width, int height, int[] texture) {
        Log.d("wxc", "Draw ");
        mYHandle = GLES20.glGetUniformLocation(mProgram, "YTexture");
        checkGLError("Ytexture");
        mUVHandle = GLES20.glGetUniformLocation(mProgram, "UVTexture");
        checkGLError("UVtexture");

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 12, vertexBuffer);

        mTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        GLES20.glEnableVertexAttribArray(mTextureHandle);
        GLES20.glVertexAttribPointer(mTextureHandle, 2, GLES20.GL_FLOAT, false, 8, coordBuffer);

        if (mNv21Buffer == null)
            mNv21Buffer = ByteBuffer.allocateDirect(width * height * 3 / 2).order(ByteOrder.nativeOrder());
        mNv21Buffer.put(nv21_data).position(0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GL_LUMINANCE, width, height, 0, GL_LUMINANCE, GL_UNSIGNED_BYTE, mNv21Buffer);
        GLES20.glUniform1i(mYHandle, 0);
        checkGLError("wxc");
//
        mNv21Buffer.position(width * height);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[1]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE_ALPHA, width / 2, height / 2, 0, GLES20.GL_LUMINANCE_ALPHA, GL_UNSIGNED_BYTE, mNv21Buffer);
        GLES20.glUniform1i(mUVHandle, 1);


//        mMatrixHandle = GLES20.glGetUniformLocation(mProgram,"mMat");
//        GLES20.glUniformMatrix4fv(mMatrixHandle,1,false,rotateMatrix,0);

        // Draw the square
        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP, 0, 4);

        checkGLError("Abbott");
        mNv21Buffer.position(0);
        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureHandle);
    }

    ByteBuffer mNv21Buffer = null;


    public static int loadShader(int type, String shaderCode) {
        // 创建 vertex shader 或者 fragment shader 类型
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    public static void checkGLError(String glOperation) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, glOperation + ": glError " + error);
            throw new RuntimeException(glOperation + "glError " + error);
        }
    }
}
