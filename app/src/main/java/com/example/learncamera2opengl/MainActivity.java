package com.example.learncamera2opengl;
#test
import static android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY;

//import static com.example.learncamera2opengl.render.ByteFlowRender.IMAGE_FORMAT_I420;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.learncamera2opengl.camera.Camera2FrameCallback;
import com.example.learncamera2opengl.camera.Camera2Wrapper;
//import com.example.learncamera2opengl.gesture.MyGestureListener;
import com.example.learncamera2opengl.camera.CameraUtil;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity implements Camera2FrameCallback, GLSurfaceView.Renderer {
    private static final String TAG = "Abbott MainActivity";

    private GLSurfaceView mGlSurfaceView;
    private Camera2Wrapper mCamera2Wrapper;
    private NV21Display mNv21Display;
    private ImageButton mSwitchCamBtn, mSwitchRatioBtn;
    protected Size mScreenSize;

    byte[] nv21;
    int mWidth, mHeight;

    private int[] mTexName;

    private static final String[] REQUEST_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSwitchCamBtn = (ImageButton) findViewById(R.id.switch_camera_btn);
        mSwitchRatioBtn = (ImageButton) findViewById(R.id.switch_ratio_btn);
        mSwitchCamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String cameraId = mCamera2Wrapper.getCameraId();
                String[] cameraIds = mCamera2Wrapper.getSupportCameraIds();
                Log.d(TAG, "onClick: " + cameraIds);
                if (cameraIds != null) {
                    for (int i = 0; i < cameraIds.length; i++) {
                        if (!cameraIds[i].equals(cameraId)) {
                            mCamera2Wrapper.updateCameraId(cameraIds[i]);
                            break;
                        }
                    }
                }
            }
        });


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCamera2Wrapper != null) {
                    mCamera2Wrapper.capture();
                }
            }
        });


    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPermissionsGranted(REQUEST_PERMISSIONS)) {

        } else {
            ActivityCompat.requestPermissions(this, REQUEST_PERMISSIONS, CAMERA_PERMISSION_REQUEST_CODE);

        }

        mCamera2Wrapper = new Camera2Wrapper(this);
        mCamera2Wrapper.startCamera();

        mGlSurfaceView = findViewById(R.id.glView);
        mGlSurfaceView.setEGLContextClientVersion(2);
        mGlSurfaceView.setRenderer(this);
        mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

    }


    @Override
    protected void onPause() {
        if (hasPermissionsGranted(REQUEST_PERMISSIONS)) {
            mCamera2Wrapper.stopCamera();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCamera2Wrapper.stopCamera();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (hasPermissionsGranted(REQUEST_PERMISSIONS)) {
            } else {
                Toast.makeText(this, "We need the camera permission.", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    protected boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    @Override
    public void onPreviewFrame(byte[] data, int width, int height) {
        Log.d(TAG, "onPreviewFrame: " + data);
        Log.d(TAG, "onPreviewFrame: " + width);
        Log.d(TAG, "onPreviewFrame: " + height);
        nv21 = data;
        mWidth = width;
        mHeight = height;
        mGlSurfaceView.requestRender();
    }

    @Override
    public void onCaptureFrame(byte[] data, int width, int height) {
        Log.d(TAG, "onCaptureFrame: " + data);
        Log.d(TAG, "onCaptureFrame: " + width);
        Log.d(TAG, "onCaptureFrame: " + height);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");
        Date currentDate = new Date();
        String formattedDate = sdf.format(currentDate);
        saveNv21ToFile(data, formattedDate);

    }

    public void saveNv21ToFile(byte[] nv21Data, String filename) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 存在 storage/emulated/0/Pictures
            File file = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    filename + ".yuv"
            );
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DESCRIPTION, "yuv data");
            values.put(MediaStore.Images.Media.DISPLAY_NAME, file.getName());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.TITLE, "Image.jpg");
//            values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/decode");
            Uri external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            ContentResolver resolver = this.getContentResolver();
            Uri insertUri = resolver.insert(external, values);
            OutputStream os = null;
            try {
                if (insertUri != null) {
                    os = resolver.openOutputStream(insertUri);
                }
                if (os != null) {
                    os.write(nv21Data);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (os != null) {
                        os.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            // Android SDK 28 及以下
            File file = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    filename + ".yuv"
            );
            Log.d(TAG, "Save File " + file.getAbsolutePath());
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(file);
                output.write(nv21Data);
            } catch (IOException e) {
                Log.d(TAG, e.getMessage());
            } finally {
                try {
                    if (output != null) {
                        output.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        mNv21Display = new NV21Display();

        GLES20.glClearColor(0.0f, 0, 0, 1.0f);
        mTexName = new int[3];

        GLES20.glGenTextures(3, mTexName, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexName[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexName[1]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexName[2]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        Log.d("wxc", "onDrawFrame ");
        // 画背景颜色
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (mWidth == 0 || mHeight == 0 || nv21 == null) {
            Log.d("wxc", "onDrawFrame return  ");
            return;
        }
        Log.d("wxc", "onDrawFrame draw ");
        mNv21Display.draw(nv21, mWidth, mHeight, mTexName);
    }


    public Size getScreenSize() {
        if (mScreenSize == null) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            mScreenSize = new Size(displayMetrics.widthPixels, displayMetrics.heightPixels);
        }
        return mScreenSize;
    }




}
